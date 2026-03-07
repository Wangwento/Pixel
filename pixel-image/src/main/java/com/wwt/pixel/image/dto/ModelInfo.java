package com.wwt.pixel.image.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private boolean imageToImageSupported;
}
