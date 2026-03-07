package com.wwt.pixel.image.ai;

import com.wwt.pixel.image.domain.GenerationRequest;
import com.wwt.pixel.image.domain.GenerationResult;

public interface ImageModelAdapter {

    ImageVendor getVendor();

    boolean isAvailable();

    GenerationResult generate(GenerationRequest request);

    default boolean supportsImageInput() {
        return false;
    }

    default int getWeight() {
        return 1;
    }

    default int getTimeoutMs() {
        return 60000;
    }

    default String getModelId() {
        return null;
    }

    default String getModelDisplayName() {
        return null;
    }

    default int getMinVipLevel() {
        return 0;
    }
}
