package com.wwt.pixel.image.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图片资产
 */
@Data
public class ImageAsset {
    private Long id;
    private Long userId;
    private Long generationRecordId;
    private Integer imageIndex;
    private Long folderId;
    private String title;
    private String imageUrl;
    private String prompt;
    private String style;
    private String sourceType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
