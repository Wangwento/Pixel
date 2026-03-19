package com.wwt.pixel.user.service;

import com.wwt.pixel.common.constant.CommonConstant;
import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.user.domain.PointsRecord;
import com.wwt.pixel.user.domain.User;
import com.wwt.pixel.user.domain.UserBasicInfo;
import com.wwt.pixel.user.mapper.PointsRecordMapper;
import com.wwt.pixel.user.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * 用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PointsRecordMapper pointsRecordMapper;

    public User findById(Long id) {
        return userMapper.findById(id);
    }

    public List<UserBasicInfo> listBasicInfoByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> normalizedIds = userIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return Collections.emptyList();
        }
        return userMapper.findBasicInfoByIds(normalizedIds);
    }

    /**
     * 用户签到 (第1天10分...第7天70分，VIP翻倍)
     */
    @Transactional
    public int signIn(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (!user.canSignIn()) {
            throw new BusinessException("今日已签到");
        }

        int earnedPoints = user.doSignIn();
        userMapper.updatePoints(userId, user.getPoints(), user.getTotalPoints());
        userMapper.updateSignIn(userId, user.getLastSignDate(), user.getContinuousSignDays());

        log.info("用户签到: userId={}, day={}, points={}",
                userId, user.getContinuousSignDays(), earnedPoints);
        return earnedPoints;
    }

    /**
     * 获取用户额度信息
     */
    public UserQuotaInfo getQuotaInfo(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            return null;
        }
        user.refreshDailyLimitIfNeeded();
        user.refreshMonthlyQuotaIfNeeded();

        UserQuotaInfo info = new UserQuotaInfo();
        info.setPoints(user.getPoints());
        info.setPointsPerImage(CommonConstant.POINTS_PER_IMAGE);
        info.setFreeQuota(user.getFreeQuota());
        info.setMonthlyQuota(user.getMonthlyQuota());
        info.setMonthlyUsed(user.getMonthlyQuotaUsed());
        info.setMonthlyRemaining(user.getAvailableMonthlyQuota());
        info.setDailyLimit(user.getDailyLimit());
        info.setDailyUsed(user.getDailyUsed());
        info.setDailyRemaining(user.getAvailableDailyLimit());
        info.setVip(user.isVip());
        info.setVipLevel(user.getVipLevel());
        // 总可用额度: 免费额度 + VIP月度剩余
        int totalAvailable = (user.getFreeQuota() != null ? user.getFreeQuota() : 0)
                + user.getAvailableMonthlyQuota();
        info.setTotalAvailable(totalAvailable);
        info.setCanGenerateCount(Math.min(totalAvailable, user.getAvailableDailyLimit()));
        return info;
    }

    /**
     * 完善资料奖励 (50积分)
     */
    @Transactional
    public int completeProfileReward(Long userId, String nickname, String avatar) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getProfileCompleted() != null && user.getProfileCompleted() == 1) {
            throw new BusinessException("资料已完善，无法重复领取");
        }

        user.addPoints(CommonConstant.PROFILE_COMPLETE_POINTS);
        userMapper.updatePoints(userId, user.getPoints(), user.getTotalPoints());
        userMapper.updateProfile(userId, nickname, avatar, 1);

        log.info("完善资料奖励: userId={}, points={}", userId, CommonConstant.PROFILE_COMPLETE_POINTS);
        return CommonConstant.PROFILE_COMPLETE_POINTS;
    }

    /**
     * 检查是否有足够额度 (不扣减，只检查)
     * @return true-有额度, false-无额度
     */
    public boolean checkQuotaAvailable(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            return false;
        }

        user.refreshDailyLimitIfNeeded();

        // VIP用户没有每日限制
        if (!user.isVip()) {
            // 检查每日限额（仅对非VIP用户）
            if (user.getDailyUsed() >= user.getDailyLimit()) {
                return false;
            }
        }

        // 检查是否有可用额度: 免费额度 + VIP月度额度
        int availableQuota = (user.getFreeQuota() != null ? user.getFreeQuota() : 0);
        if (user.isVip()) {
            availableQuota += user.getAvailableMonthlyQuota();
        }

        return availableQuota > 0;
    }

    /**
     * 消耗额度 (生成图片时由Image服务调用)
     * 扣减顺序: 1.免费额度 -> 2.VIP月度额度
     * VIP用户没有每日限制
     * @return 消耗类型: free_quota | vip_monthly | null(失败)
     */
    @Transactional
    public String consumeQuota(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.refreshDailyLimitIfNeeded();

        // VIP用户没有每日限制
        if (!user.isVip()) {
            // 检查每日限额（仅对非VIP用户）
            if (!user.consumeDailyLimit()) {
                throw new BusinessException(String.format("今日已达生成上限(%d张)，升级VIP享受无限制", user.getDailyLimit()));
            }
        } else {
            // VIP用户也要记录每日使用次数（用于统计）
            user.setDailyUsed(user.getDailyUsed() + 1);
        }

        // 1. 优先消耗免费额度
        int freeConsumed = user.consumeFreeQuota(1);
        if (freeConsumed > 0) {
            userMapper.updateFreeQuota(userId, user.getFreeQuota(), user.getFreeQuotaTotal());
            userMapper.updateDailyLimit(userId, user.getDailyUsed(), user.getDailyLimitDate());
            log.info("消耗免费额度: userId={}", userId);
            return "free_quota";
        }

        // 2. 免费额度用完后，消耗VIP月度额度
        if (user.isVip()) {
            int consumed = user.consumeMonthlyQuota(1);
            if (consumed > 0) {
                userMapper.updateMonthlyQuota(userId, user.getMonthlyQuotaUsed(), user.getMonthlyQuotaDate());
                userMapper.updateDailyLimit(userId, user.getDailyUsed(), user.getDailyLimitDate());
                log.info("消耗VIP月度额度: userId={}", userId);
                return "vip_monthly";
            }
        }

        // 回滚每日限额（仅非VIP用户）
        if (!user.isVip()) {
            user.setDailyUsed(user.getDailyUsed() - 1);
        }
        throw new BusinessException("额度不足");
    }

    /**
     * 内部加积分 (供其他服务通过Feign调用)
     * type=13 表示热门奖励
     */
    @Transactional
    public void addPointsInternal(Long userId, int points, String source, String description) {
        if (points <= 0) {
            throw new BusinessException("积分数量必须大于0");
        }
        User user = userMapper.findByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.addPoints(points);
        userMapper.updatePoints(userId, user.getPoints(), user.getTotalPoints());
        pointsRecordMapper.insert(PointsRecord.builder()
                .userId(userId)
                .points(points)
                .balance(user.getPoints())
                .type(13) // 热门奖励
                .source(source)
                .description(description)
                .build());
        log.info("内部加积分: userId={}, points={}, source={}", userId, points, source);
    }

    /**
     * 用户额度信息DTO
     */
    @Data
    public static class UserQuotaInfo {
        private Integer points;
        private Integer pointsPerImage;
        private Integer freeQuota;
        private Integer monthlyQuota;
        private Integer monthlyUsed;
        private Integer monthlyRemaining;
        private Integer totalAvailable;
        private Integer dailyLimit;
        private Integer dailyUsed;
        private Integer dailyRemaining;
        private Boolean vip;
        private Integer vipLevel;
        private Integer canGenerateCount;
    }
}
