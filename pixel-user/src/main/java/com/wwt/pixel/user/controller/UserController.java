package com.wwt.pixel.user.controller;

import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 用户签到
     */
    @PostMapping("/sign-in")
    public Result<Map<String, Object>> signIn(@RequestHeader("X-User-Id") Long userId) {
        int earnedPoints = userService.signIn(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("earnedPoints", earnedPoints);
        data.put("quotaInfo", userService.getQuotaInfo(userId));
        return Result.success(data);
    }

    /**
     * 获取额度信息
     */
    @GetMapping("/quota")
    public Result<UserService.UserQuotaInfo> getQuotaInfo(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(userService.getQuotaInfo(userId));
    }

    /**
     * 完善资料获得积分
     */
    @PostMapping("/profile/complete")
    public Result<Map<String, Object>> completeProfile(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ProfileRequest request) {
        int earnedPoints = userService.completeProfileReward(userId, request.getNickname(), request.getAvatar());

        Map<String, Object> data = new HashMap<>();
        data.put("earnedPoints", earnedPoints);
        data.put("quotaInfo", userService.getQuotaInfo(userId));
        return Result.success(data);
    }

    /**
     * 检查额度是否可用 (供Image服务内部调用)
     */
    @GetMapping("/internal/check-quota")
    public Result<Boolean> checkQuota(@RequestHeader("X-User-Id") Long userId) {
        boolean available = userService.checkQuotaAvailable(userId);
        return Result.success(available);
    }

    /**
     * 消耗额度 (供Image服务内部调用)
     */
    @PostMapping("/internal/consume-quota")
    public Result<String> consumeQuota(@RequestHeader("X-User-Id") Long userId) {
        String consumeType = userService.consumeQuota(userId);
        return Result.success(consumeType);
    }

    @Data
    public static class ProfileRequest {
        @NotBlank(message = "昵称不能为空")
        private String nickname;
        @NotBlank(message = "头像不能为空")
        private String avatar;
    }
}
