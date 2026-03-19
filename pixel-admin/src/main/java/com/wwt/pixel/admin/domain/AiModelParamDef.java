package com.wwt.pixel.admin.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiModelParamDef {
    private Long id;
    private Long modelId;
    private String paramKey;
    private String paramName;
    private String paramType; // string/number/boolean/select/multiSelect/array/object
    private Boolean required;
    private Boolean visible; // 是否对用户可见
    private String defaultValue;
    private String options; // JSON格式
    private String validationRule;
    private String description;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}