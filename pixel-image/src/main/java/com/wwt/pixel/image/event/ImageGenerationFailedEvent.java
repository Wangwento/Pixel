package com.wwt.pixel.image.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 图片生成失败事件数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationFailedEvent implements Serializable {

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
     * 错误信息
     */
    private String errorMessage;
}