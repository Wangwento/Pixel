package com.wwt.pixel.audio.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AudioAsset {
    private Long id;
    private Long userId;
    private Long generationRecordId;
    private Long folderId;
    private String clipId;
    private String title;
    private String audioUrl;
    private String videoUrl;
    private String coverUrl;
    private String prompt;
    private String tags;
    private String model;
    private String sourceType;
    private String status;
    private String rawPayload;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
