package com.wwt.pixel.image.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.image.domain.GenerationRecord;
import com.wwt.pixel.image.event.BaseEvent;
import com.wwt.pixel.image.event.EventType;
import com.wwt.pixel.image.event.ImageGenerationFailedEvent;
import com.wwt.pixel.image.event.ImageGenerationSuccessEvent;
import com.wwt.pixel.image.mapper.GenerationRecordMapper;
import com.wwt.pixel.image.mq.MQTopic;
import com.wwt.pixel.image.service.AssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 图片生成事件消费者 - 负责更新生成记录状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MQTopic.IMAGE_GENERATION,
        consumerGroup = "image-record-consumer-group",
        maxReconsumeTimes = 3
)
public class ImageGenerationConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final GenerationRecordMapper generationRecordMapper;
    private final StringRedisTemplate redisTemplate;
    private final AssetService assetService;

    @Override
    public void onMessage(String message) {
        try {
            log.info("收到图片生成事件消息: {}", message);

            BaseEvent event = objectMapper.readValue(message, BaseEvent.class);

            if (isDuplicate("image-record:" + event.getEventId())) {
                log.warn("消息已处理，跳过: eventId={}", event.getEventId());
                return;
            }

            if (EventType.IMAGE_GENERATION_SUCCESS.equals(event.getEventType())) {
                handleSuccess(event);
            } else if (EventType.IMAGE_GENERATION_FAILED.equals(event.getEventType())) {
                handleFailure(event);
            }

            markAsProcessed("image-record:" + event.getEventId());

        } catch (Exception e) {
            log.error("处理图片生成事件失败: message={}", message, e);
            throw new RuntimeException("处理消息失败", e);
        }
    }

    private void handleSuccess(BaseEvent event) {
        LinkedHashMap<String, Object> dataMap = (LinkedHashMap<String, Object>) event.getData();
        ImageGenerationSuccessEvent successEvent = objectMapper.convertValue(dataMap, ImageGenerationSuccessEvent.class);
        if (successEvent.getRecordId() == null) {
            log.info("生成成功事件未携带recordId，跳过生成记录更新: taskId={}", successEvent.getTaskId());
            return;
        }

        GenerationRecord record = new GenerationRecord();
        record.setId(successEvent.getRecordId());
        record.setStatus(1);
        String finalUrl = successEvent.getOssUrl();
        if (finalUrl == null && successEvent.getOssUrls() != null && !successEvent.getOssUrls().isEmpty()) {
            finalUrl = successEvent.getOssUrls().get(0);
        }
        if (finalUrl == null) {
            finalUrl = successEvent.getImageUrl();
        }
        if (finalUrl == null && successEvent.getImageUrls() != null && !successEvent.getImageUrls().isEmpty()) {
            finalUrl = successEvent.getImageUrls().get(0);
        }
        record.setResultImageUrl(finalUrl);
        record.setVendor(successEvent.getVendor());
        record.setModel(successEvent.getModel());
        record.setCost(BigDecimal.valueOf(0.2));

        generationRecordMapper.updateStatus(record);
        assetService.syncAssetFromGenerationRecord(successEvent.getRecordId(), resolveAssetImageUrls(successEvent));
        log.info("更新生成记录成功: recordId={}", successEvent.getRecordId());
    }

    private void handleFailure(BaseEvent event) {
        LinkedHashMap<String, Object> dataMap = (LinkedHashMap<String, Object>) event.getData();
        ImageGenerationFailedEvent failedEvent = objectMapper.convertValue(dataMap, ImageGenerationFailedEvent.class);
        if (failedEvent.getRecordId() == null) {
            log.info("生成失败事件未携带recordId，跳过生成记录更新: taskId={}", failedEvent.getTaskId());
            return;
        }

        GenerationRecord record = new GenerationRecord();
        record.setId(failedEvent.getRecordId());
        record.setStatus(2);
        record.setErrorMessage(failedEvent.getErrorMessage());
        generationRecordMapper.updateStatus(record);
        log.info("更新生成记录失败状态: recordId={}", failedEvent.getRecordId());
    }

    private boolean isDuplicate(String key) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent("message:processed:" + key, "1", Duration.ofDays(7));
        return !Boolean.TRUE.equals(result);
    }

    private void markAsProcessed(String key) {
        redisTemplate.opsForValue().set("message:processed:" + key, "1", Duration.ofDays(7));
    }

    private List<String> resolveAssetImageUrls(ImageGenerationSuccessEvent successEvent) {
        List<String> ossUrls = normalizeImageUrls(successEvent.getOssUrls());
        if (!ossUrls.isEmpty()) {
            return ossUrls;
        }
        List<String> imageUrls = normalizeImageUrls(successEvent.getImageUrls());
        if (!imageUrls.isEmpty()) {
            return imageUrls;
        }
        List<String> singleOssUrl = normalizeImageUrl(successEvent.getOssUrl());
        if (!singleOssUrl.isEmpty()) {
            return singleOssUrl;
        }
        return normalizeImageUrl(successEvent.getImageUrl());
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        List<String> normalized = new ArrayList<>();
        if (imageUrls == null) {
            return normalized;
        }
        for (String imageUrl : imageUrls) {
            if (imageUrl == null) {
                continue;
            }
            String trimmed = imageUrl.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private List<String> normalizeImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return new ArrayList<>();
        }
        return normalizeImageUrls(List.of(imageUrl));
    }
}
