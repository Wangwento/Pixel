package com.wwt.pixel.image.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 本地消息表实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalMessage {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 消息ID（唯一）
     */
    private String messageId;

    /**
     * Topic
     */
    private String topic;

    /**
     * Tag
     */
    private String tag;

    /**
     * 消息内容（JSON）
     */
    private String content;

    /**
     * 状态：0-待发送，1-已发送，2-发送失败
     */
    private Integer status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetry;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}