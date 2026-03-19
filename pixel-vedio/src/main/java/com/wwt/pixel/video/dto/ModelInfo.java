package com.wwt.pixel.video.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfo {
    private String modelId;
    private String displayName;
    private String family;
    private int minVipLevel;
    private boolean available;
    private boolean textToVideoSupported;
    private boolean imageToVideoSupported;
    @Builder.Default
    private List<String> supportedAspectRatios = new ArrayList<>();
    @Builder.Default
    private List<String> supportedTextDurations = new ArrayList<>();
    @Builder.Default
    private List<String> supportedImageDurations = new ArrayList<>();
    private boolean supportsHd;
    private boolean supportsEnhancePrompt;
    private boolean supportsUpsample;
    private int minImageCount;
    private int maxImageCount;
    private String defaultAspectRatio;
    private String defaultTextDuration;
    private String defaultImageDuration;
    private BigDecimal costPerSecond;
    private boolean defaultHd;
    private boolean defaultEnhancePrompt;
    private boolean defaultUpsample;
    @Builder.Default
    private List<ModelParamInfo> params = new ArrayList<>();
}
