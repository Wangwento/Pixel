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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Component
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
        config.setModelId(dto.getModelId());
        config.setModelDisplayName(dto.getModelDisplayName());
        config.setMinVipLevel(dto.getMinVipLevel());
        config.setEnabled(dto.isEnabled());
        config.setApiKey(dto.getApiKey());
        config.setBaseUrl(dto.getBaseUrl());
        config.setModel(dto.getModel() != null
                ? dto.getModel() : "dall-e-3");
        config.setWeight(
                dto.getWeight() > 0 ? dto.getWeight() : 1);
        config.setTimeout(
                dto.getTimeout() > 0 ? dto.getTimeout() : 60000);
        config.setSupportsImageInput(dto.isSupportsImageInput());
        config.setAspectRatio(dto.getAspectRatio());
        config.setImageSize(dto.getImageSize());
        return config;
    }

    @Data
    public static class VendorConfigDTO {
        private String code;
        private String name;
        private String modelId;
        private String modelDisplayName;
        private int minVipLevel = 0;
        private boolean enabled = true;
        private String apiKey;
        private String baseUrl;
        private String model;
        private int weight = 1;
        private int timeout = 60000;
        private boolean supportsImageInput = false;
        private String aspectRatio;
        private String imageSize;
    }
}
