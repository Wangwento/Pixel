package com.wwt.pixel.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.common.constant.MqConstants;
import com.wwt.pixel.common.event.MqEvent;
import com.wwt.pixel.common.event.UserRegisteredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户增长事件生产者
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGrowthEventProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    public void sendUserRegisteredEvent(Long userId, String username, Long invitedBy) {
        try {
            MqEvent<UserRegisteredEventData> event = MqEvent.<UserRegisteredEventData>builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(MqConstants.EVENT_USER_REGISTERED)
                    .timestamp(System.currentTimeMillis())
                    .data(UserRegisteredEventData.builder()
                            .userId(userId)
                            .username(username)
                            .invitedBy(invitedBy)
                            .registeredAt(LocalDateTime.now())
                            .build())
                    .build();

            String payload = objectMapper.writeValueAsString(event);
            rocketMQTemplate.convertAndSend(MqConstants.TOPIC_USER_GROWTH, payload);
            log.info("发送用户注册增长事件成功: userId={}, topic={}", userId, MqConstants.TOPIC_USER_GROWTH);
        } catch (Exception e) {
            log.error("发送用户注册增长事件失败: userId={}", userId, e);
        }
    }
}
