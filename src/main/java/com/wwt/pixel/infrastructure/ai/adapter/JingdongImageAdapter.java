package com.wwt.pixel.infrastructure.ai.adapter;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.domain.model.GenerationRequest;
import com.wwt.pixel.domain.model.GenerationResult;
import com.wwt.pixel.infrastructure.ai.ImageModelAdapter;
import com.wwt.pixel.infrastructure.ai.ImageVendor;
import com.wwt.pixel.infrastructure.ai.config.AiVendorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 京东云灵境适配器
 * 使用异步任务模式：submitTask -> queryTaskResult
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pixel.ai.jingdong.enabled", havingValue = "true")
public class JingdongImageAdapter implements ImageModelAdapter {

    private final AiVendorProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    private AiVendorProperties.JingdongConfig getConfig() {
        return properties.getJingdong();
    }

    @Override
    public ImageVendor getVendor() {
        return ImageVendor.JINGDONG;
    }

    @Override
    public boolean isAvailable() {
        return getConfig().isEnabled() && StringUtils.hasText(getConfig().getApiKey());
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            String finalPrompt = buildPrompt(request);
            log.debug("[京东云灵境] 生成图片, prompt: {}", finalPrompt);

            // 1. 提交任务
            String taskId = submitTask(finalPrompt, request);
            log.info("[京东云灵境] 任务已提交, taskId: {}", taskId);

            // 2. 轮询查询结果
            String imageUrl = pollTaskResult(taskId);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[京东云灵境] 图片生成成功, 耗时: {}ms, url: {}", elapsed, imageUrl);

            return GenerationResult.builder()
                    .imageUrl(imageUrl)
                    .revisedPrompt(finalPrompt)
                    .vendor(getVendor().getCode())
                    .model(getConfig().getModel())
                    .generationTimeMs(elapsed)
                    .fromCache(false)
                    .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[京东云灵境] 图片生成失败", e);
            throw new BusinessException(1003, "京东云灵境服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public int getWeight() {
        return getConfig().getWeight();
    }

    @Override
    public int getTimeoutMs() {
        return getConfig().getTimeout();
    }

    /**
     * 提交生成任务
     */
    private String submitTask(String prompt, GenerationRequest request) {
        String url = getConfig().getBaseUrl() + "/joycreator/openApi/submitTask";

        Map<String, Object> body = new HashMap<>();
        body.put("apiId", getConfig().getApiId());

        // params 参数 - 使用 Vidu 格式
        Map<String, Object> params = new HashMap<>();
        params.put("prompt", prompt);
        params.put("model", getConfig().getModel());
        params.put("taskNum", 1);

        // 宽高比映射
        String aspectRatio = mapAspectRatio(request.getSize());
        params.put("aspect_ratio", aspectRatio);

        // 分辨率 (仅 Vidu Q2 支持，Q1 不要传此参数)
        String model = getConfig().getModel();
        if ("viduq2".equalsIgnoreCase(model)) {
            params.put("resolution", "1080p");
        }

        // 参考图（如果有）
        if (StringUtils.hasText(request.getSourceImageUrl())) {
            params.put("images", List.of(request.getSourceImageUrl()));
        }

        body.put("params", params);

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        log.debug("[京东云灵境] 请求: {}", body);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        Map responseBody = response.getBody();

        log.debug("[京东云灵境] 响应: {}", responseBody);

        if (responseBody == null) {
            throw new BusinessException(1003, "京东云灵境返回为空");
        }

        // 响应可能嵌套在 result.result 中
        Map innerResult = responseBody;
        if (responseBody.containsKey("result")) {
            Object resultObj = responseBody.get("result");
            if (resultObj instanceof Map) {
                Map resultMap = (Map) resultObj;
                if (resultMap.containsKey("result")) {
                    Object innerObj = resultMap.get("result");
                    if (innerObj instanceof Map) {
                        innerResult = (Map) innerObj;
                    }
                } else {
                    innerResult = resultMap;
                }
            }
        }

        // 检查错误
        Boolean success = (Boolean) innerResult.get("success");
        Object errorObj = innerResult.get("error");
        String error = errorObj != null ? errorObj.toString() : null;

        if ((success != null && !success) || (StringUtils.hasText(error) && !"null".equals(error))) {
            throw new BusinessException(1003, "京东云灵境错误: " + error);
        }

        // 获取任务ID
        Object taskId = innerResult.get("genTaskId");
        if (taskId == null) {
            throw new BusinessException(1003, "京东云灵境未返回任务ID");
        }

        return taskId.toString();
    }

    /**
     * 尺寸转宽高比
     */
    private String mapAspectRatio(String size) {
        if (!StringUtils.hasText(size)) {
            return "1:1";
        }
        // 支持 1024x1024, 16:9 等格式
        if (size.contains(":")) {
            return size;
        }
        String[] parts = size.toLowerCase().split("x");
        if (parts.length != 2) {
            return "1:1";
        }
        try {
            int w = Integer.parseInt(parts[0]);
            int h = Integer.parseInt(parts[1]);
            // 简单映射
            double ratio = (double) w / h;
            if (Math.abs(ratio - 16.0/9.0) < 0.1) return "16:9";
            if (Math.abs(ratio - 9.0/16.0) < 0.1) return "9:16";
            if (Math.abs(ratio - 4.0/3.0) < 0.1) return "4:3";
            if (Math.abs(ratio - 3.0/4.0) < 0.1) return "3:4";
            return "1:1";
        } catch (NumberFormatException e) {
            return "1:1";
        }
    }

    /**
     * 轮询查询任务结果
     */
    private String pollTaskResult(String taskId) throws InterruptedException {
        String url = getConfig().getBaseUrl() + "/joycreator/openApi/queryTasKResult";

        Map<String, Object> body = new HashMap<>();
        body.put("genTaskId", taskId);

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        int maxPollCount = getConfig().getMaxPollCount();
        int pollInterval = getConfig().getPollInterval();

        for (int i = 0; i < maxPollCount; i++) {
            Thread.sleep(pollInterval);

            try {
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
                Map responseBody = response.getBody();

                log.debug("[京东云灵境] 查询响应: {}", responseBody);

                if (responseBody == null) {
                    continue;
                }

                // 响应嵌套在 result.result 中
                Map result = responseBody;
                if (responseBody.containsKey("result")) {
                    Object resultObj = responseBody.get("result");
                    if (resultObj instanceof Map) {
                        Map resultMap = (Map) resultObj;
                        if (resultMap.containsKey("result")) {
                            Object innerObj = resultMap.get("result");
                            if (innerObj instanceof Map) {
                                result = (Map) innerObj;
                            }
                        } else {
                            result = resultMap;
                        }
                    }
                }

                Object taskStatusObj = result.get("taskStatus");
                Integer taskStatus = null;
                if (taskStatusObj instanceof Integer) {
                    taskStatus = (Integer) taskStatusObj;
                } else if (taskStatusObj instanceof Number) {
                    taskStatus = ((Number) taskStatusObj).intValue();
                }

                if (taskStatus == null) {
                    log.debug("[京东云灵境] 任务状态未知, 继续轮询...");
                    continue;
                }

                log.info("[京东云灵境] 任务状态: {}, taskId: {}, 第{}次查询", taskStatus, taskId, i + 1);

                // taskStatus: 2=失败, 4=成功
                if (taskStatus == 2) {
                    Object errorObj = result.get("error");
                    String errorMsg = errorObj != null ? errorObj.toString() : null;
                    // 检查 taskResults 是否有错误信息
                    List taskResults = (List) result.get("taskResults");
                    if ((taskResults == null || taskResults.isEmpty()) && !StringUtils.hasText(errorMsg)) {
                        errorMsg = "任务执行失败，请检查账户余额或服务配额";
                    }
                    throw new BusinessException(1003, "京东云灵境生成失败: " + errorMsg);
                }

                if (taskStatus == 4) {
                    // 成功，提取图片URL
                    List<Map> taskResults = (List<Map>) result.get("taskResults");
                    if (taskResults != null && !taskResults.isEmpty()) {
                        Map firstResult = taskResults.get(0);
                        // 优先使用无水印URL
                        String imageUrl = (String) firstResult.get("url");
                        if (!StringUtils.hasText(imageUrl)) {
                            imageUrl = (String) firstResult.get("watermarkUrl");
                        }
                        if (StringUtils.hasText(imageUrl)) {
                            return imageUrl;
                        }
                    }
                    throw new BusinessException(1003, "京东云灵境未返回图片URL");
                }

                // 其他状态(如进行中)继续轮询
                log.debug("[京东云灵境] 任务进行中, taskId: {}, 状态: {}", taskId, taskStatus);

            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("[京东云灵境] 查询任务结果异常: {}", e.getMessage());
            }
        }

        throw new BusinessException(1003, "京东云灵境任务超时，请稍后重试");
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + getConfig().getApiKey());
        headers.set("x-jdcloud-request-id", UUID.randomUUID().toString());
        return headers;
    }

    private String buildPrompt(GenerationRequest request) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(request.getStyle())) {
            sb.append(request.getStyle()).append("风格，");
        }
        sb.append(request.getPrompt());
        return sb.toString();
    }
}
