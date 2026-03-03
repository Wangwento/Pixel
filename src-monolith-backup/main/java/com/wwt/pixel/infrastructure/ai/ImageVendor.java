package com.wwt.pixel.infrastructure.ai;

import lombok.Getter;

@Getter
public enum ImageVendor {

    OPENAI("openai", "OpenAI DALL-E", true),
    GEMINI("gemini", "Google Gemini", true),
    HUNYUAN("hunyuan", "腾讯混元", true),
    JINGDONG("jingdong", "京东云灵境", true),
    NANO_BANANA("nano-banana", "Nano Banana", true),
    ZHIPU("zhipu", "智谱AI", true);

    private final String code;
    private final String name;
    private final boolean enabled;

    ImageVendor(String code, String name, boolean enabled) {
        this.code = code;
        this.name = name;
        this.enabled = enabled;
    }

    public static ImageVendor fromCode(String code) {
        for (ImageVendor vendor : values()) {
            if (vendor.getCode().equalsIgnoreCase(code)) {
                return vendor;
            }
        }
        return null;
    }
}