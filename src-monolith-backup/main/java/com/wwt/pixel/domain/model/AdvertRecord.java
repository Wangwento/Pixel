package com.wwt.pixel.domain.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 广告观看记录 (每次观看60秒视频广告奖励30积分)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvertRecord {
    private Long id;
    private Long userId;
    private String adType;           // 广告类型: video/banner/interstitial
    private String adId;             // 广告ID(来自广告平台)
    private Integer pointsEarned;    // 获得积分
    private Integer duration;        // 观看时长(秒)
    private LocalDateTime createdAt;

    /**
     * 广告类型常量
     */
    public static class AdType {
        public static final String VIDEO = "video";              // 激励视频广告(30积分)
        public static final String BANNER = "banner";            // 横幅广告(暂不奖励)
        public static final String INTERSTITIAL = "interstitial";// 插屏广告(暂不奖励)
    }
}
