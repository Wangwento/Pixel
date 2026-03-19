package com.wwt.pixel.audio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.audio.config.AudioProviderProperties;
import com.wwt.pixel.audio.controller.AudioController;
import com.wwt.pixel.audio.dto.AudioModelInfo;
import com.wwt.pixel.audio.dto.ModelParamInfo;
import com.wwt.pixel.audio.mapper.AudioGenerationRecordMapper;
import com.wwt.pixel.audio.util.TaskIdCodec;
import com.wwt.pixel.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioService {

    private final RestTemplate audioRestTemplate;
    private final AudioProviderProperties properties;
    private final ObjectMapper objectMapper;
    private final AudioPersistenceService audioPersistenceService;
    private final AudioDirectUploadService audioDirectUploadService;
    private final AudioGenerationRecordMapper audioGenerationRecordMapper;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    private volatile List<AudioProviderProperties.CompatibleVendorConfig> allVendors = List.of();
    private volatile Map<String, List<ModelParamInfo>> cachedModelParamsMap = Map.of();

    @PostConstruct
    public void init() {
        refreshVendors(properties.getEffectiveVendors());
        log.info("已加载 {} 个AI音频生成适配配置", allVendors.size());
        allVendors.forEach(vendor -> log.info("  - {} (modelId={}, available={}, weight={})",
                vendor.getName(), vendor.getModelId(), vendor.isEnabled(), vendor.getWeight()));
    }

    public List<AudioModelInfo> getModels() {
        Map<String, AudioModelInfo> modelMap = new LinkedHashMap<>();
        for (AudioProviderProperties.CompatibleVendorConfig vendor : allVendors) {
            if (!vendor.isEnabled() || !StringUtils.hasText(vendor.getModelId())) {
                continue;
            }
            modelMap.compute(vendor.getModelId(), (modelId, existing) ->
                    existing == null ? createModelInfo(vendor) : mergeModelInfo(existing, vendor));
        }
        return new ArrayList<>(modelMap.values());
    }

    public Object submitMusic(Long userId, AudioController.MusicRequest request) {
        AudioProviderProperties.CompatibleVendorConfig vendor = resolveVendorForModel(request.getMv());
        Map<String, Object> payload = buildMusicPayload(request, vendor);
        Object response = post(vendor, "/suno/submit/music", payload);
        Object decorated = decorateTaskResponse(vendor, response);
        audioPersistenceService.createSubmitRecord(userId, request, vendor, decorated);
        audioPersistenceService.syncTaskResult(decorated);
        return attachPersistenceMeta(decorated);
    }

    public Object submitLyrics(AudioController.LyricsRequest request) {
        AudioProviderProperties.CompatibleVendorConfig vendor = selectDefaultVendor();
        Object response = post(vendor, "/suno/submit/lyrics", toRequestMap(request));
        return decorateTaskResponse(vendor, response);
    }

    public Object submitConcat(AudioController.ConcatRequest request) {
        return post(selectDefaultVendor(), "/suno/submit/concat", toRequestMap(request));
    }

    public Object createUpload(AudioController.CreateUploadRequest request) {
        return post(selectDefaultVendor(), "/sunoi/uploads/audio", toRequestMap(request));
    }

    public Object uploadByUrl(AudioController.UploadByUrlRequest request) {
        return post(selectDefaultVendor(), "/suno/uploads/audio-url", toRequestMap(request));
    }

    public Object finishUpload(String uploadId, AudioController.FinishUploadRequest request) {
        return post(selectDefaultVendor(), "/sunoi/uploads/audio/" + uploadId + "/upload-finish", toRequestMap(request));
    }

    public Object getUpload(String uploadId) {
        return get(selectDefaultVendor(), "/sunoi/uploads/audio/" + uploadId, null);
    }

    public Object initializeClip(String uploadId) {
        return post(selectDefaultVendor(), "/sunoi/uploads/audio/" + uploadId + "/initialize-clip", null);
    }

    public Object createPersona(AudioController.PersonaRequest request) {
        return post(selectDefaultVendor(), "/suno/persona/create/", toRequestMap(request));
    }

    public Object expandTags(AudioController.TagsRequest request) {
        return post(selectDefaultVendor(), "/suno/act/tags", toRequestMap(request));
    }

    public Object getTask(String taskId, String action) {
        Map<String, String> queryParams = new HashMap<>();
        if (StringUtils.hasText(action)) {
            queryParams.put("action", action);
        }
        ResolvedTaskId resolvedTaskId = resolveTaskId(taskId);
        if (resolvedTaskId != null) {
            Object response = get(resolvedTaskId.vendor(), "/suno/fetch/" + resolvedTaskId.providerTaskId(), queryParams);
            Object decorated = decorateTaskResponse(resolvedTaskId.vendor(), response, resolvedTaskId.providerTaskId());
            audioPersistenceService.syncTaskResult(decorated);
            return attachPersistenceMeta(decorated);
        }

        List<AudioProviderProperties.CompatibleVendorConfig> availableVendors = allVendors.stream()
                .filter(AudioProviderProperties.CompatibleVendorConfig::isEnabled)
                .toList();
        BusinessException lastException = null;
        for (AudioProviderProperties.CompatibleVendorConfig vendor : availableVendors) {
            try {
                Object response = get(vendor, "/suno/fetch/" + taskId, queryParams);
                Object decorated = decorateTaskResponse(vendor, response, taskId);
                audioPersistenceService.syncTaskResult(decorated);
                return attachPersistenceMeta(decorated);
            } catch (BusinessException exception) {
                lastException = exception;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new BusinessException(400, "无法识别任务所属供应商，请使用系统返回的 taskId 查询");
    }

    public Object batchFetch(AudioController.BatchFetchRequest request) {
        Map<AudioProviderProperties.CompatibleVendorConfig, List<String>> groupedIds = groupTaskIds(request.getIds());
        List<Object> results = new ArrayList<>();
        for (Map.Entry<AudioProviderProperties.CompatibleVendorConfig, List<String>> entry : groupedIds.entrySet()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ids", entry.getValue());
            if (StringUtils.hasText(request.getAction())) {
                payload.put("action", request.getAction());
            }
            Object upstream = post(entry.getKey(), "/suno/fetch", payload);
            audioPersistenceService.syncBatchTaskResults(upstream);
            Map<String, Object> decorated = new LinkedHashMap<>();
            decorated.put("vendor", entry.getKey().getCode());
            decorated.put("model", entry.getKey().getModelId());
            decorated.put("providerTaskIds", entry.getValue());
            decorated.put("data", upstream);
            results.add(decorated);
        }
        if (results.size() == 1) {
            return results.get(0);
        }
        return Map.of("results", results);
    }

    public Object getTiming(String clipId) {
        return get(selectDefaultVendor(), "/suno/act/timing/" + clipId, null);
    }

    public Object getWav(String clipId) {
        return get(selectDefaultVendor(), "/suno/act/wav/" + clipId, null);
    }

    public Object getDirectUploadPolicy(Long userId, AudioController.DirectUploadPolicyRequest request) {
        return audioDirectUploadService.createDirectUploadPolicy(userId, request);
    }

    public Object finalizeTaskAssets(Long userId, String taskId, AudioController.FinalizeTaskAssetsRequest request) {
        audioPersistenceService.finalizeUploadedAssets(userId, taskId, request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("recordStatus", "SUCCESS".equalsIgnoreCase(request.getStatus()) ? "SUCCESS" : "FAILED");
        result.put("assetReady", "SUCCESS".equalsIgnoreCase(request.getStatus()));
        return result;
    }

    public void refreshVendors(List<AudioProviderProperties.CompatibleVendorConfig> vendors) {
        List<AudioProviderProperties.CompatibleVendorConfig> source = (vendors == null || vendors.isEmpty())
                ? properties.getEffectiveVendors()
                : vendors;
        List<AudioProviderProperties.CompatibleVendorConfig> refreshed = source.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeVendor)
                .filter(vendor -> StringUtils.hasText(vendor.getCode())
                        && StringUtils.hasText(vendor.getModelId())
                        && StringUtils.hasText(vendor.getProviderModel()))
                .toList();

        Map<String, List<ModelParamInfo>> paramsMap = new LinkedHashMap<>();
        for (AudioProviderProperties.CompatibleVendorConfig vendor : refreshed) {
            paramsMap.put(vendor.getModelId(), convertParams(vendor.getParams()));
        }

        this.allVendors = Collections.unmodifiableList(new ArrayList<>(refreshed));
        this.cachedModelParamsMap = Collections.unmodifiableMap(paramsMap);
        this.roundRobinIndex.set(0);
        log.info("音频厂商列表已热刷新，当前 {} 个模型配置", allVendors.size());
    }

    private AudioModelInfo createModelInfo(AudioProviderProperties.CompatibleVendorConfig vendor) {
        return AudioModelInfo.builder()
                .modelId(vendor.getModelId())
                .displayName(StringUtils.hasText(vendor.getModelDisplayName()) ? vendor.getModelDisplayName() : vendor.getName())
                .description(vendor.getDescription())
                .minVipLevel(vendor.getMinVipLevel())
                .available(vendor.isEnabled())
                .supportedTasks(new ArrayList<>(vendor.getSupportedTasks()))
                .costPerUnit(vendor.getCostPerUnit())
                .params(copyModelParams(vendor.getModelId()))
                .build();
    }

    private AudioModelInfo mergeModelInfo(AudioModelInfo current,
                                          AudioProviderProperties.CompatibleVendorConfig incoming) {
        if (!StringUtils.hasText(current.getDescription()) && StringUtils.hasText(incoming.getDescription())) {
            current.setDescription(incoming.getDescription());
        }
        current.setAvailable(current.isAvailable() || incoming.isEnabled());
        current.setMinVipLevel(Math.min(current.getMinVipLevel(), incoming.getMinVipLevel()));
        if ((current.getCostPerUnit() == null || current.getCostPerUnit().signum() == 0)
                && incoming.getCostPerUnit() != null) {
            current.setCostPerUnit(incoming.getCostPerUnit());
        }
        current.setSupportedTasks(mergeStrings(current.getSupportedTasks(), incoming.getSupportedTasks()));
        current.setParams(mergeModelParams(current.getParams(), copyModelParams(incoming.getModelId())));
        return current;
    }

    private List<String> mergeStrings(List<String> current, List<String> incoming) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        if (current != null) {
            current.stream().filter(StringUtils::hasText).forEach(item -> merged.put(item, item));
        }
        if (incoming != null) {
            incoming.stream().filter(StringUtils::hasText).forEach(item -> merged.put(item, item));
        }
        return new ArrayList<>(merged.keySet());
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

    private List<ModelParamInfo> copyModelParams(String modelId) {
        List<ModelParamInfo> params = cachedModelParamsMap.get(modelId);
        if (params == null || params.isEmpty()) {
            return new ArrayList<>();
        }
        return params.stream()
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
    }

    private List<ModelParamInfo> convertParams(List<AudioProviderProperties.ModelParamConfig> params) {
        if (params == null || params.isEmpty()) {
            return List.of();
        }
        return params.stream()
                .filter(Objects::nonNull)
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
    }

    private AudioProviderProperties.CompatibleVendorConfig normalizeVendor(AudioProviderProperties.CompatibleVendorConfig vendor) {
        AudioProviderProperties.CompatibleVendorConfig normalized = new AudioProviderProperties.CompatibleVendorConfig();
        normalized.setCode(StringUtils.hasText(vendor.getCode()) ? vendor.getCode() : "audio-" + vendor.getModelId());
        normalized.setName(StringUtils.hasText(vendor.getName()) ? vendor.getName() : vendor.getCode());
        normalized.setModelCode(vendor.getModelCode());
        normalized.setModelId(vendor.getModelId());
        normalized.setProviderModel(vendor.getProviderModel());
        normalized.setModelDisplayName(StringUtils.hasText(vendor.getModelDisplayName()) ? vendor.getModelDisplayName() : vendor.getModelId());
        normalized.setDescription(vendor.getDescription());
        normalized.setMinVipLevel(vendor.getMinVipLevel());
        normalized.setEnabled(vendor.isEnabled());
        normalized.setApiKey(StringUtils.hasText(vendor.getApiKey()) ? vendor.getApiKey() : properties.getApiKey());
        normalized.setBaseUrl(StringUtils.hasText(vendor.getBaseUrl()) ? vendor.getBaseUrl() : properties.getBaseUrl());
        normalized.setWeight(vendor.getWeight() > 0 ? vendor.getWeight() : 1);
        normalized.setTimeout(vendor.getTimeout() > 0 ? vendor.getTimeout() : Math.max(properties.getTimeout(), 10_000));
        normalized.setCostPerUnit(vendor.getCostPerUnit() != null ? vendor.getCostPerUnit() : BigDecimal.ZERO);
        normalized.setSupportedTasks(vendor.getSupportedTasks() == null ? new ArrayList<>() : new ArrayList<>(vendor.getSupportedTasks()));
        normalized.setParams(vendor.getParams() == null ? new ArrayList<>() : new ArrayList<>(vendor.getParams()));
        return normalized;
    }

    private AudioProviderProperties.CompatibleVendorConfig resolveVendorForModel(String requestedModelId) {
        if (!StringUtils.hasText(requestedModelId)) {
            return selectDefaultVendor();
        }
        List<AudioProviderProperties.CompatibleVendorConfig> candidates = allVendors.stream()
                .filter(AudioProviderProperties.CompatibleVendorConfig::isEnabled)
                .filter(vendor -> requestedModelId.equalsIgnoreCase(vendor.getModelId())
                        || requestedModelId.equalsIgnoreCase(vendor.getProviderModel())
                        || requestedModelId.equalsIgnoreCase(vendor.getCode()))
                .toList();
        if (candidates.isEmpty()) {
            throw new BusinessException(400, "未找到可用的音频模型: " + requestedModelId);
        }
        return selectVendor(candidates);
    }

    private AudioProviderProperties.CompatibleVendorConfig selectDefaultVendor() {
        List<AudioProviderProperties.CompatibleVendorConfig> candidates = allVendors.stream()
                .filter(AudioProviderProperties.CompatibleVendorConfig::isEnabled)
                .toList();
        if (candidates.isEmpty()) {
            throw new BusinessException(500, "当前没有可用的音频服务配置");
        }
        return selectVendor(candidates);
    }

    private AudioProviderProperties.CompatibleVendorConfig selectVendor(List<AudioProviderProperties.CompatibleVendorConfig> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if ("round-robin".equalsIgnoreCase(properties.getStrategy())) {
            int index = Math.floorMod(roundRobinIndex.getAndIncrement(), candidates.size());
            return candidates.get(index);
        }
        return candidates.get(0);
    }

    private ResolvedTaskId resolveTaskId(String taskId) {
        TaskIdCodec.DecodedTaskId decodedTaskId = TaskIdCodec.decode(taskId);
        if (decodedTaskId != null) {
            AudioProviderProperties.CompatibleVendorConfig vendor = allVendors.stream()
                    .filter(AudioProviderProperties.CompatibleVendorConfig::isEnabled)
                    .filter(item -> decodedTaskId.vendorCode().equalsIgnoreCase(item.getCode()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(400, "任务对应的音频供应商不可用: " + decodedTaskId.vendorCode()));
            return new ResolvedTaskId(vendor, decodedTaskId.providerTaskId());
        }
        var record = audioGenerationRecordMapper.findByProviderTaskId(taskId);
        if (record != null && StringUtils.hasText(record.getVendor())) {
            AudioProviderProperties.CompatibleVendorConfig vendor = allVendors.stream()
                    .filter(AudioProviderProperties.CompatibleVendorConfig::isEnabled)
                    .filter(item -> record.getVendor().equalsIgnoreCase(item.getCode()))
                    .findFirst()
                    .orElse(null);
            if (vendor != null) {
                return new ResolvedTaskId(vendor, taskId);
            }
        }
        List<AudioProviderProperties.CompatibleVendorConfig> available = allVendors.stream()
                .filter(AudioProviderProperties.CompatibleVendorConfig::isEnabled)
                .toList();
        if (available.size() == 1) {
            return new ResolvedTaskId(available.getFirst(), taskId);
        }
        return null;
    }

    private Map<AudioProviderProperties.CompatibleVendorConfig, List<String>> groupTaskIds(List<String> ids) {
        Map<AudioProviderProperties.CompatibleVendorConfig, List<String>> grouped = new LinkedHashMap<>();
        for (String taskId : ids) {
            ResolvedTaskId resolvedTaskId = resolveTaskId(taskId);
            grouped.computeIfAbsent(resolvedTaskId.vendor(), key -> new ArrayList<>())
                    .add(resolvedTaskId.providerTaskId());
        }
        return grouped;
    }

    private Map<String, Object> toRequestMap(Object payload) {
        if (payload == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = objectMapper.convertValue(payload, new TypeReference<>() {
        });
        result.entrySet().removeIf(entry -> entry.getValue() == null
                || (entry.getValue() instanceof String text && !StringUtils.hasText(text)));
        return result;
    }

    private Map<String, Object> toFilteredRequestMap(Object payload,
                                                     AudioProviderProperties.CompatibleVendorConfig vendor) {
        Map<String, Object> requestMap = toRequestMap(payload);
        if (vendor == null || vendor.getParams() == null || vendor.getParams().isEmpty()) {
            return requestMap;
        }
        Set<String> allowedKeys = new HashSet<>();
        vendor.getParams().stream()
                .filter(Objects::nonNull)
                .map(AudioProviderProperties.ModelParamConfig::getParamKey)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(allowedKeys::add);
        requestMap.entrySet().removeIf(entry -> !allowedKeys.contains(entry.getKey()));
        return requestMap;
    }

    private Map<String, Object> buildMusicPayload(AudioController.MusicRequest request,
                                                  AudioProviderProperties.CompatibleVendorConfig vendor) {
        Map<String, Object> payload = toRequestMap(request);
        payload.put("prompt", trimToEmpty(request.getPrompt()));
        if (StringUtils.hasText(request.getTitle())) {
            payload.put("title", request.getTitle().trim());
        }
        payload.put("mv", StringUtils.hasText(vendor.getProviderModel())
                ? vendor.getProviderModel().trim()
                : trimToEmpty(request.getMv()));
        payload.put("tags", trimToEmpty(request.getTags()));
        payload.put("task", trimToEmpty(request.getTask()));
        payload.put("make_instrumental", Boolean.TRUE.equals(request.getMakeInstrumental()));
        payload.put("continue_at", resolveContinueAt(request));
        payload.put("continue_clip_id", trimToEmpty(request.getContinueClipId()));
        return payload;
    }

    private int resolveContinueAt(AudioController.MusicRequest request) {
        if (request.getContinueAt() != null) {
            return request.getContinueAt();
        }
        return StringUtils.hasText(request.getContinueClipId()) ? 0 : 120;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private Object decorateTaskResponse(AudioProviderProperties.CompatibleVendorConfig vendor, Object response) {
        String providerTaskId = extractProviderTaskId(response);
        return decorateTaskResponse(vendor, response, providerTaskId);
    }

    private Object attachPersistenceMeta(Object response) {
        if (!(response instanceof Map<?, ?> rawMap)) {
            return response;
        }
        Map<String, Object> decorated = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> decorated.put(String.valueOf(key), value));
        String taskId = stringValue(decorated.get("taskId"));
        String providerTaskId = firstNonBlank(
                stringValue(decorated.get("providerTaskId")),
                stringValue(decorated.get("task_id")),
                stringValue(decorated.get("id"))
        );
        var record = audioPersistenceService.findGenerationRecord(taskId, providerTaskId);
        if (record == null) {
            return decorated;
        }
        decorated.put("recordStatus", record.getStatus());
        decorated.put("assetReady", "SUCCESS".equalsIgnoreCase(record.getStatus()));

        Object nested = decorated.get("data");
        if (nested instanceof Map<?, ?> rawNestedMap) {
            Map<String, Object> nestedMap = new LinkedHashMap<>();
            rawNestedMap.forEach((key, value) -> nestedMap.put(String.valueOf(key), value));
            nestedMap.put("recordStatus", record.getStatus());
            nestedMap.put("assetReady", "SUCCESS".equalsIgnoreCase(record.getStatus()));
            nestedMap.putIfAbsent("taskId", taskId);
            nestedMap.putIfAbsent("providerTaskId", providerTaskId);
            nestedMap.putIfAbsent("vendor", decorated.get("vendor"));
            nestedMap.putIfAbsent("model", decorated.get("model"));
            decorated.put("data", nestedMap);
        }
        return decorated;
    }

    private Object decorateTaskResponse(AudioProviderProperties.CompatibleVendorConfig vendor,
                                        Object response,
                                        String providerTaskId) {
        if (!StringUtils.hasText(providerTaskId)) {
            return response;
        }
        String taskId = TaskIdCodec.encode(vendor.getCode(), providerTaskId);
        if (response instanceof Map<?, ?> rawMap) {
            Map<String, Object> decorated = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> decorated.put(String.valueOf(key), value));
            decorated.put("taskId", taskId);
            decorated.put("providerTaskId", providerTaskId);
            decorated.putIfAbsent("vendor", vendor.getCode());
            decorated.putIfAbsent("model", vendor.getModelId());
            return decorated;
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("taskId", taskId);
        wrapped.put("providerTaskId", providerTaskId);
        wrapped.put("vendor", vendor.getCode());
        wrapped.put("model", vendor.getModelId());
        wrapped.put("data", response);
        return wrapped;
    }

    private String extractProviderTaskId(Object response) {
        if (response instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        if (!(response instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Object providerTaskId = rawMap.get("providerTaskId");
        if (providerTaskId != null && StringUtils.hasText(String.valueOf(providerTaskId))) {
            return String.valueOf(providerTaskId);
        }
        for (String key : List.of("task_id", "id")) {
            Object value = rawMap.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        Object data = rawMap.get("data");
        if (data instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        if (data instanceof Map<?, ?> nestedMap) {
            return extractProviderTaskId(new LinkedHashMap<>(nestedMap));
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : trimToEmpty(String.valueOf(value));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Object post(AudioProviderProperties.CompatibleVendorConfig vendor, String path, Object body) {
        return exchange(vendor, path, HttpMethod.POST, body, null);
    }

    private Object get(AudioProviderProperties.CompatibleVendorConfig vendor, String path, Map<String, String> queryParams) {
        return exchange(vendor, path, HttpMethod.GET, null, queryParams);
    }

    private Object exchange(AudioProviderProperties.CompatibleVendorConfig vendor,
                            String path,
                            HttpMethod method,
                            Object body,
                            Map<String, String> queryParams) {
        String url = buildUrl(vendor, path, queryParams);
        HttpHeaders headers = buildHeaders(vendor);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = audioRestTemplate.exchange(url, method, entity, String.class);
            return parseResponseBody(response.getBody());
        } catch (RestClientResponseException exception) {
            log.error("音频上游接口调用失败, method={}, path={}, status={}, body={}",
                    method, path, exception.getStatusCode().value(), exception.getResponseBodyAsString());
            throw new BusinessException(500, "音频服务调用失败: " + resolveErrorMessage(exception));
        } catch (Exception exception) {
            log.error("音频接口调用异常, method={}, path={}", method, path, exception);
            throw new BusinessException(500, "音频服务调用异常: " + exception.getMessage());
        }
    }

    private HttpHeaders buildHeaders(AudioProviderProperties.CompatibleVendorConfig vendor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
        if (StringUtils.hasText(vendor.getApiKey())) {
            headers.setBearerAuth(vendor.getApiKey().trim());
        }
        return headers;
    }

    private String buildUrl(AudioProviderProperties.CompatibleVendorConfig vendor,
                            String path,
                            Map<String, String> queryParams) {
        if (!StringUtils.hasText(vendor.getBaseUrl())) {
            throw new BusinessException(500, "未配置音频服务 baseUrl");
        }
        String baseUrl = vendor.getBaseUrl().trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + path);
        if (queryParams != null) {
            queryParams.forEach((key, value) -> {
                if (StringUtils.hasText(value)) {
                    builder.queryParam(key, value);
                }
            });
        }
        return builder.build(true).toUriString();
    }

    private Object parseResponseBody(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return Map.of("success", true, "message", "empty response body");
        }
        try {
            return objectMapper.readValue(responseBody, Object.class);
        } catch (JsonProcessingException exception) {
            return Map.of("raw", responseBody);
        }
    }

    private String resolveErrorMessage(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            return exception.getStatusText();
        }
        try {
            Object parsed = objectMapper.readValue(body, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Object message = map.get("message");
                if (message != null) {
                    return String.valueOf(message);
                }
                Object error = map.get("error");
                if (error != null) {
                    return String.valueOf(error);
                }
            }
        } catch (Exception ignored) {
        }
        return body;
    }

    private record ResolvedTaskId(AudioProviderProperties.CompatibleVendorConfig vendor,
                                  String providerTaskId) {
    }
}
