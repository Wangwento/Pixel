package com.wwt.pixel.image.service;

import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.image.ai.ImageGenerationService;
import com.wwt.pixel.image.domain.GenerationRecord;
import com.wwt.pixel.image.domain.GenerationRequest;
import com.wwt.pixel.image.domain.GenerationResult;
import com.wwt.pixel.image.domain.StyleTemplate;
import com.wwt.pixel.image.feign.UserServiceClient;
import com.wwt.pixel.image.mapper.GenerationRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 头像生成应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarGenerationService {

    private final ImageGenerationService imageGenerationService;
    private final StyleTemplateService styleTemplateService;
    private final UserServiceClient userServiceClient;
    private final GenerationRecordMapper generationRecordMapper;

    // 本地缓存 (相同prompt直接返回)
    private final Map<String, GenerationResult> localCache = new ConcurrentHashMap<>();

    /**
     * 文生图
     */
    public GenerationResult generateAvatar(Long userId, String prompt, String style) {
        // 1. 检查用户额度
        try {
            Result<Boolean> checkResult = userServiceClient.checkQuota(userId);
            if (checkResult.getCode() != 200 || !Boolean.TRUE.equals(checkResult.getData())) {
                throw new BusinessException("额度不足，请充值或签到获取额度");
            }
        } catch (Exception e) {
            log.error("检查额度失败", e);
            throw new BusinessException("额度检查失败: " + e.getMessage());
        }

        // 2. 构建缓存key
        String cacheKey = buildCacheKey(prompt, style);

        // 3. 查询缓存
        GenerationResult cached = localCache.get(cacheKey);
        if (cached != null) {
            log.info("命中本地缓存, key: {}, userId: {}", cacheKey, userId);

            // 缓存命中也要扣费
            try {
                Result<String> consumeResult = userServiceClient.consumeQuota(userId);
                if (consumeResult.getCode() != 200) {
                    throw new BusinessException(consumeResult.getMessage());
                }
                log.info("额度消耗成功(缓存): userId={}, type={}", userId, consumeResult.getData());
            } catch (Exception e) {
                log.error("缓存命中但额度扣减失败", e);
                throw new BusinessException("额度不足");
            }

            return GenerationResult.builder()
                    .imageUrl(cached.getImageUrl())
                    .imageBase64(cached.getImageBase64())
                    .ossUrl(cached.getOssUrl())
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
                .style(style)
                .status(0)  // 生成中
                .build();
        generationRecordMapper.insert(record);
        log.info("插入生成记录, recordId: {}, userId: {}", record.getId(), userId);

        // 5. 获取风格模板并构建请求
        StyleTemplate template = null;
        if (style != null && !style.isBlank()) {
            template = styleTemplateService.getTemplateByNameEn(style);
        }

        GenerationRequest request = buildRequest(prompt, style, template);
        log.info("文生图请求, prompt: {}", request.getPrompt());

        // 6. 调用AI生成
        GenerationResult result;
        try {
            result = imageGenerationService.generateImage(request);

            // 7. 生成成功，更新记录状态
            record.setStatus(1);  // 成功
            // 优先使用OSS URL，如果没有则使用原始URL
            String finalUrl = result.getOssUrl() != null ? result.getOssUrl() : result.getImageUrl();
            record.setResultImageUrl(finalUrl);
            record.setVendor(result.getVendor());
            record.setModel(result.getModel());
            record.setCost(BigDecimal.valueOf(0.2));  // 假设每张图0.2元成本
            generationRecordMapper.updateStatus(record);
            log.info("更新生成记录为成功, recordId: {}, url: {}", record.getId(), finalUrl);

            // 8. 生成成功后扣减额度
            try {
                Result<String> consumeResult = userServiceClient.consumeQuota(userId);
                if (consumeResult.getCode() != 200) {
                    log.error("生成成功但额度扣减失败: userId={}, message={}", userId, consumeResult.getMessage());
                    // 这里可以考虑补偿机制，但暂时不影响返回结果
                } else {
                    log.info("额度消耗成功: userId={}, type={}", userId, consumeResult.getData());
                }
            } catch (Exception e) {
                log.error("额度扣减异常", e);
                // 不影响返回结果
            }

            // 9. 存入缓存
            localCache.put(cacheKey, result);
            log.info("存入缓存, key: {}", cacheKey);

            return result;

        } catch (Exception e) {
            log.error("图片生成失败", e);

            // 10. 生成失败，更新记录状态（不扣额度）
            record.setStatus(2);  // 失败
            record.setErrorMessage(e.getMessage());
            generationRecordMapper.updateStatus(record);
            log.info("更新生成记录为失败, recordId: {}", record.getId());

            throw new BusinessException("图片生成失败: " + e.getMessage());
        }
    }

    /**
     * 构建缓存key (基于prompt和style的MD5)
     */
    private String buildCacheKey(String prompt, String style) {
        String content = prompt
                + "|" + (style != null ? style : "")
                + "|1024x1024|hd";  // 固定尺寸和质量
        return "avatar:" + DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
    }

    private GenerationRequest buildRequest(String prompt, String style, StyleTemplate template) {
        GenerationRequest request = new GenerationRequest();

        if (template != null) {
            // 使用模板构建prompt
            String fullPrompt = template.getNameEn() + "风格，" + prompt;
            if (template.getPromptTemplate() != null) {
                fullPrompt += "，" + template.getPromptTemplate();
            }
            request.setPrompt(fullPrompt);
        } else {
            request.setPrompt(prompt);
        }

        request.setStyle(style);
        request.setSize("1024x1024");
        request.setQuality("hd");
        return request;
    }
}
