package com.wwt.pixel.image.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.image.ai.ImageGenerationService;
import com.wwt.pixel.image.ai.MultiVendorImageService;
import com.wwt.pixel.image.domain.GenerationRecord;
import com.wwt.pixel.image.domain.GenerationRequest;
import com.wwt.pixel.image.domain.GenerationResult;
import com.wwt.pixel.image.domain.StyleTemplate;
import com.wwt.pixel.image.dto.ModelInfo;
import com.wwt.pixel.image.event.EventType;
import com.wwt.pixel.image.event.ImageGenerationFailedEvent;
import com.wwt.pixel.image.event.ImageGenerationSuccessEvent;
import com.wwt.pixel.image.feign.UserServiceClient;
import com.wwt.pixel.image.infrastructure.redis.RedisLockService;
import com.wwt.pixel.image.infrastructure.redis.TaskStatusService;
import com.wwt.pixel.image.mapper.GenerationRecordMapper;
import com.wwt.pixel.image.mq.MQTopic;
import com.wwt.pixel.image.mq.MessageProducer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 头像生成应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarGenerationService {

    private static final Duration LOCAL_CACHE_TTL = Duration.ofHours(3);
    private final ImageGenerationService imageGenerationService;
    private final MultiVendorImageService multiVendorImageService;
    private final StyleTemplateService styleTemplateService;
    private final UserServiceClient userServiceClient;
    private final GenerationRecordMapper generationRecordMapper;
    private final RedisLockService redisLockService;
    private final TaskStatusService taskStatusService;
    private final MessageProducer messageProducer;

    // 本地缓存 (3小时过期)
    private final Cache<String, GenerationResult> localCache = Caffeine.newBuilder()
            .expireAfterWrite(LOCAL_CACHE_TTL)
            .build();

    private static final String LOCK_PREFIX = "generation:lock:user:";
    private static final Duration LOCK_EXPIRE_TIME = Duration.ofMinutes(10);

    /**
     * 文生图（带幂等处理 + 消息队列异步处理）
     * 不使用 @GlobalTransactional：AI生成耗时不可控（可达60s+），会导致Seata全局事务超时回滚。
     * 数据一致性通过 Redis分布式锁 + 本地消息表 保证。
     */
    public GenerationResult generateAvatar(Long userId, String prompt, String style, String modelId,
                                           String aspectRatio, String imageSize,
                                           Map<String, Object> extraParams) {
        return executeGenerationWithLock(userId,
                taskId -> doGenerateAvatarAsync(
                        userId, prompt, style, modelId, aspectRatio, imageSize, extraParams, taskId));
    }

    /**
     * 图生图（复用文生图的服务链路，但必须带参考图）
     */
    public GenerationResult generateAvatarFromImages(Long userId, String prompt, String style, String modelId,
                                                     List<MultipartFile> sourceImages, List<String> sourceImageUrls,
                                                     String responseFormat, String aspectRatio, String imageSize,
                                                     Map<String, Object> extraParams) {
        return executeGenerationWithLock(userId,
                taskId -> doGenerateAvatarFromImagesAsync(
                        userId, prompt, style, modelId, sourceImages, sourceImageUrls,
                        responseFormat, aspectRatio, imageSize, extraParams, taskId));
    }

    private GenerationResult executeGenerationWithLock(Long userId, TaskExecutor taskExecutor) {
        String lockKey = LOCK_PREFIX + userId;
        String lockValue = null;
        String taskId = null;

        try {
            // 1. 尝试获取分布式锁
            lockValue = redisLockService.tryLock(lockKey, LOCK_EXPIRE_TIME);

            if (lockValue == null) {
                // 获取锁失败，说明用户有正在进行的任务
                log.info("用户正在生成图片，返回当前任务状态: userId={}", userId);
                return handleRunningTask(userId);
            }

            // 2. 获取锁成功，生成新的任务ID
            taskId = UUID.randomUUID().toString();
            log.info("开始生成图片任务: userId={}, taskId={}", userId, taskId);

            // 3. 保存任务状态为RUNNING
            GenerationResult runningResult = GenerationResult.builder()
                    .taskId(taskId)
                    .taskStatus("RUNNING")
                    .build();
            taskStatusService.saveTaskStatus(taskId, "RUNNING", runningResult);

            // 4. 执行实际的生成逻辑（异步处理）
            GenerationResult result = taskExecutor.execute(taskId);

            // 5. 更新任务状态为SUCCESS
            result.setTaskId(taskId);
            result.setTaskStatus("SUCCESS");
            taskStatusService.saveTaskStatus(taskId, "SUCCESS", result);

            return result;

        } catch (Exception e) {
            log.error("图片生成失败: userId={}, taskId={}", userId, taskId, e);

            // 保存失败状态
            if (taskId != null) {
                GenerationResult failedResult = GenerationResult.builder()
                        .taskId(taskId)
                        .taskStatus("FAILED")
                        .build();
                taskStatusService.saveTaskStatus(taskId, "FAILED", failedResult);
            }

            throw new BusinessException("图片生成失败: " + e.getMessage());

        } finally {
            // 6. 释放锁
            if (lockValue != null) {
                redisLockService.unlock(lockKey, lockValue);
                log.debug("释放生成锁: userId={}", userId);
            }
        }
    }

    @FunctionalInterface
    private interface TaskExecutor {
        GenerationResult execute(String taskId);
    }

    /**
     * 处理正在运行的任务（用户重复点击时返回）
     */
    private GenerationResult handleRunningTask(Long userId) {
        // 返回RUNNING状态，让前端继续展示进度
        return GenerationResult.builder()
                .taskStatus("RUNNING")
                .build();
    }

    /**
     * 实际的生成逻辑（异步处理，通过MQ解耦）
     */
    private GenerationResult doGenerateAvatarAsync(Long userId, String prompt, String style, String modelId,
                                                   String aspectRatio, String imageSize,
                                                   Map<String, Object> extraParams, String taskId) {
        checkQuotaAndModelAccess(userId, modelId, false);

        StyleTemplate template = getStyleTemplate(style);
        GenerationRequest request = buildRequest(prompt, style, template, modelId, aspectRatio, imageSize, extraParams);

        // 2. 构建缓存key（基于最终生效的请求内容）
        String cacheKey = buildTextCacheKey(request);

        // 3. 查询缓存
        GenerationResult cached = getCachedResult(cacheKey);
        if (cached != null) {
            log.info("命中本地缓存, key: {}, userId: {}", cacheKey, userId);

            GenerationRecord cachedRecord = GenerationRecord.builder()
                    .userId(userId)
                    .prompt(prompt)
                    .negativePrompt(request.getNegativePrompt())
                    .style(style)
                    .status(0)
                    .build();
            generationRecordMapper.insert(cachedRecord);

            // 缓存命中，仍写入生成记录并发送成功消息到MQ（异步扣减额度、同步资产）
            sendSuccessMessage(userId, cachedRecord.getId(), taskId, cached);

            return GenerationResult.builder()
                    .taskId(taskId)
                    .imageUrl(cached.getImageUrl())
                    .imageBase64(cached.getImageBase64())
                    .imageUrls(cached.getImageUrls())
                    .imageBase64List(cached.getImageBase64List())
                    .ossUrl(cached.getOssUrl())
                    .ossUrls(cached.getOssUrls())
                    .revisedPrompt(cached.getRevisedPrompt())
                    .vendor(cached.getVendor())
                    .model(cached.getModel())
                    .generationTimeMs(0L)
                    .fromCache(true)
                    .build();
        }

        // 4. 插入生成记录（status=0 生成中）
        GenerationRecord record = GenerationRecord.builder()
                .userId(userId)
                .prompt(prompt)
                .negativePrompt(request.getNegativePrompt())
                .style(style)
                .status(0)  // 生成中
                .build();
        generationRecordMapper.insert(record);
        log.info("插入生成记录, recordId: {}, userId: {}", record.getId(), userId);

        log.info("文生图请求, prompt: {}, negativePrompt: {}", request.getPrompt(), request.getNegativePrompt());

        // 6. 调用AI生成
        GenerationResult result;
        try {
            result = imageGenerationService.generateImage(request);

            // 7. 生成成功，发送消息到MQ（异步处理后续逻辑）
            sendSuccessMessage(userId, record.getId(), taskId, result);

            // 8. 存入缓存
            putCachedResult(cacheKey, result);

            return result;

        } catch (Exception e) {
            log.error("图片生成失败", e);

            // 9. 生成失败，发送失败消息到MQ
            sendFailureMessage(userId, record.getId(), taskId, e.getMessage());

            throw new BusinessException("图片生成失败: " + e.getMessage());
        }
    }

    /**
     * 发送生成成功消息到MQ
     */
    private void sendSuccessMessage(Long userId, Long recordId, String taskId, GenerationResult result) {
        ImageGenerationSuccessEvent event = ImageGenerationSuccessEvent.builder()
                .userId(userId)
                .recordId(recordId)
                .taskId(taskId)
                .imageUrl(result.getImageUrl())
                .imageUrls(result.getImageUrls())
                .ossUrl(result.getOssUrl())
                .ossUrls(result.getOssUrls())
                .vendor(result.getVendor())
                .model(result.getModel())
                .generationTimeMs(result.getGenerationTimeMs())
                .fromCache(result.getFromCache())
                .revisedPrompt(result.getRevisedPrompt())
                .build();

        messageProducer.sendMessage(MQTopic.IMAGE_GENERATION, EventType.IMAGE_GENERATION_SUCCESS, event);
        log.info("发送生成成功消息: userId={}, recordId={}, taskId={}", userId, recordId, taskId);
    }

    /**
     * 发送生成失败消息到MQ
     */
    private void sendFailureMessage(Long userId, Long recordId, String taskId, String errorMessage) {
        ImageGenerationFailedEvent event = ImageGenerationFailedEvent.builder()
                .userId(userId)
                .recordId(recordId)
                .taskId(taskId)
                .errorMessage(errorMessage)
                .build();

        messageProducer.sendMessage(MQTopic.IMAGE_GENERATION, EventType.IMAGE_GENERATION_FAILED, event);
        log.info("发送生成失败消息: userId={}, recordId={}, taskId={}", userId, recordId, taskId);
    }

    private GenerationResult doGenerateAvatarFromImagesAsync(Long userId, String prompt, String style, String modelId,
                                                             List<MultipartFile> sourceImages, List<String> sourceImageUrls,
                                                             String responseFormat, String aspectRatio,
                                                             String imageSize, Map<String, Object> extraParams,
                                                             String taskId) {
        checkQuotaAndModelAccess(userId, modelId, true);

        List<String> imageBase64List = toBase64List(sourceImages);
        List<String> imageDigests = toImageDigests(sourceImages, sourceImageUrls);
        StyleTemplate template = getStyleTemplate(style);
        GenerationRequest request = buildImageToImageRequest(
                prompt, style, template, modelId, imageBase64List, sourceImageUrls,
                responseFormat, aspectRatio, imageSize, extraParams);
        String cacheKey = buildImageToImageCacheKey(request, imageDigests);

        GenerationResult cached = getCachedResult(cacheKey);
        if (cached != null) {
            log.info("命中图生图本地缓存, key: {}, userId: {}", cacheKey, userId);
            GenerationRecord cachedRecord = GenerationRecord.builder()
                    .userId(userId)
                    .prompt(prompt)
                    .negativePrompt(request.getNegativePrompt())
                    .style(style)
                    .status(0)
                    .build();
            generationRecordMapper.insert(cachedRecord);
            sendSuccessMessage(userId, cachedRecord.getId(), taskId, cached);
            return GenerationResult.builder()
                    .taskId(taskId)
                    .imageUrl(cached.getImageUrl())
                    .imageBase64(cached.getImageBase64())
                    .imageUrls(cached.getImageUrls())
                    .imageBase64List(cached.getImageBase64List())
                    .ossUrl(cached.getOssUrl())
                    .ossUrls(cached.getOssUrls())
                    .revisedPrompt(cached.getRevisedPrompt())
                    .vendor(cached.getVendor())
                    .model(cached.getModel())
                    .generationTimeMs(0L)
                    .fromCache(true)
                    .build();
        }

        GenerationRecord record = GenerationRecord.builder()
                .userId(userId)
                .prompt(prompt)
                .negativePrompt(request.getNegativePrompt())
                .style(style)
                .status(0)
                .build();
        generationRecordMapper.insert(record);
        log.info("插入图生图记录, recordId: {}, userId: {}", record.getId(), userId);

        log.info("图生图请求, prompt: {}, negativePrompt: {}, imageCount: {}", request.getPrompt(),
                request.getNegativePrompt(),
                imageBase64List.size() + (sourceImageUrls == null ? 0 : sourceImageUrls.size()));

        try {
            GenerationResult result = imageGenerationService.generateImageFromImage(request);
            sendSuccessMessage(userId, record.getId(), taskId, result);
            putCachedResult(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.error("图生图失败", e);
            sendFailureMessage(userId, record.getId(), taskId, e.getMessage());
            throw new BusinessException("图生图失败: " + e.getMessage());
        }
    }

    private void checkQuotaAndModelAccess(Long userId, String modelId, boolean requireImageToImageSupport) {
        try {
            Result<Boolean> checkResult = userServiceClient.checkQuota(userId);
            if (checkResult.getCode() != 200 || !Boolean.TRUE.equals(checkResult.getData())) {
                throw new BusinessException("额度不足，请充值或签到获取额度");
            }
        } catch (Exception e) {
            log.error("检查额度失败", e);
            throw new BusinessException("额度检查失败: " + e.getMessage());
        }

        if (!StringUtils.hasText(modelId)) {
            return;
        }

        int userVipLevel = 0;
        try {
            Result<Integer> vipResult = userServiceClient.getVipLevel(userId);
            if (vipResult.getCode() == 200 && vipResult.getData() != null) {
                userVipLevel = vipResult.getData();
            }
        } catch (Exception e) {
            log.warn("获取VIP等级失败，默认为0: {}", e.getMessage());
        }

        List<ModelInfo> models = multiVendorImageService.getModelList(userVipLevel);
        ModelInfo selectedModel = models.stream()
                .filter(model -> model.getModelId().equals(modelId))
                .findFirst()
                .orElse(null);
        if (selectedModel == null || !selectedModel.isAvailable()) {
            throw new BusinessException("您的VIP等级不足以使用该模型，请升级会员");
        }
        if (requireImageToImageSupport && !selectedModel.isSupportsImageInput()) {
            throw new BusinessException("当前模型暂不支持图生图，请切换支持图生图的模型");
        }
    }

    private String buildTextCacheKey(GenerationRequest request) {
        String content = request.getPrompt()
                + "|" + (request.getNegativePrompt() != null ? request.getNegativePrompt() : "")
                + "|" + (request.getStyle() != null ? request.getStyle() : "")
                + "|" + (request.getModelId() != null ? request.getModelId() : "")
                + "|" + (request.getAspectRatio() != null ? request.getAspectRatio() : "1:1")
                + "|" + (request.getImageSize() != null ? request.getImageSize() : "1K")
                + "|" + stringifyExtraParams(request.getExtraParams());
        return "avatar:" + DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
    }

    private String buildImageToImageCacheKey(GenerationRequest request, List<String> imageDigests) {
        String content = request.getPrompt()
                + "|" + (request.getNegativePrompt() != null ? request.getNegativePrompt() : "")
                + "|" + (request.getStyle() != null ? request.getStyle() : "")
                + "|" + (request.getModelId() != null ? request.getModelId() : "")
                + "|" + (request.getResponseFormat() != null ? request.getResponseFormat() : "url")
                + "|" + (request.getAspectRatio() != null ? request.getAspectRatio() : "")
                + "|" + (request.getImageSize() != null ? request.getImageSize() : "")
                + "|" + stringifyExtraParams(request.getExtraParams())
                + "|" + String.join(",", imageDigests);
        return "avatar:edit:" + DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
    }

    private GenerationRequest buildRequest(String prompt, String style, StyleTemplate template, String modelId,
                                           String aspectRatio, String imageSize,
                                           Map<String, Object> extraParams) {
        GenerationRequest request = new GenerationRequest();

        if (template != null && template.getPromptTemplate() != null
                && template.getPromptTemplate().contains("{prompt}")) {
            // 将用户prompt填充到风格模板的 {prompt} 占位符中
            String fullPrompt = template.getPromptTemplate().replace("{prompt}", prompt);
            request.setPrompt(fullPrompt);
        } else if (template != null) {
            // 模板没有占位符，使用默认拼接
            request.setPrompt(prompt + ", high quality, detailed");
        } else {
            // 无模板，添加基础质量描述
            request.setPrompt(prompt + ", high quality, detailed");
        }

        if (template != null && StringUtils.hasText(template.getNegativePrompt())) {
            request.setNegativePrompt(template.getNegativePrompt());
        }
        request.setStyle(style);
        request.setSize("1024x1024");
        request.setQuality("hd");
        if (modelId != null && !modelId.isBlank()) {
            request.setModelId(modelId);
        }
        if (StringUtils.hasText(aspectRatio)) {
            request.setAspectRatio(aspectRatio);
        }
        if (StringUtils.hasText(imageSize)) {
            request.setImageSize(imageSize);
        }
        if (extraParams != null && !extraParams.isEmpty()) {
            request.setExtraParams(new LinkedHashMap<>(extraParams));
        }
        return request;
    }

    private StyleTemplate getStyleTemplate(String style) {
        if (!StringUtils.hasText(style)) {
            return null;
        }
        return styleTemplateService.getTemplateByNameEn(style);
    }

    private GenerationRequest buildImageToImageRequest(String prompt, String style, StyleTemplate template,
                                                       String modelId, List<String> sourceImageBase64List,
                                                       List<String> sourceImageUrls,
                                                       String responseFormat, String aspectRatio, String imageSize,
                                                       Map<String, Object> extraParams) {
        GenerationRequest request = buildRequest(prompt, style, template, modelId, null, null, extraParams);
        request.setSourceImageBase64List(sourceImageBase64List);
        request.setSourceImageUrls(sourceImageUrls);
        if (!sourceImageBase64List.isEmpty()) {
            request.setSourceImageBase64(sourceImageBase64List.get(0));
        }
        if (sourceImageUrls != null && !sourceImageUrls.isEmpty()) {
            request.setSourceImageUrl(sourceImageUrls.get(0));
        }
        if (StringUtils.hasText(responseFormat)) {
            request.setResponseFormat(responseFormat);
        }
        if (StringUtils.hasText(aspectRatio)) {
            request.setAspectRatio(aspectRatio);
        }
        if (StringUtils.hasText(imageSize)) {
            request.setImageSize(imageSize);
        }
        return request;
    }

    private String stringifyExtraParams(Map<String, Object> extraParams) {
        if (extraParams == null || extraParams.isEmpty()) {
            return "{}";
        }
        return new TreeMap<>(extraParams).toString();
    }

    private List<String> toBase64List(List<MultipartFile> sourceImages) {
        if (sourceImages == null || sourceImages.isEmpty()) {
            return List.of();
        }
        return sourceImages.stream()
                .map(this::toBase64)
                .toList();
    }

    private List<String> toImageDigests(List<MultipartFile> sourceImages, List<String> sourceImageUrls) {
        List<String> digests = sourceImages.stream()
                .map(this::toDigest)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (sourceImageUrls != null && !sourceImageUrls.isEmpty()) {
            sourceImageUrls.stream()
                    .filter(StringUtils::hasText)
                    .map(url -> DigestUtils.md5DigestAsHex(url.getBytes(StandardCharsets.UTF_8)))
                    .forEach(digests::add);
        }
        if (digests.isEmpty()) {
            throw new BusinessException("请至少上传一张参考图片");
        }
        return digests;
    }

    private String toBase64(MultipartFile file) {
        try {
            return Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            throw new BusinessException("读取参考图片失败: " + file.getOriginalFilename());
        }
    }

    private String toDigest(MultipartFile file) {
        try {
            return DigestUtils.md5DigestAsHex(file.getBytes());
        } catch (IOException e) {
            throw new BusinessException("计算参考图片摘要失败: " + file.getOriginalFilename());
        }
    }

    private GenerationResult getCachedResult(String cacheKey) {
        return localCache.getIfPresent(cacheKey);
    }

    private void putCachedResult(String cacheKey, GenerationResult result) {
        localCache.put(cacheKey, result);
        log.info("存入本地缓存, key: {}, ttlHours: {}", cacheKey, LOCAL_CACHE_TTL.toHours());
    }
}
