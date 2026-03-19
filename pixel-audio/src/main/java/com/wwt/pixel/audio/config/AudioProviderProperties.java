package com.wwt.pixel.audio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "pixel.audio")
public class AudioProviderProperties {

    private String strategy = "round-robin";

    private boolean fallbackEnabled = true;

    private String baseUrl;

    private String apiKey;

    private int timeout = 180000;

    private List<ModelConfig> models = defaultModels();

    private List<CompatibleVendorConfig> vendors = new ArrayList<>();

    private static List<ModelConfig> defaultModels() {
        return new ArrayList<>(Arrays.asList(
                ModelConfig.of(
                        "chirp-v4",
                        "Chirp V4",
                        "适合灵感生成、自定义歌词、续写与上传二创",
                        Arrays.asList("music", "generate", "inspiration", "custom", "extend", "upload_extend")),
                ModelConfig.of(
                        "chirp-v4-tau",
                        "Chirp V4 Tau",
                        "适合歌手风格一致性生成",
                        Arrays.asList("music", "artist_consistency")),
                ModelConfig.of(
                        "chirp-v3-5-tau",
                        "Chirp V3.5 Tau",
                        "歌手风格兼容模型",
                        Arrays.asList("music", "artist_consistency")),
                ModelConfig.of(
                        "chirp-auk",
                        "Chirp Auk",
                        "适合曲声分离与 stem 处理",
                        Arrays.asList("music", "gen_stem"))
        ));
    }

    public List<CompatibleVendorConfig> getEffectiveVendors() {
        if (vendors != null && !vendors.isEmpty()) {
            return vendors;
        }
        List<CompatibleVendorConfig> derived = new ArrayList<>();
        if (models == null || models.isEmpty()) {
            return derived;
        }
        for (ModelConfig model : models) {
            CompatibleVendorConfig vendor = new CompatibleVendorConfig();
            vendor.setCode("audio-" + model.getModelId());
            vendor.setName(StringUtils.hasText(model.getDisplayName()) ? model.getDisplayName() : model.getModelId());
            vendor.setModelCode(model.getModelId());
            vendor.setModelId(model.getModelId());
            vendor.setProviderModel(model.getModelId());
            vendor.setModelDisplayName(model.getDisplayName());
            vendor.setDescription(model.getDescription());
            vendor.setEnabled(model.isAvailable());
            vendor.setApiKey(apiKey);
            vendor.setBaseUrl(baseUrl);
            vendor.setTimeout(timeout);
            vendor.setCostPerUnit(model.getCostPerUnit());
            vendor.setSupportedTasks(model.getSupportedTasks());
            derived.add(vendor);
        }
        return derived;
    }

    @Data
    public static class ModelConfig {
        private String modelId;
        private String displayName;
        private String description;
        private boolean available = true;
        private List<String> supportedTasks = new ArrayList<>();
        private BigDecimal costPerUnit = BigDecimal.ZERO;

        public static ModelConfig of(String modelId,
                                     String displayName,
                                     String description,
                                     List<String> supportedTasks) {
            ModelConfig config = new ModelConfig();
            config.setModelId(modelId);
            config.setDisplayName(displayName);
            config.setDescription(description);
            config.setSupportedTasks(new ArrayList<>(supportedTasks));
            return config;
        }
    }

    @Data
    public static class CompatibleVendorConfig {
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
        private List<String> supportedTasks = new ArrayList<>();
        private List<ModelParamConfig> params = new ArrayList<>();

        public String getModelCode() {
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

        public void setModelCode(String modelCode) {
            this.modelCode = modelCode;
            if (!StringUtils.hasText(this.modelId)) {
                this.modelId = modelCode;
            }
        }

        public String getModelId() {
            if (StringUtils.hasText(modelId)) {
                return modelId;
            }
            if (StringUtils.hasText(modelCode)) {
                return modelCode;
            }
            if (StringUtils.hasText(providerModel)) {
                return providerModel;
            }
            if (StringUtils.hasText(model)) {
                return model;
            }
            return null;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
            if (!StringUtils.hasText(this.modelCode)) {
                this.modelCode = modelId;
            }
        }

        public String getProviderModel() {
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

        public void setProviderModel(String providerModel) {
            this.providerModel = providerModel;
            if (!StringUtils.hasText(this.model)) {
                this.model = providerModel;
            }
        }

        public String getModel() {
            return getProviderModel();
        }

        public void setModel(String model) {
            this.model = model;
            if (!StringUtils.hasText(this.providerModel)) {
                this.providerModel = model;
            }
        }

        public List<String> getSupportedTasks() {
            if (supportedTasks != null && !supportedTasks.isEmpty()) {
                return supportedTasks;
            }
            Set<String> tasks = new LinkedHashSet<>();
            tasks.add("music");
            if (params != null) {
                for (ModelParamConfig param : params) {
                    if (param == null || !"task".equalsIgnoreCase(param.getParamKey())) {
                        continue;
                    }
                    if (StringUtils.hasText(param.getDefaultValue())) {
                        tasks.add(param.getDefaultValue().trim());
                    }
                    tasks.addAll(parseOptions(param.getOptions()));
                }
            }
            return new ArrayList<>(tasks);
        }

        private List<String> parseOptions(String options) {
            if (!StringUtils.hasText(options)) {
                return List.of();
            }
            String normalized = options.trim();
            if (normalized.startsWith("[")) {
                normalized = normalized.substring(1);
            }
            if (normalized.endsWith("]")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            if (!StringUtils.hasText(normalized)) {
                return List.of();
            }
            return Arrays.stream(normalized.split(","))
                    .map(item -> item.replace("\"", "").replace("'", "").trim())
                    .filter(StringUtils::hasText)
                    .toList();
        }
    }

    @Data
    public static class ModelParamConfig {
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
