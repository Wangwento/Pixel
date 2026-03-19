package com.wwt.pixel.admin.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminOperationLog {
    private Long id;
    private Long adminId;
    private String adminName;
    private String module;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String ip;
    private LocalDateTime createdAt;
}
