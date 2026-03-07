package com.wwt.pixel.image.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.image.domain.LocalMessage;
import com.wwt.pixel.image.event.BaseEvent;
import com.wwt.pixel.image.mapper.LocalMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 消息生产者 - 集成本地消息表保证可靠投递
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final LocalMessageMapper localMessageMapper;

    /**
     * 发送消息（先写本地消息表，再发MQ）
     * 调用方需在事务内调用，保证业务操作和消息记录的原子性
     */
    public void sendMessage(String topic, String eventType, Object data) {
        String messageId = UUID.randomUUID().toString();
        try {
            BaseEvent event = BaseEvent.builder()
                    .eventId(messageId)
                    .eventType(eventType)
                    .timestamp(System.currentTimeMillis())
                    .data(data)
                    .build();

            String jsonMessage = objectMapper.writeValueAsString(event);

            // 1. 写本地消息表（状态：待发送）
            LocalMessage localMessage = LocalMessage.builder()
                    .messageId(messageId)
                    .topic(topic)
                    .content(jsonMessage)
                    .maxRetry(3)
                    .nextRetryTime(LocalDateTime.now().plusMinutes(1))
                    .build();
            localMessageMapper.insert(localMessage);

            // 2. 发送 MQ
            Message<String> message = MessageBuilder.withPayload(jsonMessage).build();
            rocketMQTemplate.send(topic, message);

            // 3. 更新状态为已发送
            localMessageMapper.updateStatus(messageId, 1);
            log.info("消息发送成功: topic={}, eventType={}, messageId={}", topic, eventType, messageId);

        } catch (Exception e) {
            log.error("消息发送失败: topic={}, eventType={}, messageId={}", topic, eventType, messageId, e);
            // 标记为失败，由定时任务重试
            localMessageMapper.updateFailed(messageId, LocalDateTime.now().plusMinutes(1), e.getMessage());
        }
    }

    /**
     * 重试发送（由定时任务调用）
     */
    public void retrySend(LocalMessage localMessage) {
        try {
            Message<String> message = MessageBuilder.withPayload(localMessage.getContent()).build();
            rocketMQTemplate.send(localMessage.getTopic(), message);
            localMessageMapper.updateStatus(localMessage.getMessageId(), 1);
            log.info("消息重试成功: messageId={}, retryCount={}", localMessage.getMessageId(), localMessage.getRetryCount());
        } catch (Exception e) {
            // 计算下次重试时间（指数退避：1min, 2min, 4min）
            int nextMinutes = (int) Math.pow(2, localMessage.getRetryCount());
            LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(nextMinutes);
            localMessageMapper.updateFailed(localMessage.getMessageId(), nextRetryTime, e.getMessage());
            log.warn("消息重试失败: messageId={}, retryCount={}, nextRetry={}",
                    localMessage.getMessageId(), localMessage.getRetryCount() + 1, nextRetryTime);
        }
    }
}