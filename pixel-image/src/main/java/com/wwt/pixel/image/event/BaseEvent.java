package com.wwt.pixel.image.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 基础事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件ID（唯一标识）
     */
    private String eventId;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 事件时间戳
     */
    private Long timestamp;

    /**
     * 业务数据
     */
    private Object data;
}