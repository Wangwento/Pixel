package com.wwt.pixel.infrastructure.ai;

import com.wwt.pixel.domain.model.GenerationRequest;
import com.wwt.pixel.domain.model.GenerationResult;

public interface ImageModelAdapter {

    ImageVendor getVendor();

    boolean isAvailable();

    GenerationResult generate(GenerationRequest request);

    default int getWeight() {
        return 1;
    }

    default int getTimeoutMs() {
        return 60000;
    }
}