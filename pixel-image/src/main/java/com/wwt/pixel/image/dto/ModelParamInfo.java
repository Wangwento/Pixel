package com.wwt.pixel.image.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型参数定义
 */
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