package com.wwt.pixel.user.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水
 */
@Data
@Builder
public class PointsRecord {
    private Long id;
    private Long userId;
    private Integer points;
    private Integer balance;
    private Integer type;
    private String source;
    private String description;
    private LocalDateTime createdAt;
}
