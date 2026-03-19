package com.wwt.pixel.video.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssetFolder {
    private Long id;
    private Long userId;
    private String folderName;
    private Long parentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}