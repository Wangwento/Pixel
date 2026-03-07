package com.wwt.pixel.image.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
        private String modelId;
        private String modelDisplayName;
        private int minVipLevel = 0;
        private boolean enabled = true;
        private boolean supportsImageInput = false;
        private String apiKey;
        private String baseUrl;
        private String model = "dall-e-3";
        private int weight = 1;
        private int timeout = 60000;
        private String aspectRatio;   // 1:1, 2:3, 3:2, 16:9 等
        private String imageSize;     // 1K, 2K, 4K (仅nano-banana-2支持)
    }
}
