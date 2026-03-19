package com.wwt.pixel.video.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.video.ai.MultiVendorVideoService;
import com.wwt.pixel.video.ai.config.AiVendorProperties;
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
    private static final String LEGACY_DATA_ID = "pixel-vedio-vendors.json";

    private final MultiVendorVideoService multiVendorVideoService;
    private final ObjectMapper objectMapper;
    private final NacosConfigManager nacosConfigManager;
    private final Executor listenerExecutor = Executors.newSingleThreadExecutor();

    @Value("${service.category:video}")
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

            log.info("视频厂商 Nacos 配置监听已启动, primaryDataId={}, legacyDataId={}",
                    getPrimaryDataId(), LEGACY_DATA_ID);
        } catch (Exception e) {
            log.warn("视频厂商 Nacos 配置监听启动失败，将使用本地配置: {}", e.getMessage());
        }
    }

    private boolean tryLoadConfig(ConfigService configService, String dataId) throws Exception {
        String config = configService.getConfig(dataId, GROUP, 5000);
        log.info("Nacos拉取视频厂商配置 dataId={}, 内容预览={}",
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
                log.info("收到视频厂商 Nacos 配置变更: {}", dataId);
                applyConfig(configInfo);
            }
        });
    }

    private void applyConfig(String configJson) {
        try {
            List<VendorConfigDTO> vendors = objectMapper.readValue(configJson, new TypeReference<>() {
            });
            multiVendorVideoService.refreshVendors(vendors.stream().map(this::toProperties).toList());
            log.info("视频厂商动态配置已刷新，共 {} 个厂商", vendors.size());
        } catch (Exception e) {
            log.error("解析视频厂商 Nacos 配置失败", e);
        }
    }

    private AiVendorProperties.CompatibleVendorConfig toProperties(VendorConfigDTO dto) {
        AiVendorProperties.CompatibleVendorConfig config = new AiVendorProperties.CompatibleVendorConfig();
        config.setCode(dto.getCode());
        config.setName(dto.getName());
        config.setModelCode(dto.resolveModelCode());
        config.setProviderModel(dto.resolveProviderModel());
        config.setModelDisplayName(dto.getModelDisplayName());
        config.setMinVipLevel(dto.getMinVipLevel());
        config.setEnabled(dto.isEnabled());
        config.setSupportsImageInput(dto.getSupportsImageInput());
        config.setApiKey(dto.getApiKey());
        config.setBaseUrl(dto.getBaseUrl());
        config.setWeight(dto.getWeight() > 0 ? dto.getWeight() : 1);
        config.setTimeout(dto.getTimeout() > 0 ? dto.getTimeout() : 600000);
        config.setAspectRatio(dto.getAspectRatio());
        config.setDuration(dto.getDuration());
        config.setCostPerSecond(resolveCostPerSecond(dto));
        config.setHd(dto.getHd());
        config.setWatermark(dto.getWatermark());
        config.setPrivateMode(dto.getPrivateMode());
        config.setNotifyHook(dto.getNotifyHook());
        config.setSupportsTextToVideo(dto.getSupportsTextToVideo());
        config.setSupportsHd(dto.getSupportsHd());
        config.setSupportsEnhancePrompt(dto.getSupportsEnhancePrompt());
        config.setSupportsUpsample(dto.getSupportsUpsample());
        config.setSupportedAspectRatios(dto.getSupportedAspectRatios());
        config.setSupportedTextDurations(dto.getSupportedTextDurations());
        config.setSupportedImageDurations(dto.getSupportedImageDurations());
        config.setMinImageCount(dto.getMinImageCount());
        config.setMaxImageCount(dto.getMaxImageCount());
        config.setEnhancePrompt(dto.getEnhancePrompt());
        config.setEnableUpsample(dto.getEnableUpsample());
        if (dto.getParams() != null) {
            List<AiVendorProperties.ModelParamConfig> paramConfigs = new ArrayList<>();
            for (ModelParamDTO param : dto.getParams()) {
                AiVendorProperties.ModelParamConfig paramConfig = new AiVendorProperties.ModelParamConfig();
                paramConfig.setParamKey(param.getParamKey());
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

    private BigDecimal resolveCostPerSecond(VendorConfigDTO dto) {
        if (dto.getCostPerSecond() != null && dto.getCostPerSecond().signum() > 0) {
            return dto.getCostPerSecond();
        }
        if (dto.getCostPerUnit() != null && dto.getCostPerUnit().signum() > 0) {
            return dto.getCostPerUnit();
        }
        return dto.getCostPerSecond() != null ? dto.getCostPerSecond() : BigDecimal.ZERO;
    }

    @Data
    public static class VendorConfigDTO {
        private String code;
        private String name;
        private String modelCode;
        private String modelId;
        private String providerModel;
        private String modelDisplayName;
        private int minVipLevel = 0;
        private boolean enabled = true;
        private Boolean supportsImageInput;
        private String apiKey;
        private String baseUrl;
        private String model = "sora-2";
        private int weight = 1;
        private int timeout = 600000;
        private BigDecimal costPerUnit = BigDecimal.ZERO;
        private String aspectRatio = "16:9";
        private String duration;
        private BigDecimal costPerSecond = BigDecimal.ZERO;
        private Boolean hd = false;
        private Boolean watermark = false;
        private Boolean privateMode = false;
        private String notifyHook;
        private Boolean supportsTextToVideo;
        private Boolean supportsHd;
        private Boolean supportsEnhancePrompt;
        private Boolean supportsUpsample;
        private List<String> supportedAspectRatios;
        private List<String> supportedTextDurations;
        private List<String> supportedImageDurations;
        private Integer minImageCount;
        private Integer maxImageCount;
        private Boolean enhancePrompt = false;
        private Boolean enableUpsample = false;
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
            return "sora-2";
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
