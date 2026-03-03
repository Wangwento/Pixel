package com.wwt.pixel.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleTemplate {

    private Long id;

    private String name;

    private String nameEn;

    private String description;

    private String promptTemplate;

    private String coverImage;

    private String category;

    private Integer sortOrder;
}
