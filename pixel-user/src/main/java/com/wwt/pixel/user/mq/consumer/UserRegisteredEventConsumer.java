package com.wwt.pixel.user.mq.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.common.constant.MqConstants;
import com.wwt.pixel.common.event.MqEvent;
import com.wwt.pixel.common.event.UserRegisteredEventData;
import com.wwt.pixel.user.service.UserGrowthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 注册成功事件消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_USER_GROWTH,
        consumerGroup = "user-growth-consumer-group",
        maxReconsumeTimes = 3
)
public class UserRegisteredEventConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final UserGrowthService userGrowthService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(String message) {
        try {
            MqEvent<UserRegisteredEventData> event = objectMapper.readValue(
                    message, new TypeReference<MqEvent<UserRegisteredEventData>>() {
                    });

            if (!MqConstants.EVENT_USER_REGISTERED.equals(event.getEventType()) || event.getData() == null) {
                return;
            }

            String eventId = event.getEventId();
            if (isDuplicate(eventId)) {
                log.warn("注册事件重复消费，跳过: eventId={}", eventId);
                return;
            }

            UserRegisteredEventData data = event.getData();
            userGrowthService.createRegisterGiftTask(data.getUserId());
            markAsProcessed(eventId);
            log.info("注册礼包任务创建成功: userId={}, username={}", data.getUserId(), data.getUsername());
        } catch (Exception e) {
            log.error("处理注册成功事件失败", e);
            throw new RuntimeException("处理注册成功事件失败", e);
        }
    }

    private boolean isDuplicate(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey("message:processed:user-growth:" + eventId));
    }

    private void markAsProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set("message:processed:user-growth:" + eventId, "1", Duration.ofDays(7));
    }
}
