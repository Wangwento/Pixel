package com.wwt.pixel.user.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户增长活动记录
 */
@Data
public class UserGrowthRecord {
    private Long id;
    private Long activityId;
    private Long userId;
    private String bizKey;
    private String triggerType;
    private String triggerSource;
    private Integer hitStatus;
    private Integer rewardStatus;
    private String rewardSnapshot;
    private LocalDateTime triggeredAt;
    private LocalDateTime grantedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
