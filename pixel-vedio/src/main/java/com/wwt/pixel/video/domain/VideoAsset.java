package com.wwt.pixel.video.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VideoAsset {
    private Long id;
    private Long userId;
    private Long generationRecordId;
    private Long folderId;
    private String title;
    private String videoUrl;
    private String coverUrl;
    private String prompt;
    private String duration;
    private String sourceType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
