package com.wwt.pixel.audio.dto;

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
public class AudioModelInfo {

    private String modelId;

    private String displayName;

    private String description;

    private int minVipLevel;

    private boolean available;

    @Builder.Default
    private List<String> supportedTasks = new ArrayList<>();

    private BigDecimal costPerUnit;

    @Builder.Default
    private List<ModelParamInfo> params = new ArrayList<>();
}
