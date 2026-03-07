package com.wwt.pixel.image.ai.adapter;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.image.domain.GenerationRequest;
import com.wwt.pixel.image.domain.GenerationResult;
import com.wwt.pixel.image.ai.ImageModelAdapter;
import com.wwt.pixel.image.ai.ImageVendor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.net.URI;
import java.util.*;

/**
 * 柏拉图AI适配器 - 支持 /v1/images/edits (multipart/form-data)
 * 模型: nano-banana-2 (Pro)
 */
@Slf4j
public class PlatoImageAdapter implements ImageModelAdapter {

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
    private final String aspectRatio;
    private final String imageSize;
    private final RestTemplate restTemplate;

    private static RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate();
        // 放宽 Jackson 字符串长度限制，base64 图片可能超过默认 20MB
        rt.getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .forEach(converter -> converter.getObjectMapper().getFactory()
                        .setStreamReadConstraints(StreamReadConstraints.builder()
                                .maxStringLength(Integer.MAX_VALUE)
                                .build()));
        return rt;
    }

    public PlatoImageAdapter(String vendorCode, String vendorName,
                             String apiKey, String baseUrl, String model,
                             int weight, int timeoutMs,
                             String modelId, String modelDisplayName,
                             int minVipLevel,
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
        this.aspectRatio = aspectRatio;
        this.imageSize = imageSize;
        this.restTemplate = createRestTemplate();
    }

    @Override
    public ImageVendor getVendor() {
        ImageVendor vendor = ImageVendor.fromCode(vendorCode);
        return vendor != null ? vendor : ImageVendor.PLATO;
    }

    public String getVendorCode() { return vendorCode; }
    public String getVendorName() { return vendorName; }

    @Override
    public boolean isAvailable() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(baseUrl);
    }

    @Override
    public boolean supportsImageInput() {
        return true;
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            String finalPrompt = buildPrompt(request);
            log.debug("[{}] 柏拉图AI生成图片, prompt: {}", vendorCode, finalPrompt);

            boolean imageEditMode = request.hasSourceImages();
            String path = imageEditMode ? "v1/images/edits" : "v1/images/generations";
            String url = baseUrl.endsWith("/")
                    ? baseUrl + path
                    : baseUrl + "/" + path;

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("model", model);
            body.add("prompt", finalPrompt);
            body.add("response_format",
                    StringUtils.hasText(request.getResponseFormat()) ? request.getResponseFormat() : "url");

            String finalAspectRatio = StringUtils.hasText(request.getAspectRatio())
                    ? request.getAspectRatio() : aspectRatio;
            if (StringUtils.hasText(finalAspectRatio)) {
                body.add("aspect_ratio", finalAspectRatio);
            }
            String finalImageSize = StringUtils.hasText(request.getImageSize())
                    ? request.getImageSize() : imageSize;
            if (StringUtils.hasText(finalImageSize)) {
                body.add("image_size", finalImageSize);
            }

            if (imageEditMode) {
                addSourceImages(body, request);
            }

            HttpHeaders headers = new HttpHeaders();
            if (imageEditMode) {
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            }
            headers.setBearerAuth(apiKey);

            HttpEntity<MultiValueMap<String, Object>> entity =
                    new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class);

            Map<String, String> imageData = extractImageData(response.getBody());
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[{}] 柏拉图AI图片生成成功, 耗时: {}ms", vendorCode, elapsed);

            return GenerationResult.builder()
                    .imageUrl(imageData.get("url"))
                    .imageBase64(imageData.get("base64"))
                    .revisedPrompt(finalPrompt)
                    .vendor(vendorCode)
                    .model(model)
                    .generationTimeMs(elapsed)
                    .fromCache(false)
                    .build();
        } catch (Exception e) {
            log.error("[{}] 柏拉图AI图片生成失败", vendorCode, e);
            throw new BusinessException(1003,
                    vendorName + "服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public int getWeight() { return weight; }
    @Override
    public int getTimeoutMs() { return timeoutMs; }
    @Override
    public String getModelId() { return modelId; }
    @Override
    public String getModelDisplayName() { return modelDisplayName; }
    @Override
    public int getMinVipLevel() { return minVipLevel; }

    private void addSourceImages(MultiValueMap<String, Object> body,
                                 GenerationRequest request) {
        int imageIndex = 0;

        for (String base64 : request.resolveSourceImageBase64List()) {
            byte[] imageBytes = decodeBase64(base64);
            if (imageBytes == null) {
                continue;
            }
            imageIndex++;
            body.add("image", buildImageResource(imageBytes, imageIndex));
        }

        for (String imageUrl : request.resolveSourceImageUrls()) {
            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes == null) {
                continue;
            }
            imageIndex++;
            body.add("image", buildImageResource(imageBytes, imageIndex));
        }

        if (imageIndex == 0) {
            throw new BusinessException(400, "参考图片不能为空");
        }
    }

    private ByteArrayResource buildImageResource(byte[] data, int imageIndex) {
        return new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return "image-" + imageIndex + ".png";
            }
        };
    }

    private byte[] decodeBase64(String rawBase64) {
        if (!StringUtils.hasText(rawBase64)) {
            return null;
        }
        String base64 = rawBase64;
        if (base64.contains(",")) {
            base64 = base64.substring(base64.indexOf(",") + 1);
        }
        return Base64.getDecoder().decode(base64);
    }

    private byte[] downloadImage(String imageUrl) {
        try {
            ResponseEntity<byte[]> resp = restTemplate.getForEntity(
                    URI.create(imageUrl), byte[].class);
            return resp.getBody();
        } catch (Exception e) {
            log.warn("下载参考图失败: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractImageData(Map response) {
        if (response == null) {
            throw new BusinessException(1003, "API返回为空");
        }
        List<Map<String, Object>> data =
                (List<Map<String, Object>>) response.get("data");
        if (data == null || data.isEmpty()) {
            throw new BusinessException(1003, "未返回图片数据");
        }
        Map<String, String> result = new HashMap<>();
        Object url = data.get(0).get("url");
        if (url != null) {
            result.put("url", url.toString());
            return result;
        }
        Object b64 = data.get(0).get("b64_json");
        if (b64 != null) {
            result.put("base64", b64.toString());
            return result;
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
