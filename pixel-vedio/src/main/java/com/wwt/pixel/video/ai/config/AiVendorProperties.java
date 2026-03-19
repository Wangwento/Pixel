package com.wwt.pixel.video.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "pixel.video")
public class AiVendorProperties {

    private String strategy = "round-robin";

    private boolean fallbackEnabled = true;

    private List<CompatibleVendorConfig> vendors = new ArrayList<>();

    @Data
    public static class CompatibleVendorConfig {
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
        private List<String> supportedAspectRatios = new ArrayList<>();
        private List<String> supportedTextDurations = new ArrayList<>();
        private List<String> supportedImageDurations = new ArrayList<>();
        private Integer minImageCount;
        private Integer maxImageCount;
        private Boolean enhancePrompt = false;
        private Boolean enableUpsample = false;
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
            return StringUtils.hasText(modelId) ? modelId : modelCode;
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
            return "sora-2";
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

        public BigDecimal getCostPerSecond() {
            if (costPerSecond != null && costPerSecond.signum() > 0) {
                return costPerSecond;
            }
            if (costPerUnit != null && costPerUnit.signum() > 0) {
                return costPerUnit;
            }
            return costPerSecond != null ? costPerSecond : BigDecimal.ZERO;
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
