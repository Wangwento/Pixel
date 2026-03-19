package com.wwt.pixel.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.admin.domain.AiModel;
import com.wwt.pixel.admin.domain.AiModelParamDef;
import com.wwt.pixel.admin.domain.AiProvider;
import com.wwt.pixel.admin.mapper.AiModelMapper;
import com.wwt.pixel.admin.mapper.AiModelParamDefMapper;
import com.wwt.pixel.admin.mapper.AiProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigSyncService {

    private final AiProviderMapper providerMapper;
    private final AiModelMapper modelMapper;
    private final AiModelParamDefMapper paramDefMapper;
    private final ObjectMapper objectMapper;

    @Value("${spring.cloud.nacos.config.server-addr:localhost:8848}")
    private String nacosServerAddr;

    @Value("${spring.cloud.nacos.config.namespace:}")
    private String namespace;

    private static final String GROUP = "DEFAULT_GROUP";

    /**
     * 同步配置到Nacos（按category分文件）
     */
    public void syncToNacos() {
        try {
            List<AiProvider> providers = providerMapper.findAll();
            Map<String, List<Map<String, Object>>> configsByCategory = new HashMap<>();

            for (AiProvider provider : providers) {
                List<AiModel> models = modelMapper.findByProviderId(provider.getId());

                for (AiModel model : models) {
                    String modelCode = model.getModelCode();
                    Map<String, Object> config = new HashMap<>();
                    config.put("code", provider.getProviderCode() + "-" + modelCode);
                    config.put("name", provider.getProviderName() + " " + model.getModelName());
                    config.put("enabled", provider.getEnabled() && model.getEnabled());
                    config.put("baseUrl", provider.getBaseUrl());
                    config.put("apiKey", model.getApiKey());
                    // 规范字段：modelCode 表示平台内部模型编码；providerModel 表示发给上游供应商的 model 参数。
                    // 当前没有单独的“上游模型名”字段，因此两者都来自 ai_model.model_code。
                    config.put("modelCode", modelCode);
                    config.put("providerModel", modelCode);
                    // 兼容旧字段，待所有消费端迁移完成后再移除。
                    config.put("model", modelCode);
                    config.put("weight", provider.getWeight());
                    config.put("timeout", model.getTimeoutMs());
                    config.put("modelId", modelCode);
                    config.put("modelDisplayName", model.getModelName());
                    config.put("minVipLevel", model.getMinVipLevel());
                    config.put("category", model.getCategory());
                    config.put("supportsImageInput", model.getSupportsImageInput());
                    config.put("costPerUnit", model.getCostPerUnit());
                    if ("video".equalsIgnoreCase(model.getCategory())) {
                        config.put("costPerSecond", model.getCostPerUnit());
                    } else if ("image".equalsIgnoreCase(model.getCategory())) {
                        config.put("costPerImage", model.getCostPerUnit());
                    }

                    // 查询并添加模型参数
                    List<AiModelParamDef> params = paramDefMapper.findByModelId(model.getId());
                    log.info("模型 {} (id={}) 查询到 {} 个参数", model.getModelCode(), model.getId(),
                            params != null ? params.size() : 0);
                    if (params != null && !params.isEmpty()) {
                        List<Map<String, Object>> paramList = new ArrayList<>();
                        for (AiModelParamDef param : params) {
                            Map<String, Object> paramMap = new HashMap<>();
                            paramMap.put("paramKey", param.getParamKey());
                            paramMap.put("paramName", param.getParamName());
                            paramMap.put("paramType", param.getParamType());
                            paramMap.put("required", param.getRequired());
                            paramMap.put("visible", param.getVisible());
                            paramMap.put("defaultValue", param.getDefaultValue());
                            paramMap.put("options", param.getOptions());
                            paramMap.put("validationRule", param.getValidationRule());
                            paramMap.put("description", param.getDescription());
                            paramMap.put("displayOrder", param.getDisplayOrder());
                            paramList.add(paramMap);
                        }
                        config.put("params", paramList);
                        log.info("已添加 {} 个参数到配置", paramList.size());
                    }

                    String category = model.getCategory();
                    configsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(config);
                }
            }

            int totalCount = 0;
            for (Map.Entry<String, List<Map<String, Object>>> entry : configsByCategory.entrySet()) {
                String category = entry.getKey();
                List<Map<String, Object>> configs = entry.getValue();
                String dataId = "ai-" + category + "-config.json";
                String content = objectMapper.writeValueAsString(configs);
                publishToNacos(dataId, content);
                totalCount += configs.size();
                log.info("已同步 {} 类型配置到 {}，共 {} 个模型", category, dataId, configs.size());
            }
            log.info("配置同步完成，共 {} 个模型配置", totalCount);
        } catch (Exception e) {
            log.error("同步配置到Nacos失败", e);
            throw new RuntimeException("同步配置失败: " + e.getMessage());
        }
    }

    private void publishToNacos(String dataId, String content) throws Exception {
        String url = String.format("http://%s/nacos/v1/cs/configs", nacosServerAddr);

        Map<String, String> params = new HashMap<>();
        params.put("dataId", dataId);
        params.put("group", GROUP);
        params.put("content", content);
        if (namespace != null && !namespace.isEmpty()) {
            params.put("tenant", namespace);
        }

        String body = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + java.net.URLEncoder.encode(e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 || !"true".equals(response.body())) {
            throw new RuntimeException("Nacos发布失败: " + response.body());
        }
    }
}
