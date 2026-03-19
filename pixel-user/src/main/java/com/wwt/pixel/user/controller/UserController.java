package com.wwt.pixel.user.controller;

import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.user.domain.User;
import com.wwt.pixel.user.domain.UserBasicInfo;
import com.wwt.pixel.user.service.UserGrowthService;
import com.wwt.pixel.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserGrowthService userGrowthService;

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
        UserGrowthService.GrowthTaskDTO task = userGrowthService.completeProfileTask(
                userId, request.getNickname(), request.getAvatar());

        Map<String, Object> data = new HashMap<>();
        data.put("task", task);
        data.put("message", "资料已完善，请在通知中领取奖励");
        data.put("quotaInfo", userService.getQuotaInfo(userId));
        return Result.success(data);
    }

    /**
     * 绑定邮箱并生成待领取奖励
     */
    @PostMapping("/profile/bind-email")
    public Result<Map<String, Object>> bindEmail(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody EmailBindRequest request) {
        UserGrowthService.GrowthTaskDTO task = userGrowthService.bindEmailTask(
                userId,
                request.getEmail(),
                request.getCode()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("task", task);
        data.put("message", "邮箱已绑定，请在通知中领取奖励");
        data.put("quotaInfo", userService.getQuotaInfo(userId));
        return Result.success(data);
    }

    /**
     * 绑定手机号并生成待领取奖励
     */
    @PostMapping("/profile/bind-phone")
    public Result<Map<String, Object>> bindPhone(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PhoneBindRequest request) {
        UserGrowthService.GrowthTaskDTO task = userGrowthService.bindPhoneTask(
                userId,
                request.getPhone(),
                request.getCode()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("task", task);
        data.put("message", "手机号已绑定，请在通知中领取奖励");
        data.put("quotaInfo", userService.getQuotaInfo(userId));
        return Result.success(data);
    }

    /**
     * 实名认证并生成待领取奖励
     */
    @PostMapping("/profile/verify-real-name")
    public Result<Map<String, Object>> verifyRealName(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody RealNameVerifyRequest request) {
        UserGrowthService.GrowthTaskDTO task = userGrowthService.verifyRealNameTask(
                userId, request.getRealName(), request.getIdCard());

        Map<String, Object> data = new HashMap<>();
        data.put("task", task);
        data.put("message", "实名认证已完成，请在通知中领取奖励");
        data.put("quotaInfo", userService.getQuotaInfo(userId));
        return Result.success(data);
    }

    /**
     * 获取增长任务/通知
     */
    @GetMapping("/growth/tasks")
    public Result<Map<String, Object>> getGrowthTasks(@RequestHeader("X-User-Id") Long userId) {
        UserGrowthService.TaskListResult result = userGrowthService.listTasks(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("pendingCount", result.getPendingCount());
        data.put("tasks", result.getTasks());
        return Result.success(data);
    }

    /**
     * 领取增长奖励
     */
    @PostMapping("/growth/claim/{recordId}")
    public Result<Map<String, Object>> claimGrowthReward(@RequestHeader("X-User-Id") Long userId,
                                                         @PathVariable Long recordId) {
        UserGrowthService.ClaimResult result = userGrowthService.claimReward(userId, recordId);
        Map<String, Object> data = new HashMap<>();
        data.put("pointsAdded", result.getPointsAdded());
        data.put("quotaAdded", result.getQuotaAdded());
        data.put("task", result.getTask());
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

    /**
     * 内部加积分 (供Image服务调用, 如热门图片奖励)
     */
    @PostMapping("/internal/add-points")
    public Result<Void> addPointsInternal(@RequestHeader("X-User-Id") Long userId,
                                          @RequestBody Map<String, Object> body) {
        int points = body.get("points") != null ? ((Number) body.get("points")).intValue() : 0;
        String source = body.get("source") != null ? body.get("source").toString() : "internal";
        String description = body.get("description") != null ? body.get("description").toString() : "内部奖励";
        userService.addPointsInternal(userId, points, source, description);
        return Result.success(null);
    }

    /**
     * 获取用户VIP等级 (供Image服务内部调用)
     */
    @GetMapping("/internal/vip-level")
    public Result<Integer> getVipLevel(@RequestHeader("X-User-Id") Long userId) {
        User user = userService.findById(userId);
        if (user == null) {
            return Result.success(0);
        }
        return Result.success(user.isVip() ? user.getVipLevel() : 0);
    }

    /**
     * 批量获取用户基础信息 (供Image服务内部调用)
     */
    @PostMapping("/internal/basic-info/batch")
    public Result<List<UserBasicInfo>> getBasicInfoBatch(@RequestBody Map<String, List<Long>> body) {
        List<Long> userIds = body != null ? body.get("userIds") : null;
        return Result.success(userService.listBasicInfoByIds(userIds));
    }

    @Data
    public static class ProfileRequest {
        @NotBlank(message = "昵称不能为空")
        private String nickname;
        @NotBlank(message = "头像不能为空")
        private String avatar;
    }

    @Data
    public static class EmailBindRequest {
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "请输入正确的邮箱地址")
        private String email;

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "请输入6位验证码")
        private String code;
    }

    @Data
    public static class PhoneBindRequest {
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
        private String phone;

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "请输入6位验证码")
        private String code;
    }

    @Data
    public static class RealNameVerifyRequest {
        @NotBlank(message = "真实姓名不能为空")
        @Size(max = 30, message = "真实姓名长度不能超过30个字符")
        private String realName;

        @NotBlank(message = "身份证号不能为空")
        @Pattern(regexp = "^(\\d{15}|\\d{17}[\\dXx])$", message = "请输入正确的身份证号")
        private String idCard;
    }
}
