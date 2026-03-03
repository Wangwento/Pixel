package com.wwt.pixel.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 标签实体
 */
@Data
public class Tag {
    private Long id;
    private String name;
    private String nameEn;
    private String category;  // style/color/theme/mood
    private Long parentId;
    private String icon;
    private String color;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
}
