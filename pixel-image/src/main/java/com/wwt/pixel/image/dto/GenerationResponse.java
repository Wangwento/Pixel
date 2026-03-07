package com.wwt.pixel.image.dto;

import com.wwt.pixel.image.domain.GenerationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片生成响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationResponse {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务状态：RUNNING-生成中, SUCCESS-成功, FAILED-失败
     */
    private String taskStatus;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 生成结果（仅在SUCCESS状态时有值）
     */
    private GenerationResult result;

    /**
     * 创建RUNNING状态的响应
     */
    public static GenerationResponse running(String taskId) {
        return GenerationResponse.builder()
                .taskId(taskId)
                .taskStatus("RUNNING")
                .message("图片正在生成中，请稍候...")
                .build();
    }

    /**
     * 创建SUCCESS状态的响应
     */
    public static GenerationResponse success(GenerationResult result) {
        return GenerationResponse.builder()
                .taskId(result.getTaskId())
                .taskStatus("SUCCESS")
                .message("图片生成成功")
                .result(result)
                .build();
    }

    /**
     * 创建FAILED状态的响应
     */
    public static GenerationResponse failed(String taskId, String errorMessage) {
        return GenerationResponse.builder()
                .taskId(taskId)
                .taskStatus("FAILED")
                .message(errorMessage)
                .build();
    }
}
