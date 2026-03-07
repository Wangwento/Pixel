package com.wwt.pixel.image.mq;

import com.wwt.pixel.image.domain.LocalMessage;
import com.wwt.pixel.image.mapper.LocalMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本地消息表定时重试任务
 * 扫描发送失败的消息，重新投递（最多重试3次）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalMessageRetryScheduler {

    private final LocalMessageMapper localMessageMapper;
    private final MessageProducer messageProducer;

    /**
     * 每30秒扫描一次待重试消息
     */
    @Scheduled(fixedDelay = 30_000)
    public void retryFailedMessages() {
        List<LocalMessage> messages = localMessageMapper.findPendingRetry();
        if (messages.isEmpty()) {
            return;
        }

        log.info("扫描到待重试消息: count={}", messages.size());
        for (LocalMessage msg : messages) {
            messageProducer.retrySend(msg);
        }
    }
}