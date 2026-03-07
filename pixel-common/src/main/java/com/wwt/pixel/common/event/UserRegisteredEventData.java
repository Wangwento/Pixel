package com.wwt.pixel.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户注册成功事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEventData {

    private Long userId;

    private String username;

    private Long invitedBy;

    private LocalDateTime registeredAt;
}
