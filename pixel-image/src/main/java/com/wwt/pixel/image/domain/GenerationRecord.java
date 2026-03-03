package com.wwt.pixel.image.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生成记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationRecord {
    private Long id;
    private Long userId;
    private String prompt;
    private String negativePrompt;
    private String style;
    private String sourceImageUrl;
    private String resultImageUrl;
    private String vendor;
    private String model;
    private BigDecimal cost;
    private Integer status;  // 0-生成中, 1-成功, 2-失败
    private String errorMessage;
    private LocalDateTime createdAt;
}