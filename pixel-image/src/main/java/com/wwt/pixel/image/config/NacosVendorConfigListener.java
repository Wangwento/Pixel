package com.wwt.pixel.image.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.image.ai.MultiVendorImageService;
import com.wwt.pixel.image.ai.config.AiVendorProperties;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@RequiredArgsConstructor
public class NacosVendorConfigListener {

    private static final String DATA_ID = "pixel-ai-vendors.json";
    private static final String GROUP = "DEFAULT_GROUP";

    private final MultiVendorImageService multiVendorImageService;
    private final ObjectMapper objectMapper;
    private final NacosConfigManager nacosConfigManager;

    @PostConstruct
    public void init() {
        try {
            // 复用 Spring Cloud 管理的 ConfigService
            ConfigService configService =
                    nacosConfigManager.getConfigService();

            // 启动时先拉取一次
            String config = configService.getConfig(
                    DATA_ID, GROUP, 5000);
            log.info("Nacos拉取配置 dataId={}, 内容: {}",
                    DATA_ID,
                    config == null ? "null"
                        : config.substring(0,
                            Math.min(config.length(), 200)));
            if (config != null && !config.isBlank()) {
                applyConfig(config);
            }

            // 监听后续变更
            configService.addListener(DATA_ID, GROUP,
                    new Listener() {
                @Override
                public Executor getExecutor() {
                    return Executors.newSingleThreadExecutor();
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("收到Nacos厂商配置变更");
                    applyConfig(configInfo);
                }
            });

            log.info("Nacos厂商配置监听已启动, dataId={}",
                    DATA_ID);
        } catch (Exception e) {
            log.warn("Nacos厂商配置监听启动失败，将使用本地配置: {}",
                    e.getMessage());
        }
    }

    private void applyConfig(String configJson) {
        try {
            List<VendorConfigDTO> vendors = objectMapper.readValue(
                    configJson, new TypeReference<>() {});
            multiVendorImageService.refreshVendors(
                    vendors.stream()
                        .map(this::toProperties).toList());
            log.info("动态厂商配置已刷新，共 {} 个厂商",
                    vendors.size());
        } catch (Exception e) {
            log.error("解析Nacos厂商配置失败", e);
        }
    }

    private AiVendorProperties.CompatibleVendorConfig toProperties(
            VendorConfigDTO dto) {
        var config =
                new AiVendorProperties.CompatibleVendorConfig();
        config.setCode(dto.getCode());
        config.setName(dto.getName());
        config.setModelCode(dto.resolveModelCode());
        config.setModelDisplayName(dto.getModelDisplayName());
        config.setMinVipLevel(dto.getMinVipLevel());
        config.setEnabled(dto.isEnabled());
        config.setApiKey(dto.getApiKey());
        config.setBaseUrl(dto.getBaseUrl());
        config.setProviderModel(dto.resolveProviderModel());
        config.setWeight(
                dto.getWeight() > 0 ? dto.getWeight() : 1);
        config.setTimeout(
                dto.getTimeout() > 0 ? dto.getTimeout() : 60000);
        config.setSupportsImageInput(dto.isSupportsImageInput());
        config.setCostPerUnit(resolveCostPerUnit(dto));
        config.setAspectRatio(dto.getAspectRatio());
        config.setImageSize(dto.getImageSize());
        return config;
    }

    private BigDecimal resolveCostPerUnit(VendorConfigDTO dto) {
        if (dto.getCostPerUnit() != null && dto.getCostPerUnit().signum() > 0) {
            return dto.getCostPerUnit();
        }
        if (dto.getCostPerImage() != null && dto.getCostPerImage().signum() > 0) {
            return dto.getCostPerImage();
        }
        return dto.getCostPerUnit() != null ? dto.getCostPerUnit() : BigDecimal.ZERO;
    }

    @Data
    public static class VendorConfigDTO {
        private String code;
        private String name;
        /**
         * 规范字段：平台内部模型编码，对应 ai_model.model_code。
         */
        private String modelCode;
        /**
         * 兼容旧字段：历史上使用 modelId 表示平台内部模型编码。
         */
        private String modelId;
        /**
         * 规范字段：发给上游供应商请求体中的 model 参数。
         */
        private String providerModel;
        /**
         * 兼容旧字段：历史上使用 model 表示上游请求参数。
         */
        private String model;
        private String modelDisplayName;
        private int minVipLevel = 0;
        private boolean enabled = true;
        private String apiKey;
        private String baseUrl;
        private int weight = 1;
        private int timeout = 60000;
        private BigDecimal costPerUnit = BigDecimal.ZERO;
        private BigDecimal costPerImage = BigDecimal.ZERO;
        private boolean supportsImageInput = false;
        private String aspectRatio;
        private String imageSize;

        public String resolveModelCode() {
            if (modelCode != null && !modelCode.isBlank()) {
                return modelCode;
            }
            if (modelId != null && !modelId.isBlank()) {
                return modelId;
            }
            if (providerModel != null && !providerModel.isBlank()) {
                return providerModel;
            }
            if (model != null && !model.isBlank()) {
                return model;
            }
            return null;
        }

        public String resolveProviderModel() {
            if (providerModel != null && !providerModel.isBlank()) {
                return providerModel;
            }
            if (model != null && !model.isBlank()) {
                return model;
            }
            if (modelCode != null && !modelCode.isBlank()) {
                return modelCode;
            }
            if (modelId != null && !modelId.isBlank()) {
                return modelId;
            }
            return "dall-e-3";
        }
    }
}
