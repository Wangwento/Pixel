package com.wwt.pixel.common;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 业务错误码 1000+
    USER_NOT_FOUND(1001, "用户不存在"),
    QUOTA_EXCEEDED(1002, "生成次数已用完"),
    AI_SERVICE_ERROR(1003, "AI服务调用失败"),
    INVALID_PROMPT(1004, "提示词不合法");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}