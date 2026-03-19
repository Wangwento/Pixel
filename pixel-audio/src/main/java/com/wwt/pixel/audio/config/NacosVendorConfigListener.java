package com.wwt.pixel.audio.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.audio.service.AudioService;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NacosVendorConfigListener {

    private static final String GROUP = "DEFAULT_GROUP";
    private static final String LEGACY_DATA_ID = "pixel-audio-vendors.json";

    private final AudioService audioService;
    private final ObjectMapper objectMapper;
    private final NacosConfigManager nacosConfigManager;
    private final Executor listenerExecutor = Executors.newSingleThreadExecutor();

    @Value("${service.category:audio}")
    private String serviceCategory;

    private String getPrimaryDataId() {
        return "ai-" + serviceCategory + "-config.json";
    }

    @PostConstruct
    public void init() {
        try {
            ConfigService configService = nacosConfigManager.getConfigService();
            boolean loaded = tryLoadConfig(configService, getPrimaryDataId());
            if (!loaded) {
                tryLoadConfig(configService, LEGACY_DATA_ID);
            }

            registerListener(configService, getPrimaryDataId());
            registerListener(configService, LEGACY_DATA_ID);

            log.info("音频厂商 Nacos 配置监听已启动, primaryDataId={}, legacyDataId={}",
                    getPrimaryDataId(), LEGACY_DATA_ID);
        } catch (Exception exception) {
            log.warn("音频厂商 Nacos 配置监听启动失败，将使用本地配置: {}", exception.getMessage());
        }
    }

    private boolean tryLoadConfig(ConfigService configService, String dataId) throws Exception {
        String config = configService.getConfig(dataId, GROUP, 5000);
        log.info("Nacos拉取音频厂商配置 dataId={}, 内容预览={}",
                dataId,
                config == null ? "null" : config.substring(0, Math.min(config.length(), 200)));
        if (StringUtils.hasText(config)) {
            applyConfig(config);
            return true;
        }
        return false;
    }

    private void registerListener(ConfigService configService, String dataId) throws Exception {
        configService.addListener(dataId, GROUP, new Listener() {
            @Override
            public Executor getExecutor() {
                return listenerExecutor;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                if (!StringUtils.hasText(configInfo)) {
                    return;
                }
                log.info("收到音频厂商 Nacos 配置变更: {}", dataId);
                applyConfig(configInfo);
            }
        });
    }

    private void applyConfig(String configJson) {
        try {
            List<VendorConfigDTO> vendors = objectMapper.readValue(configJson, new TypeReference<>() {
            });
            audioService.refreshVendors(vendors.stream().map(this::toProperties).toList());
            log.info("音频厂商动态配置已刷新，共 {} 个模型", vendors.size());
        } catch (Exception exception) {
            log.error("解析音频厂商 Nacos 配置失败", exception);
        }
    }

    private AudioProviderProperties.CompatibleVendorConfig toProperties(VendorConfigDTO dto) {
        AudioProviderProperties.CompatibleVendorConfig config = new AudioProviderProperties.CompatibleVendorConfig();
        config.setCode(dto.getCode());
        config.setName(dto.getName());
        config.setModelCode(dto.resolveModelCode());
        config.setProviderModel(dto.resolveProviderModel());
        config.setModelDisplayName(dto.getModelDisplayName());
        config.setDescription(dto.getDescription());
        config.setMinVipLevel(dto.getMinVipLevel());
        config.setEnabled(dto.isEnabled());
        config.setApiKey(dto.getApiKey());
        config.setBaseUrl(dto.getBaseUrl());
        config.setWeight(dto.getWeight() > 0 ? dto.getWeight() : 1);
        config.setTimeout(dto.getTimeout() > 0 ? dto.getTimeout() : 180000);
        config.setCostPerUnit(dto.getCostPerUnit() != null ? dto.getCostPerUnit() : BigDecimal.ZERO);
        if (dto.getSupportedTasks() != null) {
            config.setSupportedTasks(dto.getSupportedTasks());
        }
        if (dto.getParams() != null) {
            List<AudioProviderProperties.ModelParamConfig> paramConfigs = new ArrayList<>();
            for (ModelParamDTO param : dto.getParams()) {
                AudioProviderProperties.ModelParamConfig paramConfig = new AudioProviderProperties.ModelParamConfig();
                paramConfig.setParamKey(param.getParamKey() == null ? null : param.getParamKey().trim());
                paramConfig.setParamName(param.getParamName());
                paramConfig.setParamType(param.getParamType());
                paramConfig.setRequired(param.getRequired());
                paramConfig.setVisible(param.getVisible());
                paramConfig.setDefaultValue(param.getDefaultValue());
                paramConfig.setOptions(param.getOptions());
                paramConfig.setValidationRule(param.getValidationRule());
                paramConfig.setDescription(param.getDescription());
                paramConfig.setDisplayOrder(param.getDisplayOrder());
                paramConfigs.add(paramConfig);
            }
            config.setParams(paramConfigs);
        }
        return config;
    }

    @Data
    public static class VendorConfigDTO {
        private String code;
        private String name;
        private String modelCode;
        private String modelId;
        private String providerModel;
        private String model;
        private String modelDisplayName;
        private String description;
        private int minVipLevel = 0;
        private boolean enabled = true;
        private String apiKey;
        private String baseUrl;
        private int weight = 1;
        private int timeout = 180000;
        private BigDecimal costPerUnit = BigDecimal.ZERO;
        private List<String> supportedTasks;
        private List<ModelParamDTO> params;

        public String resolveModelCode() {
            if (StringUtils.hasText(modelCode)) {
                return modelCode;
            }
            if (StringUtils.hasText(modelId)) {
                return modelId;
            }
            if (StringUtils.hasText(providerModel)) {
                return providerModel;
            }
            if (StringUtils.hasText(model)) {
                return model;
            }
            return null;
        }

        public String resolveProviderModel() {
            if (StringUtils.hasText(providerModel)) {
                return providerModel;
            }
            if (StringUtils.hasText(model)) {
                return model;
            }
            if (StringUtils.hasText(modelCode)) {
                return modelCode;
            }
            if (StringUtils.hasText(modelId)) {
                return modelId;
            }
            return null;
        }
    }

    @Data
    public static class ModelParamDTO {
        private String paramKey;
        private String paramName;
        private String paramType;
        private Boolean required;
        private Boolean visible;
        private String defaultValue;
        private String options;
        private String validationRule;
        private String description;
        private Integer displayOrder;
    }
}
