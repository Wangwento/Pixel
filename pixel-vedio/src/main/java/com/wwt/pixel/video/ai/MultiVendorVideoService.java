package com.wwt.pixel.video.ai;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.video.ai.adapter.PlatoVideoAdapter;
import com.wwt.pixel.video.ai.config.AiVendorProperties;
import com.wwt.pixel.video.domain.VideoAsset;
import com.wwt.pixel.video.domain.VideoGenerationRecord;
import com.wwt.pixel.video.domain.VideoGenerationRequest;
import com.wwt.pixel.video.domain.VideoSubmitResult;
import com.wwt.pixel.video.domain.VideoTaskResult;
import com.wwt.pixel.video.dto.ModelInfo;
import com.wwt.pixel.video.dto.ModelParamInfo;
import com.wwt.pixel.video.service.VideoPersistenceService;
import com.wwt.pixel.video.util.TaskIdCodec;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiVendorVideoService {

    private volatile List<VideoModelAdapter> allAdapters = List.of();
    private volatile Map<String, List<ModelParamInfo>> cachedModelParamsMap = Map.of();
    private final AiVendorProperties properties;
    private final VideoPersistenceService videoPersistenceService;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final Random random = new Random();

    @PostConstruct
    public void init() {
        refreshVendors(properties.getVendors());
        log.info("已加载 {} 个AI视频生成适配器", allAdapters.size());
        allAdapters.forEach(adapter -> log.info("  - {} (available: {}, weight: {})",
                adapter.getVendorName(), adapter.isAvailable(), adapter.getWeight()));
    }

    public VideoSubmitResult submitTextTask(VideoGenerationRequest request) {
        return submitInternal(request, GenerationMode.TEXT);
    }

    public VideoSubmitResult submitImageTask(VideoGenerationRequest request) {
        if (!request.hasSourceImages()) {
            throw new BusinessException(400, "图生视频模式需要提供参考图片");
        }
        return submitInternal(request, GenerationMode.IMAGE);
    }

    public VideoTaskResult queryTask(String taskId) {
        ResolvedVideoTask resolvedTask = resolveTask(taskId, null);
        try {
            return queryAndSyncTask(resolvedTask);
        } catch (BusinessException exception) {
            VideoTaskResult fallbackResult = buildFallbackTaskResult(resolvedTask, exception);
            if (fallbackResult != null) {
                log.warn("视频任务查询降级为本地记录: taskId={}, providerTaskId={}, vendor={}, reason={}",
                        fallbackResult.getTaskId(),
                        fallbackResult.getProviderTaskId(),
                        fallbackResult.getVendor(),
                        exception.getMessage());
                return fallbackResult;
            }
            throw exception;
        }
    }

    public Map<String, Object> recoverTask(String taskIdOrProviderTaskId, String vendorCode) {
        ResolvedVideoTask resolvedTask = resolveTask(taskIdOrProviderTaskId, vendorCode);
        try {
            VideoTaskResult taskResult = queryAndSyncTask(resolvedTask);
            VideoGenerationRecord record = videoPersistenceService.findRecord(taskResult.getTaskId(), taskResult.getProviderTaskId());
            VideoAsset asset = record == null ? null : videoPersistenceService.findAssetByGenerationRecordId(record.getId());
            return buildRecoveryPayload(true, false, "已重新查询上游并回填任务结果", taskResult, record, asset);
        } catch (BusinessException exception) {
            VideoTaskResult fallbackResult = buildFallbackTaskResult(resolvedTask, exception);
            if (fallbackResult != null) {
                VideoGenerationRecord record = videoPersistenceService.findRecord(fallbackResult.getTaskId(), fallbackResult.getProviderTaskId());
                VideoAsset asset = record == null ? null : videoPersistenceService.findAssetByGenerationRecordId(record.getId());
                return buildRecoveryPayload(false, true, "上游查询暂时失败，已返回本地记录，可稍后再次补查", fallbackResult, record, asset);
            }
            throw exception;
        }
    }

    public List<ModelInfo> getModelList(int userVipLevel) {
        Map<String, ModelInfo> modelMap = new LinkedHashMap<>();
        for (VideoModelAdapter adapter : allAdapters) {
            if (!adapter.isAvailable()) {
                continue;
            }
            String modelId = adapter.getModelId();
            if (!StringUtils.hasText(modelId)) {
                continue;
            }
            modelMap.compute(modelId, (key, existing) ->
                    existing == null
                            ? createModelInfo(adapter, userVipLevel)
                            : mergeModelInfo(existing, adapter, userVipLevel));
        }
        return new ArrayList<>(modelMap.values());
    }

    private ModelInfo createModelInfo(VideoModelAdapter adapter, int userVipLevel) {
        return ModelInfo.builder()
                .modelId(adapter.getModelId())
                .displayName(adapter.getModelDisplayName())
                .family(adapter.getModelFamily())
                .minVipLevel(adapter.getMinVipLevel())
                .available(userVipLevel >= adapter.getMinVipLevel())
                .textToVideoSupported(adapter.supportsTextToVideo())
                .imageToVideoSupported(adapter.supportsImageInput())
                .supportedAspectRatios(new ArrayList<>(adapter.getSupportedAspectRatios()))
                .supportedTextDurations(new ArrayList<>(adapter.getSupportedTextDurations()))
                .supportedImageDurations(new ArrayList<>(adapter.getSupportedImageDurations()))
                .supportsHd(adapter.supportsHd())
                .supportsEnhancePrompt(adapter.supportsEnhancePrompt())
                .supportsUpsample(adapter.supportsUpsample())
                .minImageCount(adapter.getMinImageCount())
                .maxImageCount(adapter.getMaxImageCount())
                .defaultAspectRatio(adapter.getDefaultAspectRatio())
                .defaultTextDuration(adapter.getDefaultTextDuration())
                .defaultImageDuration(adapter.getDefaultImageDuration())
                .costPerSecond(adapter.getCostPerSecond())
                .defaultHd(adapter.isDefaultHdEnabled())
                .defaultEnhancePrompt(adapter.isDefaultEnhancePromptEnabled())
                .defaultUpsample(adapter.isDefaultUpsampleEnabled())
                .params(copyModelParams(adapter.getModelId()))
                .build();
    }

    private ModelInfo mergeModelInfo(ModelInfo existing, VideoModelAdapter adapter, int userVipLevel) {
        existing.setAvailable(existing.isAvailable() || userVipLevel >= adapter.getMinVipLevel());
        existing.setMinVipLevel(Math.min(existing.getMinVipLevel(), adapter.getMinVipLevel()));
        if (!StringUtils.hasText(existing.getDisplayName())) {
            existing.setDisplayName(adapter.getModelDisplayName());
        }
        if (!StringUtils.hasText(existing.getFamily())) {
            existing.setFamily(adapter.getModelFamily());
        }
        existing.setTextToVideoSupported(existing.isTextToVideoSupported() || adapter.supportsTextToVideo());
        existing.setImageToVideoSupported(existing.isImageToVideoSupported() || adapter.supportsImageInput());
        existing.setSupportedAspectRatios(mergeUnique(existing.getSupportedAspectRatios(), adapter.getSupportedAspectRatios()));
        existing.setSupportedTextDurations(mergeUnique(existing.getSupportedTextDurations(), adapter.getSupportedTextDurations()));
        existing.setSupportedImageDurations(mergeUnique(existing.getSupportedImageDurations(), adapter.getSupportedImageDurations()));
        existing.setSupportsHd(existing.isSupportsHd() || adapter.supportsHd());
        existing.setSupportsEnhancePrompt(existing.isSupportsEnhancePrompt() || adapter.supportsEnhancePrompt());
        existing.setSupportsUpsample(existing.isSupportsUpsample() || adapter.supportsUpsample());

        if (adapter.supportsImageInput()) {
            if (existing.getMinImageCount() <= 0) {
                existing.setMinImageCount(adapter.getMinImageCount());
            } else if (adapter.getMinImageCount() > 0) {
                existing.setMinImageCount(Math.min(existing.getMinImageCount(), adapter.getMinImageCount()));
            }
            existing.setMaxImageCount(Math.max(existing.getMaxImageCount(), adapter.getMaxImageCount()));
        }

        if (!StringUtils.hasText(existing.getDefaultAspectRatio())) {
            existing.setDefaultAspectRatio(adapter.getDefaultAspectRatio());
        }
        if (!StringUtils.hasText(existing.getDefaultTextDuration())) {
            existing.setDefaultTextDuration(adapter.getDefaultTextDuration());
        }
        if (!StringUtils.hasText(existing.getDefaultImageDuration())) {
            existing.setDefaultImageDuration(adapter.getDefaultImageDuration());
        }
        if (existing.getCostPerSecond() == null || existing.getCostPerSecond().signum() <= 0) {
            existing.setCostPerSecond(adapter.getCostPerSecond());
        }
        existing.setDefaultHd(existing.isDefaultHd() || adapter.isDefaultHdEnabled());
        existing.setDefaultEnhancePrompt(existing.isDefaultEnhancePrompt() || adapter.isDefaultEnhancePromptEnabled());
        existing.setDefaultUpsample(existing.isDefaultUpsample() || adapter.isDefaultUpsampleEnabled());
        existing.setParams(mergeModelParams(existing.getParams(), cachedModelParamsMap.get(adapter.getModelId())));
        return existing;
    }

    private List<String> mergeUnique(List<String> current, List<String> incoming) {
        List<String> values = new ArrayList<>();
        if (current != null) {
            current.stream().filter(StringUtils::hasText).forEach(values::add);
        }
        if (incoming != null) {
            incoming.stream()
                    .filter(StringUtils::hasText)
                    .filter(value -> !values.contains(value))
                    .forEach(values::add);
        }
        return values;
    }

    public void refreshVendors(List<AiVendorProperties.CompatibleVendorConfig> vendorConfigs) {
        List<VideoModelAdapter> newAdapters = new ArrayList<>();
        List<AiVendorProperties.CompatibleVendorConfig> configs =
                vendorConfigs == null ? List.of() : vendorConfigs;
        Map<String, List<ModelParamInfo>> paramsMap = new LinkedHashMap<>();

        for (AiVendorProperties.CompatibleVendorConfig vendor : configs) {
            String modelId = vendor.getModelId();
            if (!StringUtils.hasText(modelId) || vendor.getParams() == null || vendor.getParams().isEmpty()) {
                continue;
            }
            List<ModelParamInfo> paramInfos = vendor.getParams().stream()
                    .map(param -> ModelParamInfo.builder()
                            .paramKey(param.getParamKey())
                            .paramName(param.getParamName())
                            .paramType(param.getParamType())
                            .required(param.getRequired())
                            .visible(param.getVisible())
                            .defaultValue(param.getDefaultValue())
                            .options(param.getOptions())
                            .validationRule(param.getValidationRule())
                            .description(param.getDescription())
                            .displayOrder(param.getDisplayOrder())
                            .build())
                    .toList();
            paramsMap.put(modelId, mergeModelParams(paramsMap.get(modelId), paramInfos));
        }

        for (AiVendorProperties.CompatibleVendorConfig vendor : configs) {
            if (!vendor.isEnabled()) {
                log.debug("跳过禁用厂商: {}", vendor.getCode());
                continue;
            }
            if (!StringUtils.hasText(vendor.getApiKey()) || !StringUtils.hasText(vendor.getBaseUrl())) {
                log.debug("厂商 {} 缺少 apiKey/baseUrl，跳过", vendor.getCode());
                continue;
            }

            String vendorCode = StringUtils.hasText(vendor.getCode()) ? vendor.getCode() : "plato";
            String vendorName = StringUtils.hasText(vendor.getName()) ? vendor.getName() : "柏拉图AI";
            newAdapters.add(new PlatoVideoAdapter(
                    vendorCode,
                    vendorName,
                    vendor));
        }

        this.allAdapters = Collections.unmodifiableList(newAdapters);
        this.cachedModelParamsMap = Collections.unmodifiableMap(paramsMap);
        roundRobinIndex.set(0);
        log.info("视频厂商列表已热刷新，当前 {} 个适配器", newAdapters.size());
    }

    private List<ModelParamInfo> copyModelParams(String modelId) {
        List<ModelParamInfo> params = cachedModelParamsMap.get(modelId);
        if (params == null || params.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(params);
    }

    private List<ModelParamInfo> mergeModelParams(List<ModelParamInfo> current, List<ModelParamInfo> incoming) {
        LinkedHashMap<String, ModelParamInfo> merged = new LinkedHashMap<>();
        if (current != null) {
            for (ModelParamInfo param : current) {
                if (param != null && StringUtils.hasText(param.getParamKey())) {
                    merged.put(param.getParamKey(), param);
                }
            }
        }
        if (incoming != null) {
            for (ModelParamInfo param : incoming) {
                if (param != null && StringUtils.hasText(param.getParamKey())) {
                    merged.putIfAbsent(param.getParamKey(), param);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private VideoSubmitResult submitInternal(VideoGenerationRequest request, GenerationMode generationMode) {
        if (StringUtils.hasText(request.getModelId())) {
            return submitByModelId(request, request.getModelId(), generationMode);
        }
        if (StringUtils.hasText(request.getVendor())) {
            return submitWithSpecificVendor(request, request.getVendor(), generationMode);
        }
        return submitWithStrategy(request, generationMode);
    }

    private VideoSubmitResult submitWithSpecificVendor(VideoGenerationRequest request, String vendorCode,
                                                       GenerationMode generationMode) {
        VideoModelAdapter adapter = findAdapterByVendor(vendorCode);
        if (adapter == null || !adapter.isAvailable()) {
            throw new BusinessException(1003, "指定的AI厂商不可用: " + vendorCode);
        }
        validateModeSupport(adapter, generationMode, vendorCode);
        return persistSubmitResult(request, adapter, adapter.submitTask(request));
    }

    private VideoSubmitResult submitWithStrategy(VideoGenerationRequest request, GenerationMode generationMode) {
        List<VideoModelAdapter> availableAdapters = getAvailableAdapters(generationMode);
        if (availableAdapters.isEmpty()) {
            throw new BusinessException(1003,
                    generationMode == GenerationMode.IMAGE
                            ? "当前没有支持图生视频的AI服务"
                            : "当前没有支持文生视频的AI服务");
        }

        VideoModelAdapter selected = selectAdapter(availableAdapters);
        List<VideoModelAdapter> tried = new ArrayList<>();
        tried.add(selected);

        while (true) {
            try {
                log.info("使用 {} 提交视频任务", selected.getVendorName());
                return persistSubmitResult(request, selected, selected.submitTask(request));
            } catch (Exception e) {
                log.warn("{} 调用失败: {}", selected.getVendorName(), e.getMessage());
                if (!properties.isFallbackEnabled()) {
                    throw e;
                }
                List<VideoModelAdapter> remaining = availableAdapters.stream()
                        .filter(adapter -> !tried.contains(adapter))
                        .toList();
                if (remaining.isEmpty()) {
                    throw new BusinessException(1003, "所有视频AI服务都调用失败，请稍后重试");
                }
                selected = selectAdapter(remaining);
                tried.add(selected);
                log.info("降级到 {}", selected.getVendorName());
            }
        }
    }

    private VideoSubmitResult submitByModelId(VideoGenerationRequest request, String modelId,
                                              GenerationMode generationMode) {
        List<VideoModelAdapter> candidates = allAdapters.stream()
                .filter(VideoModelAdapter::isAvailable)
                .filter(adapter -> modelId.equalsIgnoreCase(adapter.getModelId()))
                .filter(adapter -> supportsMode(adapter, generationMode))
                .toList();
        if (candidates.isEmpty()) {
            throw new BusinessException(1003,
                    generationMode == GenerationMode.IMAGE
                            ? "指定的模型暂不支持图生视频: " + modelId
                            : "指定的模型暂不支持文生视频: " + modelId);
        }

        VideoModelAdapter selected = selectAdapter(candidates);
        List<VideoModelAdapter> tried = new ArrayList<>();
        tried.add(selected);

        while (true) {
            try {
                log.info("使用 {} 提交视频任务 (modelId={})", selected.getVendorName(), modelId);
                return persistSubmitResult(request, selected, selected.submitTask(request));
            } catch (Exception e) {
                log.warn("{} 调用失败: {}", selected.getVendorName(), e.getMessage());
                if (!properties.isFallbackEnabled()) {
                    throw e;
                }
                List<VideoModelAdapter> remaining = candidates.stream()
                        .filter(adapter -> !tried.contains(adapter))
                        .toList();
                if (remaining.isEmpty()) {
                    throw new BusinessException(1003, "模型 " + modelId + " 所有代理商都调用失败");
                }
                selected = selectAdapter(remaining);
                tried.add(selected);
                log.info("降级到 {}", selected.getVendorName());
            }
        }
    }

    private VideoSubmitResult persistSubmitResult(VideoGenerationRequest request,
                                                  VideoModelAdapter adapter,
                                                  VideoSubmitResult result) {
        VideoSubmitResult enrichedResult = enrichSubmitResult(adapter, result);
        videoPersistenceService.createSubmitRecord(request, enrichedResult, adapter.getCostPerSecond());
        return enrichedResult;
    }

    private VideoSubmitResult enrichSubmitResult(VideoModelAdapter adapter, VideoSubmitResult result) {
        if (result == null || !StringUtils.hasText(result.getProviderTaskId())) {
            throw new BusinessException(1003, "任务提交成功但未返回 taskId");
        }
        result.setTaskId(TaskIdCodec.encode(adapter.getVendorCode(), result.getProviderTaskId()));
        result.setVendor(adapter.getVendorCode());
        if (!StringUtils.hasText(result.getModel())) {
            result.setModel(adapter.getModelId());
        }
        return result;
    }

    private VideoTaskResult enrichTaskResult(VideoModelAdapter adapter, String providerTaskId, VideoTaskResult result) {
        if (result == null) {
            throw new BusinessException(1003, "任务查询结果为空");
        }
        result.setTaskId(TaskIdCodec.encode(adapter.getVendorCode(), providerTaskId));
        if (!StringUtils.hasText(result.getProviderTaskId())) {
            result.setProviderTaskId(providerTaskId);
        }
        if (!StringUtils.hasText(result.getVendor())) {
            result.setVendor(adapter.getVendorCode());
        }
        if (!StringUtils.hasText(result.getModel())) {
            result.setModel(adapter.getModelId());
        }
        return result;
    }

    private VideoTaskResult queryAndSyncTask(ResolvedVideoTask resolvedTask) {
        VideoModelAdapter adapter = findAdapterByVendor(resolvedTask.vendorCode());
        if (adapter == null || !adapter.isAvailable()) {
            throw new BusinessException(1003, "任务对应的AI厂商不可用: " + resolvedTask.vendorCode());
        }
        VideoTaskResult result = adapter.queryTask(resolvedTask.providerTaskId());
        VideoTaskResult enrichedResult = enrichTaskResult(adapter, resolvedTask.providerTaskId(), result);
        if (StringUtils.hasText(resolvedTask.taskId())) {
            enrichedResult.setTaskId(resolvedTask.taskId());
        }
        return videoPersistenceService.syncTaskResult(enrichedResult);
    }

    private VideoTaskResult buildFallbackTaskResult(ResolvedVideoTask resolvedTask, BusinessException exception) {
        if (!isTransientQueryException(exception)) {
            return null;
        }
        VideoGenerationRecord record = videoPersistenceService.findRecord(resolvedTask.taskId(), resolvedTask.providerTaskId());
        if (record == null) {
            return null;
        }
        VideoTaskResult fallbackResult = videoPersistenceService.buildTaskResultFromRecord(record);
        if (!StringUtils.hasText(fallbackResult.getTaskId())) {
            fallbackResult.setTaskId(StringUtils.hasText(resolvedTask.taskId())
                    ? resolvedTask.taskId()
                    : TaskIdCodec.encode(resolvedTask.vendorCode(), resolvedTask.providerTaskId()));
        }
        if (!StringUtils.hasText(fallbackResult.getProviderTaskId())) {
            fallbackResult.setProviderTaskId(resolvedTask.providerTaskId());
        }
        if (!StringUtils.hasText(fallbackResult.getVendor())) {
            fallbackResult.setVendor(resolvedTask.vendorCode());
        }
        if (!StringUtils.hasText(fallbackResult.getModel())) {
            VideoModelAdapter adapter = findAdapterByVendor(resolvedTask.vendorCode());
            if (adapter != null) {
                fallbackResult.setModel(adapter.getModelId());
            }
        }
        if (!StringUtils.hasText(fallbackResult.getFailReason())) {
            fallbackResult.setFailReason("上游查询暂时不可用，已回退本地记录，请稍后重试");
        }
        return fallbackResult;
    }

    private boolean isTransientQueryException(BusinessException exception) {
        if (exception == null || !StringUtils.hasText(exception.getMessage())) {
            return false;
        }
        String message = exception.getMessage().toLowerCase();
        return message.contains("timed out")
                || message.contains("i/o error")
                || message.contains("service unavailable")
                || message.contains("unable to tunnel through proxy")
                || message.contains("proxy returns")
                || message.contains("503");
    }

    private Map<String, Object> buildRecoveryPayload(boolean queriedUpstream,
                                                     boolean usedFallbackRecord,
                                                     String message,
                                                     VideoTaskResult taskResult,
                                                     VideoGenerationRecord record,
                                                     VideoAsset asset) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("queriedUpstream", queriedUpstream);
        payload.put("usedFallbackRecord", usedFallbackRecord);
        payload.put("message", message);
        payload.put("taskResult", taskResult);
        payload.put("record", record);
        payload.put("asset", asset);
        return payload;
    }

    private VideoModelAdapter findAdapterByVendor(String vendorCode) {
        return allAdapters.stream()
                .filter(adapter -> adapter.getVendorCode().equalsIgnoreCase(vendorCode))
                .findFirst()
                .orElse(null);
    }

    private List<VideoModelAdapter> getAvailableAdapters(GenerationMode generationMode) {
        return allAdapters.stream()
                .filter(VideoModelAdapter::isAvailable)
                .filter(adapter -> supportsMode(adapter, generationMode))
                .toList();
    }

    private boolean supportsMode(VideoModelAdapter adapter, GenerationMode generationMode) {
        return switch (generationMode) {
            case TEXT -> adapter.supportsTextToVideo();
            case IMAGE -> adapter.supportsImageInput();
        };
    }

    private void validateModeSupport(VideoModelAdapter adapter, GenerationMode generationMode, String target) {
        if (generationMode == GenerationMode.IMAGE && !adapter.supportsImageInput()) {
            throw new BusinessException(1003, "指定的模型暂不支持图生视频: " + target);
        }
        if (generationMode == GenerationMode.TEXT && !adapter.supportsTextToVideo()) {
            throw new BusinessException(1003, "指定的模型暂不支持文生视频: " + target);
        }
    }

    private VideoModelAdapter selectAdapter(List<VideoModelAdapter> adapters) {
        if (adapters.size() == 1) {
            return adapters.get(0);
        }

        String strategy = StringUtils.hasText(properties.getStrategy())
                ? properties.getStrategy().toLowerCase()
                : "round-robin";
        return switch (strategy) {
            case "weighted", "weight" -> selectByWeight(adapters);
            case "random" -> adapters.get(random.nextInt(adapters.size()));
            default -> selectByRoundRobin(adapters);
        };
    }

    private VideoModelAdapter selectByRoundRobin(List<VideoModelAdapter> adapters) {
        int index = roundRobinIndex.getAndIncrement() % adapters.size();
        return adapters.get(index);
    }

    private VideoModelAdapter selectByWeight(List<VideoModelAdapter> adapters) {
        int totalWeight = adapters.stream().mapToInt(VideoModelAdapter::getWeight).sum();
        int randomWeight = random.nextInt(totalWeight);

        int currentWeight = 0;
        for (VideoModelAdapter adapter : adapters) {
            currentWeight += adapter.getWeight();
            if (randomWeight < currentWeight) {
                return adapter;
            }
        }
        return adapters.get(adapters.size() - 1);
    }

    private ResolvedVideoTask resolveTask(String taskIdOrProviderTaskId, String vendorCode) {
        if (!StringUtils.hasText(taskIdOrProviderTaskId)) {
            throw new BusinessException(400, "任务ID不能为空");
        }

        String queryValue = taskIdOrProviderTaskId.trim();
        VideoGenerationRecord record = videoPersistenceService.findRecord(queryValue, queryValue);
        if (record != null) {
            String resolvedVendor = StringUtils.hasText(vendorCode) ? vendorCode.trim() : record.getVendor();
            if (!StringUtils.hasText(resolvedVendor)) {
                throw new BusinessException(400, "任务记录缺少厂商编码，请补充 vendorCode");
            }
            return new ResolvedVideoTask(record.getTaskId(), record.getProviderTaskId(), resolvedVendor);
        }

        TaskIdCodec.DecodedTaskId decodedTaskId = TaskIdCodec.decode(queryValue);
        if (decodedTaskId != null) {
            return new ResolvedVideoTask(queryValue, decodedTaskId.providerTaskId(), decodedTaskId.vendorCode());
        }

        if (StringUtils.hasText(vendorCode)) {
            return new ResolvedVideoTask(null, queryValue, vendorCode.trim());
        }

        List<VideoModelAdapter> availableAdapters = allAdapters.stream()
                .filter(VideoModelAdapter::isAvailable)
                .toList();
        if (availableAdapters.size() == 1) {
            return new ResolvedVideoTask(null, queryValue, availableAdapters.get(0).getVendorCode());
        }

        throw new BusinessException(400, "任务ID无法识别；如填写的是 providerTaskId，请同时提供 vendorCode");
    }

    private enum GenerationMode {
        TEXT,
        IMAGE
    }

    private record ResolvedVideoTask(String taskId, String providerTaskId, String vendorCode) {
    }
}
