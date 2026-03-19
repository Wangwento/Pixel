package com.wwt.pixel.image.ai.adapter;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.image.ai.ImageModelAdapter;
import com.wwt.pixel.image.ai.ImageVendor;
import com.wwt.pixel.image.ai.config.AiVendorProperties;
import com.wwt.pixel.image.domain.GenerationRequest;
import com.wwt.pixel.image.domain.GenerationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通用 JSON 图片生成适配器
 * 适用于 /v1/images/generations + application/json 的图片生成接口。
 */
@Slf4j
public class JsonImageGenerationsAdapter implements ImageModelAdapter {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String vendorCode;
    private final String vendorName;
    private final String modelId;
    private final String modelDisplayName;
    private final int minVipLevel;
    private final int weight;
    private final int timeoutMs;
    private final boolean supportsImageInput;
    private final boolean supportsImageSize;
    private final List<AiVendorProperties.ModelParamConfig> modelParams;
    private final String aspectRatio;
    private final String imageSize;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private static final Set<String> RESERVED_KEYS = Set.of("model", "prompt", "image");

    public JsonImageGenerationsAdapter(String vendorCode, String vendorName,
                                       String apiKey, String baseUrl, String model,
                                       int weight, int timeoutMs,
                                       String modelId, String modelDisplayName,
                                       int minVipLevel, boolean supportsImageInput,
                                       boolean supportsImageSize,
                                       List<AiVendorProperties.ModelParamConfig> modelParams,
                                       String aspectRatio, String imageSize) {
        this.vendorCode = vendorCode;
        this.vendorName = vendorName;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.weight = weight;
        this.timeoutMs = timeoutMs;
        this.modelId = modelId;
        this.modelDisplayName = modelDisplayName;
        this.minVipLevel = minVipLevel;
        this.supportsImageInput = supportsImageInput;
        this.supportsImageSize = supportsImageSize;
        this.modelParams = modelParams == null ? Collections.emptyList() : List.copyOf(modelParams);
        this.aspectRatio = aspectRatio;
        this.imageSize = imageSize;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(Integer.MAX_VALUE)
                .build());
    }

    @Override
    public ImageVendor getVendor() {
        ImageVendor vendor = ImageVendor.fromCode(vendorCode);
        return vendor != null ? vendor : ImageVendor.PLATO;
    }

    public String getVendorCode() {
        return vendorCode;
    }

    public String getVendorName() {
        return vendorName;
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(baseUrl);
    }

    @Override
    public boolean supportsImageInput() {
        return supportsImageInput;
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            String finalPrompt = buildPrompt(request);
            String url = baseUrl.endsWith("/")
                    ? baseUrl + "v1/images/generations"
                    : baseUrl + "/v1/images/generations";

            Map<String, Object> requestBody = buildRequestBody(finalPrompt, request);
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("[{}] JSON图片生成请求体: {}", vendorCode, jsonBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            ImageData imageData = extractImageData(response.getBody());
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[{}] JSON图片生成成功, 耗时: {}ms", vendorCode, elapsed);

            return GenerationResult.builder()
                    .imageUrl(imageData.primaryUrl())
                    .imageBase64(imageData.primaryBase64())
                    .imageUrls(imageData.urls())
                    .imageBase64List(imageData.base64List())
                    .revisedPrompt(finalPrompt)
                    .vendor(vendorCode)
                    .model(model)
                    .generationTimeMs(elapsed)
                    .fromCache(false)
                    .build();
        } catch (Exception e) {
            log.error("[{}] JSON图片生成失败", vendorCode, e);
            throw new BusinessException(1003, vendorName + "服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public int getWeight() {
        return weight;
    }

    @Override
    public int getTimeoutMs() {
        return timeoutMs;
    }

    @Override
    public String getModelId() {
        return modelId;
    }

    @Override
    public String getModelDisplayName() {
        return modelDisplayName;
    }

    @Override
    public int getMinVipLevel() {
        return minVipLevel;
    }

    private Map<String, Object> buildRequestBody(String prompt, GenerationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);

        applyDefaultParams(body);
        applyRequestParams(body, request.resolveExtraParams());

        if (!body.containsKey("response_format")) {
            body.put("response_format", StringUtils.hasText(request.getResponseFormat()) ? request.getResponseFormat() : "url");
        } else if (StringUtils.hasText(request.getResponseFormat())) {
            body.put("response_format", request.getResponseFormat());
        }

        String finalAspectRatio = StringUtils.hasText(request.getAspectRatio())
                ? request.getAspectRatio()
                : aspectRatio;
        if (StringUtils.hasText(finalAspectRatio)) {
            body.put("aspect_ratio", finalAspectRatio);
        }

        if (supportsImageSize) {
            String finalImageSize = StringUtils.hasText(request.getImageSize())
                    ? request.getImageSize()
                    : imageSize;
            if (StringUtils.hasText(finalImageSize)) {
                body.put("image_size", finalImageSize);
            }
        }

        List<String> images = collectImages(request);
        if (!images.isEmpty()) {
            body.put("image", images);
        }
        return body;
    }

    private void applyDefaultParams(Map<String, Object> body) {
        for (AiVendorProperties.ModelParamConfig modelParam : modelParams) {
            if (modelParam == null || !StringUtils.hasText(modelParam.getParamKey())) {
                continue;
            }
            Object defaultValue = parseParamValue(modelParam.getParamType(), modelParam.getDefaultValue());
            if (defaultValue == null) {
                continue;
            }
            body.putIfAbsent(modelParam.getParamKey(), defaultValue);
        }
    }

    private void applyRequestParams(Map<String, Object> body, Map<String, Object> extraParams) {
        if (extraParams == null || extraParams.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : extraParams.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || RESERVED_KEYS.contains(entry.getKey())) {
                continue;
            }
            Object normalized = normalizeRequestParamValue(entry.getValue());
            if (normalized != null) {
                body.put(entry.getKey(), normalized);
            }
        }
    }

    private List<String> collectImages(GenerationRequest request) {
        List<String> images = new ArrayList<>();
        images.addAll(request.resolveSourceImageUrls());
        images.addAll(request.resolveSourceImageBase64List());
        return images;
    }

    @SuppressWarnings("unchecked")
    private ImageData extractImageData(String responseBody) throws Exception {
        if (!StringUtils.hasText(responseBody)) {
            throw new BusinessException(1003, "API返回为空");
        }
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        if (data == null || data.isEmpty()) {
            throw new BusinessException(1003, "未返回图片数据");
        }
        List<String> urls = new ArrayList<>();
        List<String> base64List = new ArrayList<>();
        for (Map<String, Object> item : data) {
            Object url = item.get("url");
            if (url != null) {
                urls.add(url.toString());
            }
            Object b64 = item.get("b64_json");
            if (b64 != null) {
                base64List.add(b64.toString());
            }
        }
        if (urls.isEmpty() && base64List.isEmpty()) {
            throw new BusinessException(1003, "未找到图片URL或Base64数据");
        }
        return new ImageData(
                urls.isEmpty() ? null : urls.get(0),
                base64List.isEmpty() ? null : base64List.get(0),
                urls.isEmpty() ? null : urls,
                base64List.isEmpty() ? null : base64List
        );
    }

    private String buildPrompt(GenerationRequest request) {
        String prompt = request.getPrompt();
        if (StringUtils.hasText(request.getNegativePrompt())) {
            prompt += ", avoid: " + request.getNegativePrompt();
        }
        return prompt;
    }

    private Object parseParamValue(String paramType, String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        return switch (paramType) {
            case "number" -> {
                try {
                    yield rawValue.contains(".") ? Double.parseDouble(rawValue) : Integer.parseInt(rawValue);
                } catch (NumberFormatException ignored) {
                    yield null;
                }
            }
            case "boolean" -> Boolean.parseBoolean(rawValue);
            case "multiSelect", "array", "object" -> tryParseJson(rawValue);
            default -> rawValue;
        };
    }

    private Object normalizeRequestParamValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue) {
            if (!StringUtils.hasText(stringValue)) {
                return null;
            }
            return stringValue;
        }
        return value;
    }

    private Object tryParseJson(String rawValue) {
        try {
            return objectMapper.readValue(rawValue, Object.class);
        } catch (Exception ignored) {
            return rawValue;
        }
    }

    private record ImageData(
            String primaryUrl,
            String primaryBase64,
            List<String> urls,
            List<String> base64List
    ) {
    }
}
