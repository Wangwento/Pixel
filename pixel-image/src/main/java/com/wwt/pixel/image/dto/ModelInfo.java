package com.wwt.pixel.image.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 面向前端的模型信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfo {
    private String modelId;
    private String displayName;
    private int minVipLevel;
    private boolean available;
    private BigDecimal costPerUnit;
    private boolean supportsImageInput;  // 是否支持图片输入（图生图）
    private List<ModelParamInfo> params;  // 模型参数定义
}
