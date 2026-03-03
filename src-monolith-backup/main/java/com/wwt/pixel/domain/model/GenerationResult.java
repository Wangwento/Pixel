package com.wwt.pixel.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationResult {

    private String imageUrl;

    private String imageBase64;

    /**
     * OSS存储URL（持久化存储）
     */
    private String ossUrl;

    private String revisedPrompt;

    private String vendor;

    private String model;

    private Long generationTimeMs;

    private Boolean fromCache;
}
