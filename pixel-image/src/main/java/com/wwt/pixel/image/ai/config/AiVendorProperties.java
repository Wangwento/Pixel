package com.wwt.pixel.image.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "pixel.ai")
public class AiVendorProperties {

    private String strategy = "round-robin";

    private boolean fallbackEnabled = true;

    private OpenAiConfig openai = new OpenAiConfig();

    private GeminiConfig gemini = new GeminiConfig();

    private HunyuanConfig hunyuan = new HunyuanConfig();

    private JingdongConfig jingdong = new JingdongConfig();

    private List<CompatibleVendorConfig> vendors = new ArrayList<>();

    @Data
    public static class OpenAiConfig {
        private boolean enabled = true;
        private int weight = 1;
        private int timeout = 60000;
    }

    @Data
    public static class GeminiConfig {
        private boolean enabled = false;
        private String apiKey;
        private String baseUrl = "https://generativelanguage.googleapis.com";
        private String model = "gemini-2.0-flash-exp-image-generation";
        private int weight = 2;
        private int timeout = 60000;
    }

    @Data
    public static class HunyuanConfig {
        private boolean enabled = false;
        private String secretId;
        private String secretKey;
        private String region = "ap-guangzhou";
        private int weight = 3;
        private int timeout = 60000;
    }

    @Data
    public static class JingdongConfig {
        private boolean enabled = false;
        private String apiKey;
        private String apiId;
        private String baseUrl = "https://model.jdcloud.com";
        private String model = "image-01";
        private int weight = 2;
        private int timeout = 120000;
        private int pollInterval = 2000;
        private int maxPollCount = 60;
    }

    @Data
    public static class CompatibleVendorConfig {
        private String code;
        private String name;
        /**
         * 平台内部模型编码，对应 pixel_admin.ai_model.model_code
         * 用于前端模型选择、权限控制、缓存及路由匹配。
         */
        private String modelCode;
        /**
         * 兼容旧配置字段：历史上使用 modelId 表示内部模型编码。
         */
        private String modelId;
        /**
         * 发给上游供应商的 model 参数。
         * 当前默认与 modelCode 相同，后续如需解耦可单独配置。
         */
        private String providerModel;
        /**
         * 兼容旧配置字段：历史上使用 model 表示上游请求参数。
         */
        private String model;
        private String modelDisplayName;
        private String category = "image";
        private int minVipLevel = 0;
        private boolean enabled = true;
        private boolean supportsImageInput = false;
        private String apiKey;
        private String baseUrl;
        private int weight = 1;
        private int timeout = 60000;
        private BigDecimal costPerUnit = BigDecimal.ZERO;
        private BigDecimal costPerImage = BigDecimal.ZERO;
        private String aspectRatio;
        private String imageSize;
        private List<ModelParamConfig> params;

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
            return "dall-e-3";
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

        public BigDecimal getCostPerUnit() {
            if (costPerUnit != null && costPerUnit.signum() > 0) {
                return costPerUnit;
            }
            if (costPerImage != null && costPerImage.signum() > 0) {
                return costPerImage;
            }
            return costPerUnit != null ? costPerUnit : BigDecimal.ZERO;
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
