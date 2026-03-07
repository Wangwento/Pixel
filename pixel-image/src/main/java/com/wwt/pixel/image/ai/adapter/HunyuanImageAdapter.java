package com.wwt.pixel.image.ai.adapter;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.image.domain.GenerationRequest;
import com.wwt.pixel.image.domain.GenerationResult;
import com.wwt.pixel.image.ai.ImageModelAdapter;
import com.wwt.pixel.image.ai.ImageVendor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Component
@ConditionalOnProperty(name = "pixel.ai.hunyuan.enabled", havingValue = "true")
public class HunyuanImageAdapter implements ImageModelAdapter {

    @Value("${pixel.ai.hunyuan.secret-id:}")
    private String secretId;

    @Value("${pixel.ai.hunyuan.secret-key:}")
    private String secretKey;

    @Value("${pixel.ai.hunyuan.region:ap-guangzhou}")
    private String region;

    @Value("${pixel.ai.hunyuan.weight:3}")
    private int weight;

    @Value("${pixel.ai.hunyuan.timeout:60000}")
    private int timeoutMs;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String SERVICE = "aiart";
    private static final String HOST = "aiart.tencentcloudapi.com";
    private static final String VERSION = "2022-12-29";
    private static final String ACTION = "TextToImage";

    @Override
    public ImageVendor getVendor() {
        return ImageVendor.HUNYUAN;
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.hasText(secretId) && StringUtils.hasText(secretKey);
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            String finalPrompt = buildPrompt(request);
            log.debug("[Hunyuan] 生成图片, prompt: {}", finalPrompt);

            Map<String, Object> requestBody = buildRequestBody(finalPrompt, request);
            String jsonBody = toJson(requestBody);
            log.debug("[Hunyuan] 请求体: {}", jsonBody);

            HttpHeaders headers = buildHeaders(jsonBody);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            String url = "https://" + HOST;
            // 先获取原始字符串响应，便于调试
            ResponseEntity<String> rawResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            String responseBody = rawResponse.getBody();
            log.debug("[Hunyuan] 原始响应: {}", responseBody);

            // 解析JSON
            Map<String, Object> response = parseJson(responseBody);

            String imageBase64 = extractImageFromResponse(response);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[Hunyuan] 图片生成成功, 耗时: {}ms", elapsed);

            return GenerationResult.builder()
                    .imageBase64(imageBase64)
                    .revisedPrompt(finalPrompt)
                    .vendor(getVendor().getCode())
                    .model("hunyuan-image")
                    .generationTimeMs(elapsed)
                    .fromCache(false)
                    .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Hunyuan] 图片生成失败", e);
            throw new BusinessException(1003, "混元服务调用失败: " + e.getMessage());
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

    private Map<String, Object> buildRequestBody(String prompt, GenerationRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("Prompt", prompt);
        // aiart 的 Styles 是数组
        body.put("Styles", new String[]{"201"});
        // ResultConfig 配置输出
        Map<String, Object> resultConfig = new HashMap<>();
        resultConfig.put("Resolution", mapResolution(request.getSize()));
        body.put("ResultConfig", resultConfig);
        if (StringUtils.hasText(request.getNegativePrompt())) {
            body.put("NegativePrompt", request.getNegativePrompt());
        }
        return body;
    }

    private String mapResolution(String size) {
        if (!StringUtils.hasText(size)) return "1024:1024";
        return size.replace("x", ":");
    }

    private HttpHeaders buildHeaders(String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Host", HOST);
        headers.set("X-TC-Action", ACTION);
        headers.set("X-TC-Version", VERSION);
        headers.set("X-TC-Region", region);

        long timestamp = System.currentTimeMillis() / 1000;
        headers.set("X-TC-Timestamp", String.valueOf(timestamp));

        try {
            String authorization = sign(jsonBody, timestamp);
            headers.set("Authorization", authorization);
        } catch (Exception e) {
            throw new BusinessException(1003, "签名失败: " + e.getMessage());
        }

        return headers;
    }

    private String sign(String body, long timestamp) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = sdf.format(new Date(timestamp * 1000));

        String httpRequestMethod = "POST";
        String canonicalUri = "/";
        String canonicalQueryString = "";
        String canonicalHeaders = "content-type:application/json\nhost:" + HOST + "\n";
        String signedHeaders = "content-type;host";

        String hashedRequestPayload = sha256Hex(body);
        String canonicalRequest = httpRequestMethod + "\n" + canonicalUri + "\n" + canonicalQueryString + "\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + hashedRequestPayload;

        String algorithm = "TC3-HMAC-SHA256";
        String credentialScope = date + "/" + SERVICE + "/" + "tc3_request";
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);
        String stringToSign = algorithm + "\n" + timestamp + "\n" + credentialScope + "\n" + hashedCanonicalRequest;

        byte[] secretDate = hmac256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmac256(secretDate, SERVICE);
        byte[] secretSigning = hmac256(secretService, "tc3_request");
        String signature = bytesToHex(hmac256(secretSigning, stringToSign));

        return algorithm + " " + "Credential=" + secretId + "/" + credentialScope + ", "
                + "SignedHeaders=" + signedHeaders + ", " + "Signature=" + signature;
    }

    private static byte[] hmac256(byte[] key, String msg) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(d);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractImageFromResponse(Map response) {
        if (response == null) {
            throw new BusinessException(1003, "混元返回为空");
        }
        Map<String, Object> responseData = (Map<String, Object>) response.get("Response");
        if (responseData.containsKey("Error")) {
            Map<String, Object> error = (Map<String, Object>) responseData.get("Error");
            throw new BusinessException(1003, "混元错误: " + error.get("Message"));
        }
        List<Map<String, Object>> resultImages = (List<Map<String, Object>>) responseData.get("ResultImage");
        if (resultImages == null || resultImages.isEmpty()) {
            throw new BusinessException(1003, "混元未返回图片");
        }
        return (String) resultImages.get(0).get("Image");
    }

    private String buildPrompt(GenerationRequest request) {
        return request.getPrompt();
    }

    private String toJson(Map<String, Object> map) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new BusinessException(1003, "JSON序列化失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        // 简单的JSON解析，用于调试
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("[Hunyuan] JSON解析失败, 原始内容: {}", json);
            throw new BusinessException(1003, "混元响应解析失败: " + json);
        }
    }
}
