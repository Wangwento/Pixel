package com.wwt.pixel.image.mq;

/**
 * MQ Topic常量
 */
public class MQTopic {

    /**
     * 图片生成事件Topic
     */
    public static final String IMAGE_GENERATION = "pixel-image-generation";

    /**
     * 图片生成重试Topic
     */
    public static final String IMAGE_GENERATION_RETRY = "pixel-image-generation-retry";

    /**
     * 图片生成死信队列
     */
    public static final String IMAGE_GENERATION_DLQ = "pixel-image-generation-dlq";

    private MQTopic() {
    }
}