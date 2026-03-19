package com.wwt.pixel.video.util;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class TaskIdCodec {

    private TaskIdCodec() {
    }

    public static String encode(String vendorCode, String providerTaskId) {
        String raw = vendorCode + ":" + providerTaskId;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static DecodedTaskId decode(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(taskId), StandardCharsets.UTF_8);
            int separatorIndex = decoded.indexOf(':');
            if (separatorIndex <= 0 || separatorIndex == decoded.length() - 1) {
                return null;
            }
            String vendorCode = decoded.substring(0, separatorIndex);
            String providerTaskId = decoded.substring(separatorIndex + 1);
            if (!StringUtils.hasText(vendorCode) || !StringUtils.hasText(providerTaskId)) {
                return null;
            }
            return new DecodedTaskId(vendorCode, providerTaskId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record DecodedTaskId(String vendorCode, String providerTaskId) {
    }
}
