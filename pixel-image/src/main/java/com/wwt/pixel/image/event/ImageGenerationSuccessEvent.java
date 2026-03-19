package com.wwt.pixel.image.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 图片生成成功事件数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationSuccessEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 生成记录ID
     */
    private Long recordId;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 图片URL
     */
    private String imageUrl;

    private List<String> imageUrls;

    /**
     * OSS URL
     */
    private String ossUrl;

    private List<String> ossUrls;

    /**
     * 厂商
     */
    private String vendor;

    /**
     * 模型
     */
    private String model;

    /**
     * 生成耗时（毫秒）
     */
    private Long generationTimeMs;

    /**
     * 是否来自缓存
     */
    private Boolean fromCache;

    /**
     * 优化后的提示词
     */
    private String revisedPrompt;
}
