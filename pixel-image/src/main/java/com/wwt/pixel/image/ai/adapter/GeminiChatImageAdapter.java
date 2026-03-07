package com.wwt.pixel.image.ai.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Gemini 图像生成适配器
 * 使用 Chat Completions API 生成图片（通过一步API等中转服务）
 */
@Slf4j
public class GeminiChatImageAdapter implements ImageModelAdapter {

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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiChatImageAdapter(String vendorCode, String vendorName, String apiKey,
                                   String baseUrl, String model, int weight, int timeoutMs) {
        this(vendorCode, vendorName, apiKey, baseUrl, model, weight, timeoutMs, null, null, 0);
    }

    public GeminiChatImageAdapter(String vendorCode, String vendorName, String apiKey,
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
        return vendor != null ? vendor : ImageVendor.GEMINI;
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
            log.debug("[{}] 生成图片(Chat API), prompt: {}", vendorCode, finalPrompt);

            String url = baseUrl.endsWith("/") ? baseUrl + "v1/chat/completions"
                    : baseUrl + "/v1/chat/completions";

            Map<String, Object> requestBody = buildChatRequestBody(finalPrompt);
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("[{}] 请求体: {}", vendorCode, jsonBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            log.debug("[{}] 原始响应: {}", vendorCode, response.getBody());

            String imageData = extractImageFromChatResponse(response.getBody());
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[{}] 图片生成成功, 耗时: {}ms", vendorCode, elapsed);

            return GenerationResult.builder()
                    .imageBase64(imageData.startsWith("data:") ? imageData.split(",")[1] : null)
                    .imageUrl(imageData.startsWith("http") ? imageData : null)
                    .revisedPrompt(finalPrompt)
                    .vendor(vendorCode)
                    .model(model)
                    .generationTimeMs(elapsed)
                    .fromCache(false)
                    .build();

        } catch (BusinessException e) {
            throw e;
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

    private Map<String, Object> buildChatRequestBody(String prompt) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", false);

        // 消息列表
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", "请生成一张图片：" + prompt);
        messages.add(userMessage);
        body.put("messages", messages);

        return body;
    }

    @SuppressWarnings("unchecked")
    private String extractImageFromChatResponse(String responseBody) throws Exception {
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new BusinessException(1003, "未返回有效响应");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            throw new BusinessException(1003, "未返回消息内容");
        }

        // 尝试从 content 中提取图片
        Object content = message.get("content");

        // 情况1: content 是字符串，可能包含 base64 或 URL
        if (content instanceof String) {
            String contentStr = (String) content;
            // 检查是否是 base64 图片
            if (contentStr.contains("data:image")) {
                int start = contentStr.indexOf("data:image");
                int end = contentStr.indexOf("\"", start);
                if (end == -1) end = contentStr.length();
                return contentStr.substring(start, end);
            }
            // 检查是否包含图片URL
            if (contentStr.contains("http") && (contentStr.contains(".png") || contentStr.contains(".jpg") || contentStr.contains(".jpeg") || contentStr.contains(".webp"))) {
                // 提取URL
                int start = contentStr.indexOf("http");
                int end = contentStr.indexOf(" ", start);
                if (end == -1) end = contentStr.indexOf(")", start);
                if (end == -1) end = contentStr.indexOf("\"", start);
                if (end == -1) end = contentStr.length();
                return contentStr.substring(start, end);
            }
            throw new BusinessException(1003, "响应中未找到图片: " + contentStr.substring(0, Math.min(200, contentStr.length())));
        }

        // 情况2: content 是数组（多模态响应）
        if (content instanceof List) {
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) content;
            for (Map<String, Object> item : contentList) {
                String type = (String) item.get("type");
                if ("image_url".equals(type)) {
                    Map<String, Object> imageUrl = (Map<String, Object>) item.get("image_url");
                    if (imageUrl != null && imageUrl.get("url") != null) {
                        return imageUrl.get("url").toString();
                    }
                }
                if ("image".equals(type)) {
                    Object data = item.get("data");
                    if (data != null) {
                        return "data:image/png;base64," + data;
                    }
                }
            }
        }

        throw new BusinessException(1003, "无法从响应中提取图片");
    }

    private String buildPrompt(GenerationRequest request) {
        String prompt = request.getPrompt();
        if (StringUtils.hasText(request.getNegativePrompt())) {
            prompt += "。请避免: " + request.getNegativePrompt();
        }
        return prompt;
    }
}
