package com.wwt.pixel.image.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产文件夹
 */
@Data
public class AssetFolder {
    private Long id;
    private Long userId;
    private Long parentId;
    private String folderName;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
