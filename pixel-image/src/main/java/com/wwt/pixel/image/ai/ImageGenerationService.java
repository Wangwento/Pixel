package com.wwt.pixel.image.ai;

import com.wwt.pixel.image.domain.GenerationRequest;
import com.wwt.pixel.image.domain.GenerationResult;

/**
 * 图片生成服务接口
 */
public interface ImageGenerationService {

    /**
     * 文生图
     */
    GenerationResult generateImage(GenerationRequest request);

    /**
     * 图生图
     */
    GenerationResult generateImageFromImage(GenerationRequest request);
}
