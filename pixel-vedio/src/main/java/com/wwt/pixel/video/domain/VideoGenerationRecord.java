package com.wwt.pixel.video.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoGenerationRecord {
    private Long id;
    private Long userId;
    private String taskId;
    private String providerTaskId;
    private String requestType;
    private String prompt;
    private String sourceImages;
    private String vendor;
    private String model;
    private String aspectRatio;
    private String duration;
    private BigDecimal cost;
    private Boolean hd;
    private String notifyHook;
    private Boolean watermark;
    private Boolean privateMode;
    private String status;
    private String progress;
    private String resultVideoUrl;
    private String coverUrl;
    private String failReason;
    private LocalDateTime submitTime;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
