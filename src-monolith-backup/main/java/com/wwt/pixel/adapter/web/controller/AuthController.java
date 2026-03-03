package com.wwt.pixel.adapter.web.controller;

import com.wwt.pixel.application.service.UserService;
import com.wwt.pixel.common.Result;
import com.wwt.pixel.domain.model.PointsRecord;
import com.wwt.pixel.domain.model.QuotaRecord;
import com.wwt.pixel.domain.model.User;
import com.wwt.pixel.infrastructure.security.JwtTokenProvider;
import com.wwt.pixel.infrastructure.security.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request.getUsername(), request.getPassword(), request.getEmail());

        String token = jwtTokenProvider.createToken(user.getId(), user.getUsername());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", toUserVO(user));

        return Result.success(data);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.findByUsername(request.getUsername());
        if (user == null) {
            return Result.error(401, "用户名或密码错误");
        }
        if (!userService.verifyPassword(user, request.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            return Result.error(403, "账号已被禁用");
        }

        String token = jwtTokenProvider.createToken(user.getId(), user.getUsername());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", toUserVO(user));

        return Result.success(data);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> getCurrentUser() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        User user = userService.findById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        return Result.success(toUserVO(user));
    }

    private Map<String, Object> toUserVO(User user) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", user.getId());
        vo.put("username", user.getUsername());
        vo.put("nickname", user.getNickname());
        vo.put("avatar", user.getAvatar());
        vo.put("email", user.getEmail());
        vo.put("points", user.getPoints());
        vo.put("freeQuota", user.getFreeQuota());                    // 免费额度
        vo.put("dailyRemaining", user.getAvailableDailyLimit());     // 今日剩余可生成
        vo.put("monthlyQuotaRemaining", user.getAvailableMonthlyQuota());
        vo.put("userType", user.getUserType());
        vo.put("vipLevel", user.getVipLevel());
        vo.put("vipExpireTime", user.getVipExpireTime());
        vo.put("level", user.getLevel());
        vo.put("isVip", user.isVip());
        vo.put("inviteCode", user.getInviteCode());
        vo.put("profileCompleted", user.getProfileCompleted());
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

        @Email(message = "邮箱格式不正确")
        private String email;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }
}