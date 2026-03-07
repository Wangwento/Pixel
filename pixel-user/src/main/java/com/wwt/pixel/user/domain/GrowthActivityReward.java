package com.wwt.pixel.user.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户增长活动奖励
 */
@Data
public class GrowthActivityReward {
    private Long id;
    private Long activityId;
    private String rewardType;
    private BigDecimal rewardValue;
    private String rewardUnit;
    private Integer expireDays;
    private Integer status;
    private Integer sortOrder;
    private String extraConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
