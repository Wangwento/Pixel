package com.wwt.pixel.domain.service;

import com.wwt.pixel.domain.model.GenerationRequest;
import com.wwt.pixel.domain.model.GenerationResult;

public interface ImageGenerationService {

    GenerationResult generateImage(GenerationRequest request);

    GenerationResult generateImageFromImage(GenerationRequest request);
}
