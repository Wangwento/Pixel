package com.wwt.pixel.user.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户增长活动
 */
@Data
public class GrowthActivity {
    private Long id;
    private String activityCode;
    private String activityName;
    private String triggerType;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private Integer oncePerUser;
    private Integer autoGrant;
    private Integer priority;
    private String extConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
