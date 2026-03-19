package com.wwt.pixel.common.constant;

import java.time.Duration;
import java.util.Locale;

/**
 * 验证码常量
 */
public final class VerifyCodeConstants {

    public static final Duration CODE_TTL = Duration.ofMinutes(5);
    public static final long CODE_TTL_MINUTES = 5L;
    public static final int CODE_LENGTH = 6;

    public static final String SCENE_REGISTER = "register";
    public static final String SCENE_BIND_EMAIL = "bind_email";
    public static final String SCENE_BIND_PHONE = "bind_phone";
    public static final String SCENE_LOGIN = "login";

    private VerifyCodeConstants() {
    }

    public static String normalizeScene(String scene) {
        return scene == null ? "" : scene.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isSupportedEmailScene(String scene) {
        String normalizedScene = normalizeScene(scene);
        return SCENE_REGISTER.equals(normalizedScene) || SCENE_BIND_EMAIL.equals(normalizedScene);
    }

    public static boolean isSupportedPhoneScene(String scene) {
        String normalizedScene = normalizeScene(scene);
        return SCENE_BIND_PHONE.equals(normalizedScene) || SCENE_LOGIN.equals(normalizedScene);
    }

    public static String buildEmailKey(String scene, String email) {
        return "pixel:verify:email:" + normalizeScene(scene) + ":" + normalizeEmail(email);
    }

    public static String buildPhoneKey(String scene, String phone) {
        return "pixel:verify:phone:" + normalizeScene(scene) + ":" + normalizePhone(phone);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String phone) {
        return phone == null ? "" : phone.trim();
    }
}
