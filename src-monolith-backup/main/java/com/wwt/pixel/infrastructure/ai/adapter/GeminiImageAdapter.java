package com.wwt.pixel.infrastructure.ai.adapter;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.domain.model.GenerationRequest;
import com.wwt.pixel.domain.model.GenerationResult;
import com.wwt.pixel.infrastructure.ai.ImageModelAdapter;
import com.wwt.pixel.infrastructure.ai.ImageVendor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
@ConditionalOnProperty(name = "pixel.ai.gemini.enabled", havingValue = "true")
public class GeminiImageAdapter implements ImageModelAdapter {

    @Value("${pixel.ai.gemini.api-key:}")
    private String apiKey;

    @Value("${pixel.ai.gemini.base-url:https://generativelanguage.googleapis.com}")
    private String baseUrl;

    @Value("${pixel.ai.gemini.model:gemini-2.0-flash-exp-image-generation}")
    private String model;

    @Value("${pixel.ai.gemini.weight:2}")
    private int weight;

    @Value("${pixel.ai.gemini.timeout:60000}")
    private int timeoutMs;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public ImageVendor getVendor() {
        return ImageVendor.GEMINI;
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.hasText(apiKey);
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            String finalPrompt = buildPrompt(request);
            log.debug("[Gemini] 生成图片, prompt: {}", finalPrompt);

            String url = baseUrl + "/v1beta/models/" + model + ":generateContent?key=" + apiKey;

            Map<String, Object> requestBody = buildRequestBody(finalPrompt);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            String imageData = extractImageFromResponse(response.getBody());
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[Gemini] 图片生成成功, 耗时: {}ms", elapsed);

            return GenerationResult.builder()
                    .imageBase64(imageData)
                    .revisedPrompt(finalPrompt)
                    .vendor(getVendor().getCode())
                    .model(model)
                    .generationTimeMs(elapsed)
                    .fromCache(false)
                    .build();

        } catch (Exception e) {
            log.error("[Gemini] 图片生成失败", e);
            throw new BusinessException(1003, "Gemini服务调用失败: " + e.getMessage());
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

    private Map<String, Object> buildRequestBody(String prompt) {
        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();

        Map<String, Object> content = new HashMap<>();
        List<Map<String, Object>> parts = new ArrayList<>();
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);
        parts.add(textPart);
        content.put("parts", parts);
        contents.add(content);

        body.put("contents", contents);

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseModalities", List.of("TEXT", "IMAGE"));
        body.put("generationConfig", generationConfig);

        return body;
    }

    @SuppressWarnings("unchecked")
    private String extractImageFromResponse(Map response) {
        if (response == null) {
            throw new BusinessException(1003, "Gemini返回为空");
        }

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new BusinessException(1003, "Gemini未返回候选结果");
        }

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

        for (Map<String, Object> part : parts) {
            if (part.containsKey("inlineData")) {
                Map<String, Object> inlineData = (Map<String, Object>) part.get("inlineData");
                return (String) inlineData.get("data");
            }
        }

        throw new BusinessException(1003, "Gemini未返回图片数据");
    }

    private String buildPrompt(GenerationRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate an avatar image. ");
        if (StringUtils.hasText(request.getStyle())) {
            sb.append("Style: ").append(request.getStyle()).append(". ");
        }
        sb.append("Description: ").append(request.getPrompt());
        sb.append(". High quality portrait, detailed.");
        if (StringUtils.hasText(request.getNegativePrompt())) {
            sb.append(" Avoid: ").append(request.getNegativePrompt());
        }
        return sb.toString();
    }
}
