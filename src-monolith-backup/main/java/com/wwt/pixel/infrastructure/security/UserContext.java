package com.wwt.pixel.infrastructure.security;

import com.wwt.pixel.common.exception.BusinessException;

/**
 * 用户上下文 - 存储当前请求的用户信息
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    /**
     * 设置当前用户
     */
    public static void setCurrentUser(Long userId, String username) {
        USER_ID.set(userId);
        USERNAME.set(username);
    }

    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        return USER_ID.get();
    }

    /**
     * 获取当前用户ID(必须登录)
     */
    public static Long requireCurrentUserId() {
        Long userId = USER_ID.get();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }

    /**
     * 获取当前用户名
     */
    public static String getCurrentUsername() {
        return USERNAME.get();
    }

    /**
     * 清除上下文
     */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
    }
}