package com.wwt.pixel.user.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 额度流水
 */
@Data
@Builder
public class QuotaRecord {
    private Long id;
    private Long userId;
    private Integer quota;
    private Integer balance;
    private Integer type;
    private String source;
    private String description;
    private LocalDateTime createdAt;
}
