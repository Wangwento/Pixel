package com.wwt.pixel.user.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.user.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图片生成事件消费者 - 负责扣减用户额度
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "pixel-image-generation",
        consumerGroup = "user-quota-consumer-group",
        maxReconsumeTimes = 3
)
public class ImageGenerationEventConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(String message) {
        try {
            log.info("收到图片生成事件: {}", message);

            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            String eventId = (String) event.get("eventId");
            String eventType = (String) event.get("eventType");

            if (isDuplicate(eventId)) {
                log.warn("消息已处理，跳过: eventId={}", eventId);
                return;
            }

            if ("IMAGE_GENERATION_SUCCESS".equals(eventType)) {
                LinkedHashMap<String, Object> data = (LinkedHashMap<String, Object>) event.get("data");
                Long userId = Long.valueOf(data.get("userId").toString());
                userService.consumeQuota(userId);
                log.info("扣减用户额度成功: userId={}", userId);
            }

            markAsProcessed(eventId);

        } catch (Exception e) {
            log.error("处理图片生成事件失败", e);
            throw new RuntimeException("处理消息失败", e);
        }
    }

    private boolean isDuplicate(String eventId) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(
                "message:processed:user-quota:" + eventId, "1", Duration.ofDays(7));
        return !Boolean.TRUE.equals(result);
    }

    private void markAsProcessed(String eventId) {
        redisTemplate.opsForValue().set("message:processed:user-quota:" + eventId, "1", Duration.ofDays(7));
    }
}