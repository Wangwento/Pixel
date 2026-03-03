package com.wwt.pixel.adapter.web.controller;

import com.wwt.pixel.application.service.AdvertService;
import com.wwt.pixel.application.service.InviteService;
import com.wwt.pixel.application.service.QuotaService;
import com.wwt.pixel.application.service.UserService;
import com.wwt.pixel.common.Result;
import com.wwt.pixel.domain.model.InviteRecord;
import com.wwt.pixel.domain.model.PointsRecord;
import com.wwt.pixel.domain.model.QuotaPackage;
import com.wwt.pixel.domain.model.QuotaRecord;
import com.wwt.pixel.infrastructure.security.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户控制器 - 积分、额度、签到、广告、邀请
 *
 * 新积分体系:
 * - 1张图 = 100积分 = 0.2元成本
 * - 观看1个广告 = 30积分 (需3-4个广告生成1张图)
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final QuotaService quotaService;
    private final AdvertService advertService;
    private final InviteService inviteService;

    /**
     * 用户签到 (第1天10分...第7天70分, VIP翻倍)
     */
    @PostMapping("/sign-in")
    public Result<Map<String, Object>> signIn() {
        Long userId = UserContext.requireCurrentUserId();
        int earnedPoints = userService.signIn(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("earnedPoints", earnedPoints);
        data.put("quotaInfo", userService.getQuotaInfo(userId));

        return Result.success(data);
    }

    /**
     * 获取额度信息 (新体系)
     */
    @GetMapping("/quota")
    public Result<UserService.UserQuotaInfo> getQuotaInfo() {
        Long userId = UserContext.requireCurrentUserId();
        return Result.success(userService.getQuotaInfo(userId));
    }

    /**
     * 获取额度包列表
     */
    @GetMapping("/quota/packages")
    public Result<List<QuotaPackage>> getQuotaPackages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.requireCurrentUserId();
        return Result.success(quotaService.getQuotaPackages(userId, page, size));
    }

    /**
     * 积分兑换生成额度 (100积分=1张图)
     */
    @PostMapping("/exchange-quota")
    public Result<Map<String, Object>> exchangeQuota(@Valid @RequestBody ExchangeRequest request) {
        Long userId = UserContext.requireCurrentUserId();
        int images = userService.exchangeQuota(userId, request.getCount());

        Map<String, Object> data = new HashMap<>();
        data.put("exchangedImages", images);
        data.put("quotaInfo", userService.getQuotaInfo(userId));

        return Result.success(data);
    }

    // ==================== 广告系统 ====================

    /**
     * 观看激励视频广告获得积分 (每次30积分)
     */
    @PostMapping("/ad/watch")
    public Result<Map<String, Object>> watchAd(@Valid @RequestBody WatchAdRequest request) {
        Long userId = UserContext.requireCurrentUserId();
        int earnedPoints = advertService.watchVideoAd(userId, request.getAdId(), request.getDuration());

        Map<String, Object> data = new HashMap<>();
        data.put("earnedPoints", earnedPoints);
        data.put("adInfo", advertService.getTodayAdInfo(userId));
        data.put("quotaInfo", userService.getQuotaInfo(userId));

        return Result.success(data);
    }

    /**
     * 获取今日广告观看信息
     */
    @GetMapping("/ad/info")
    public Result<AdvertService.AdWatchInfo> getAdInfo() {
        Long userId = UserContext.requireCurrentUserId();
        return Result.success(advertService.getTodayAdInfo(userId));
    }

    // ==================== 邀请系统 ====================

    /**
     * 获取邀请统计信息
     */
    @GetMapping("/invite/stats")
    public Result<InviteService.InviteStats> getInviteStats() {
        Long userId = UserContext.requireCurrentUserId();
        return Result.success(inviteService.getInviteStats(userId));
    }

    /**
     * 获取邀请记录列表
     */
    @GetMapping("/invite/records")
    public Result<List<InviteRecord>> getInviteRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.requireCurrentUserId();
        return Result.success(inviteService.getInviteRecords(userId, page, size));
    }

    // ==================== 分享与资料完善 ====================

    /**
     * 分享作品获得积分 (每次20积分)
     */
    @PostMapping("/share")
    public Result<Map<String, Object>> shareImage(@Valid @RequestBody ShareRequest request) {
        Long userId = UserContext.requireCurrentUserId();
        int earnedPoints = userService.shareReward(userId, request.getImageId());

        Map<String, Object> data = new HashMap<>();
        data.put("earnedPoints", earnedPoints);
        data.put("quotaInfo", userService.getQuotaInfo(userId));

        return Result.success(data);
    }

    /**
     * 完善资料获得积分 (头像+昵称, 50积分)
     */
    @PostMapping("/profile/complete")
    public Result<Map<String, Object>> completeProfile(@Valid @RequestBody ProfileRequest request) {
        Long userId = UserContext.requireCurrentUserId();
        int earnedPoints = userService.completeProfileReward(userId, request.getNickname(), request.getAvatar());

        Map<String, Object> data = new HashMap<>();
        data.put("earnedPoints", earnedPoints);
        data.put("quotaInfo", userService.getQuotaInfo(userId));

        return Result.success(data);
    }

    /**
     * 领取手机认证奖励
     */
    @PostMapping("/claim/phone-verify-reward")
    public Result<Map<String, Object>> claimPhoneVerifyReward() {
        Long userId = UserContext.requireCurrentUserId();
        QuotaPackage pkg = quotaService.claimPhoneVerifyReward(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("quotaPackage", pkg);
        data.put("quotaInfo", quotaService.getQuotaInfo(userId));

        return Result.success(data);
    }

    /**
     * 领取实名认证奖励
     */
    @PostMapping("/claim/real-name-verify-reward")
    public Result<Map<String, Object>> claimRealNameVerifyReward() {
        Long userId = UserContext.requireCurrentUserId();
        QuotaPackage pkg = quotaService.claimRealNameVerifyReward(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("quotaPackage", pkg);
        data.put("quotaInfo", quotaService.getQuotaInfo(userId));

        return Result.success(data);
    }

    /**
     * 获取积分记录
     */
    @GetMapping("/points/records")
    public Result<List<PointsRecord>> getPointsRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.requireCurrentUserId();
        return Result.success(userService.getPointsRecords(userId, page, size));
    }

    /**
     * 获取额度变动记录
     */
    @GetMapping("/quota/records")
    public Result<List<QuotaRecord>> getQuotaRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.requireCurrentUserId();
        return Result.success(userService.getQuotaRecords(userId, page, size));
    }

    // ========== 请求DTO ==========

    @Data
    public static class ExchangeRequest {
        @Min(value = 1, message = "兑换数量至少为1")
        private Integer count;
    }

    @Data
    public static class WatchAdRequest {
        @NotBlank(message = "广告ID不能为空")
        private String adId;
        @Min(value = 1, message = "观看时长不能为空")
        private Integer duration;  // 观看时长(秒)
    }

    @Data
    public static class ShareRequest {
        @NotBlank(message = "图片ID不能为空")
        private String imageId;
    }

    @Data
    public static class ProfileRequest {
        @NotBlank(message = "昵称不能为空")
        private String nickname;
        @NotBlank(message = "头像不能为空")
        private String avatar;
    }
}