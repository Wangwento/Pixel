package com.wwt.pixel.audio.domain;

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
public class AudioGenerationRecord {
    private Long id;
    private Long userId;
    private String taskId;
    private String providerTaskId;
    private String requestType;
    private String prompt;
    private String title;
    private String tags;
    private String continueClipId;
    private String vendor;
    private String model;
    private BigDecimal cost;
    private Boolean makeInstrumental;
    private String requestPayload;
    private String responsePayload;
    private String status;
    private Integer resultCount;
    private String failReason;
    private LocalDateTime submitTime;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
