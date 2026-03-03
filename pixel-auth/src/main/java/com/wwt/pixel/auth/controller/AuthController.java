package com.wwt.pixel.auth.controller;

import com.wwt.pixel.auth.domain.User;
import com.wwt.pixel.auth.service.AuthService;
import com.wwt.pixel.common.dto.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getInviteCode()
        );

        Map<String, Object> data = new HashMap<>();
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

    // ========== 请求DTO ==========

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 20, message = "用户名长度3-20个字符")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度6-32个字符")
        private String password;

        private String email;
        private String inviteCode;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }
}