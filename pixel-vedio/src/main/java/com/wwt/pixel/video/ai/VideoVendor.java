package com.wwt.pixel.video.ai;

import lombok.Getter;

@Getter
public enum VideoVendor {

    PLATO("plato", "柏拉图AI", true);

    private final String code;
    private final String name;
    private final boolean enabled;

    VideoVendor(String code, String name, boolean enabled) {
        this.code = code;
        this.name = name;
        this.enabled = enabled;
    }

    public static VideoVendor fromCode(String code) {
        for (VideoVendor vendor : values()) {
            if (vendor.getCode().equalsIgnoreCase(code)) {
                return vendor;
            }
        }
        return null;
    }
}
