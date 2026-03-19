package com.wwt.pixel.admin.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiModel {
    private Long id;
    private Long providerId;
    private String modelCode;
    private String modelName;
    private String modelType;
    private String category;
    private Boolean supportsImageInput;
    private String apiKey;
    private Boolean enabled;
    private Integer minVipLevel;
    private java.math.BigDecimal costPerUnit;
    private Integer timeoutMs;
    private Integer retryCount;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}