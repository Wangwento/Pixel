package com.wwt.pixel.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用 MQ 事件包装
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqEvent<T> {

    private String eventId;

    private String eventType;

    private Long timestamp;

    private T data;
}
