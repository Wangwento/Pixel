package com.wwt.pixel.auth.controller;

import com.wwt.pixel.auth.domain.User;
import com.wwt.pixel.auth.service.AuthService;
import com.wwt.pixel.common.constant.CommonConstant;
import com.wwt.pixel.common.constant.VerifyCodeConstants;
import com.wwt.pixel.common.dto.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 检查用户名是否可用
     */
    @GetMapping("/username/check")
    public Result<Map<String, Object>> checkUsernameAvailable(
            @RequestParam("username")
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 20, message = "用户名长度3-20个字符")
            @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
            String username) {
        Map<String, Object> data = new HashMap<>();
        data.put("available", authService.isUsernameAvailable(username));
        return Result.success(data);
    }

    /**
     * 检查邮箱是否可用
     */
    @GetMapping("/email/check")
    public Result<Map<String, Object>> checkEmailAvailable(
            @RequestParam("email") @NotBlank(message = "邮箱不能为空") @Email(message = "请输入正确的邮箱地址") String email) {
        Map<String, Object> data = new HashMap<>();
        data.put("available", authService.isEmailAvailable(email));
        return Result.success(data);
    }

    /**
     * 发送邮箱验证码
     */
    @PostMapping("/email/send-code")
    public Result<Map<String, Object>> sendEmailCode(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody EmailCodeRequest request) {
        String scene = request.getScene();
        if (scene == null || scene.isBlank()) {
            scene = VerifyCodeConstants.SCENE_REGISTER;
        }
        authService.sendEmailCode(request.getEmail(), scene, userId);
        return buildSendCodeResult();
    }

    /**
     * 发送手机验证码
     */
    @PostMapping("/phone/send-code")
    public Result<Map<String, Object>> sendPhoneCode(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody PhoneCodeRequest request) {
        String scene = request.getScene();
        if (scene == null || scene.isBlank()) {
            scene = VerifyCodeConstants.SCENE_LOGIN;
        }
        authService.sendPhoneCode(request.getPhone(), scene, userId);
        return buildSendCodeResult();
    }

    /**
     * 兼容旧版手机验证码发送接口
     */
    @PostMapping("/send-code")
    public Result<Map<String, Object>> sendPhoneCodeCompat(@Valid @RequestBody PhoneCodeRequest request) {
        authService.sendPhoneCode(request.getPhone(), VerifyCodeConstants.SCENE_LOGIN, null);
        return buildSendCodeResult();
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getEmailCode(),
                request.getInviteCode()
        );
        String token = authService.generateToken(user);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", toUserVO(user));
        return Result.success("注册成功", data);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.getUsername(), request.getPassword());
        User user = authService.findByUsername(request.getUsername());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", toUserVO(user));
        return Result.success("登录成功", data);
    }

    /**
     * 手机验证码登录
     */
    @PostMapping("/login-phone")
    public Result<Map<String, Object>> loginByPhone(@Valid @RequestBody PhoneLoginRequest request) {
        String token = authService.loginByPhone(request.getPhone(), request.getCode());
        User user = authService.findByPhone(request.getPhone());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", toUserVO(user));
        return Result.success("登录成功", data);
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(CommonConstant.HEADER_USER_ID) Long userId) {
        authService.logout(userId);
        return Result.success();
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> getCurrentUser(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        User user = authService.findById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        Map<String, Object> userVO = toUserVO(user);
        log.info("返回用户信息: userId={}, monthlyQuota={}, monthlyQuotaUsed={}",
                userId, userVO.get("monthlyQuota"), userVO.get("monthlyQuotaUsed"));
        return Result.success(userVO);
    }

    private Map<String, Object> toUserVO(User user) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", user.getId());
        vo.put("username", user.getUsername());
        vo.put("nickname", user.getNickname());
        vo.put("avatar", user.getAvatar());
        vo.put("email", user.getEmail());
        vo.put("emailVerified", user.getEmailVerified());
        vo.put("phone", user.getPhone());
        vo.put("phoneVerified", user.getPhoneVerified());
        vo.put("realName", user.getRealName());
        vo.put("realNameVerified", user.getRealNameVerified());
        vo.put("points", user.getPoints());
        vo.put("freeQuota", user.getFreeQuota());
        vo.put("dailyLimit", user.getDailyLimit());
        vo.put("dailyUsed", user.getDailyUsed());
        vo.put("dailyRemaining", user.getDailyLimit() - user.getDailyUsed());
        vo.put("monthlyQuota", user.getMonthlyQuota());
        vo.put("monthlyQuotaUsed", user.getMonthlyQuotaUsed());
        vo.put("monthlyQuotaRemaining", user.getMonthlyQuota() - user.getMonthlyQuotaUsed());
        vo.put("userType", user.getUserType());
        vo.put("vipLevel", user.getVipLevel());
        vo.put("vipExpireTime", user.getVipExpireTime());
        vo.put("level", user.getLevel());
        vo.put("isVip", user.isVip());
        vo.put("inviteCode", user.getInviteCode());
        vo.put("profileCompleted", user.getProfileCompleted());

        log.debug("toUserVO: monthlyQuota={}, monthlyQuotaUsed={}, user.getMonthlyQuota()={}, user.getMonthlyQuotaUsed()={}",
                vo.get("monthlyQuota"), vo.get("monthlyQuotaUsed"), user.getMonthlyQuota(), user.getMonthlyQuotaUsed());

        return vo;
    }

    private Result<Map<String, Object>> buildSendCodeResult() {
        Map<String, Object> data = new HashMap<>();
        data.put("expireSeconds", VerifyCodeConstants.CODE_TTL.toSeconds());
        return Result.success("验证码发送成功", data);
    }

    // ========== 请求DTO ==========

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 20, message = "用户名长度3-20个字符")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度6-32个字符")
        private String password;

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "请输入正确的邮箱地址")
        private String email;

        @NotBlank(message = "邮箱验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "请输入6位邮箱验证码")
        private String emailCode;

        private String inviteCode;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class EmailCodeRequest {
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "请输入正确的邮箱地址")
        private String email;
        private String scene;

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }
    }

    @Data
    public static class PhoneCodeRequest {
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
        private String phone;
        private String scene;

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }
    }

    @Data
    public static class PhoneLoginRequest {
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
        private String phone;

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "请输入6位验证码")
        private String code;
    }
}
