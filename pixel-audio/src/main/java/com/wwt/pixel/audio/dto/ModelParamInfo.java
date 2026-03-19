package com.wwt.pixel.audio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelParamInfo {
    private String paramKey;
    private String paramName;
    private String paramType;
    private Boolean required;
    private Boolean visible;
    private String defaultValue;
    private String options;
    private String validationRule;
    private String description;
    private Integer displayOrder;
}
