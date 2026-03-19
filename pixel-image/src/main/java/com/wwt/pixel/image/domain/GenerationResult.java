package com.wwt.pixel.image.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationResult {

    /**
     * 任务ID（用于幂等处理）
     */
    private String taskId;

    /**
     * 任务状态：RUNNING-生成中, SUCCESS-成功, FAILED-失败
     */
    private String taskStatus;

    private String imageUrl;

    private String imageBase64;

    private List<String> imageUrls;

    private List<String> imageBase64List;

    /**
     * OSS存储URL（持久化存储）
     */
    private String ossUrl;

    private List<String> ossUrls;

    private String revisedPrompt;

    private String vendor;

    private String model;

    private Long generationTimeMs;

    private Boolean fromCache;
}
