package com.wwt.pixel.application.service;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.domain.model.AdvertRecord;
import com.wwt.pixel.domain.model.PointsRecord;
import com.wwt.pixel.domain.model.User;
import com.wwt.pixel.infrastructure.persistence.mapper.AdvertRecordMapper;
import com.wwt.pixel.infrastructure.persistence.mapper.PointsRecordMapper;
import com.wwt.pixel.infrastructure.persistence.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 广告服务 (核心盈利点: 用户看广告获取积分)
 *
 * 积分规则:
 * - 每次观看60秒激励视频广告 = 30积分
 * - 需要3-4个广告才能生成1张图(100积分)
 * - 广告价值约0.05-0.07元，3-4个广告可覆盖0.2元成本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvertService {

    private final UserMapper userMapper;
    private final AdvertRecordMapper advertRecordMapper;
    private final PointsRecordMapper pointsRecordMapper;

    // 广告积分配置
    public static final int VIDEO_AD_POINTS = 30;         // 激励视频广告积分
    public static final int DAILY_AD_LIMIT = 20;          // 每日观看广告上限
    public static final int MIN_WATCH_DURATION = 15;      // 最短观看时长(秒)

    /**
     * 观看激励视频广告获得积分
     *
     * @param userId 用户ID
     * @param adId 广告ID(来自广告平台回调)
     * @param duration 实际观看时长(秒)
     * @return 获得的积分
     */
    @Transactional
    public int watchVideoAd(Long userId, String adId, int duration) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 检查观看时长
        if (duration < MIN_WATCH_DURATION) {
            throw new BusinessException("广告观看时长不足，请完整观看");
        }

        // 检查今日观看次数
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        int todayCount = advertRecordMapper.countTodayAds(userId, todayStart);
        if (todayCount >= DAILY_AD_LIMIT) {
            throw new BusinessException(String.format("今日已观看%d个广告，明天再来吧", DAILY_AD_LIMIT));
        }

        // 记录广告观看
        AdvertRecord record = AdvertRecord.builder()
                .userId(userId)
                .adType(AdvertRecord.AdType.VIDEO)
                .adId(adId)
                .pointsEarned(VIDEO_AD_POINTS)
                .duration(duration)
                .createdAt(LocalDateTime.now())
                .build();
        advertRecordMapper.insert(record);

        // 发放积分
        user.addPoints(VIDEO_AD_POINTS);
        userMapper.updatePoints(userId, user.getPoints(), user.getTotalPoints());

        // 记录积分变动
        PointsRecord pointsRecord = PointsRecord.builder()
                .userId(userId)
                .points(VIDEO_AD_POINTS)
                .balance(user.getPoints())
                .type(PointsRecord.Type.WATCH_AD)
                .source("ad_" + adId)
                .description("观看激励视频广告")
                .createdAt(LocalDateTime.now())
                .build();
        pointsRecordMapper.insert(pointsRecord);

        log.info("广告积分发放: userId={}, adId={}, points={}, todayCount={}",
                userId, adId, VIDEO_AD_POINTS, todayCount + 1);

        return VIDEO_AD_POINTS;
    }

    /**
     * 获取今日广告观看信息
     */
    public AdWatchInfo getTodayAdInfo(Long userId) {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        int todayCount = advertRecordMapper.countTodayAds(userId, todayStart);
        int todayPoints = advertRecordMapper.sumTodayPoints(userId, todayStart);

        AdWatchInfo info = new AdWatchInfo();
        info.setTodayWatchCount(todayCount);
        info.setDailyLimit(DAILY_AD_LIMIT);
        info.setRemainingCount(Math.max(0, DAILY_AD_LIMIT - todayCount));
        info.setTodayEarnedPoints(todayPoints);
        info.setPointsPerAd(VIDEO_AD_POINTS);
        return info;
    }

    /**
     * 广告观看信息DTO
     */
    @Data
    public static class AdWatchInfo {
        private Integer todayWatchCount;     // 今日已观看次数
        private Integer dailyLimit;          // 每日上限
        private Integer remainingCount;      // 今日剩余可看次数
        private Integer todayEarnedPoints;   // 今日广告获得积分
        private Integer pointsPerAd;         // 每次广告获得积分
    }
}