package com.wwt.pixel.image.ai.adapter;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.image.domain.GenerationRequest;
import com.wwt.pixel.image.domain.GenerationResult;
import com.wwt.pixel.image.ai.ImageModelAdapter;
import com.wwt.pixel.image.ai.ImageVendor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * OpenAI兼容适配器 - 支持各种兼容OpenAI API的第三方服务
 * 通过构造函数注入配置，支持创建多个实例
 */
@Slf4j
public class OpenAiCompatibleAdapter implements ImageModelAdapter {

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
    private final RestTemplate restTemplate = new RestTemplate();

    public OpenAiCompatibleAdapter(String vendorCode, String vendorName, String apiKey,
                                   String baseUrl, String model, int weight, int timeoutMs) {
        this(vendorCode, vendorName, apiKey, baseUrl, model, weight, timeoutMs, null, null, 0);
    }

    public OpenAiCompatibleAdapter(String vendorCode, String vendorName, String apiKey,
                                   String baseUrl, String model, int weight, int timeoutMs,
                                   String modelId, String modelDisplayName, int minVipLevel) {
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
    }

    @Override
    public ImageVendor getVendor() {
        ImageVendor vendor = ImageVendor.fromCode(vendorCode);
        return vendor != null ? vendor : ImageVendor.OPENAI;
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
    public GenerationResult generate(GenerationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            String finalPrompt = buildPrompt(request);
            log.debug("[{}] 生成图片, prompt: {}", vendorCode, finalPrompt);

            String url = baseUrl.endsWith("/") ? baseUrl + "v1/images/generations"
                    : baseUrl + "/v1/images/generations";

            Map<String, Object> requestBody = buildRequestBody(finalPrompt, request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            String imageUrl = extractImageUrl(response.getBody());
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[{}] 图片生成成功, 耗时: {}ms", vendorCode, elapsed);

            return GenerationResult.builder()
                    .imageUrl(imageUrl)
                    .revisedPrompt(finalPrompt)
                    .vendor(vendorCode)
                    .model(model)
                    .generationTimeMs(elapsed)
                    .fromCache(false)
                    .build();

        } catch (Exception e) {
            log.error("[{}] 图片生成失败", vendorCode, e);
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
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("n", 1);
        body.put("size", request.getSize() != null ? request.getSize() : "1024x1024");
        if ("hd".equalsIgnoreCase(request.getQuality())) {
            body.put("quality", "hd");
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private String extractImageUrl(Map response) {
        if (response == null) {
            throw new BusinessException(1003, "API返回为空");
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        if (data == null || data.isEmpty()) {
            throw new BusinessException(1003, "未返回图片数据");
        }
        Object url = data.get(0).get("url");
        if (url != null) {
            return url.toString();
        }
        Object b64Json = data.get(0).get("b64_json");
        if (b64Json != null) {
            return "data:image/png;base64," + b64Json;
        }
        throw new BusinessException(1003, "未找到图片URL或Base64数据");
    }

    private String buildPrompt(GenerationRequest request) {
        String prompt = request.getPrompt();
        if (StringUtils.hasText(request.getNegativePrompt())) {
            prompt += ", avoid: " + request.getNegativePrompt();
        }
        return prompt;
    }
}
