package com.wwt.pixel.video.ai;

import com.wwt.pixel.video.domain.VideoGenerationRequest;
import com.wwt.pixel.video.domain.VideoSubmitResult;
import com.wwt.pixel.video.domain.VideoTaskResult;

import java.math.BigDecimal;
import java.util.List;

public interface VideoModelAdapter {

    VideoVendor getVendor();

    default String getVendorCode() {
        return getVendor().getCode();
    }

    default String getVendorName() {
        return getVendor().getName();
    }

    boolean isAvailable();

    default boolean supportsTextToVideo() {
        return true;
    }

    boolean supportsImageInput();

    default boolean supportsHd() {
        return false;
    }

    default boolean supportsEnhancePrompt() {
        return false;
    }

    default boolean supportsUpsample() {
        return false;
    }

    default String getModelFamily() {
        return "generic";
    }

    default List<String> getSupportedAspectRatios() {
        return List.of("16:9", "9:16");
    }

    default List<String> getSupportedTextDurations() {
        return List.of();
    }

    default List<String> getSupportedImageDurations() {
        return List.of();
    }

    default int getMinImageCount() {
        return supportsImageInput() ? 1 : 0;
    }

    default int getMaxImageCount() {
        return supportsImageInput() ? 10 : 0;
    }

    default String getDefaultAspectRatio() {
        List<String> aspectRatios = getSupportedAspectRatios();
        return aspectRatios.isEmpty() ? null : aspectRatios.get(0);
    }

    default String getDefaultTextDuration() {
        List<String> durations = getSupportedTextDurations();
        return durations.isEmpty() ? null : durations.get(0);
    }

    default String getDefaultImageDuration() {
        List<String> durations = getSupportedImageDurations();
        return durations.isEmpty() ? null : durations.get(0);
    }

    default boolean isDefaultHdEnabled() {
        return false;
    }

    default boolean isDefaultEnhancePromptEnabled() {
        return false;
    }

    default boolean isDefaultUpsampleEnabled() {
        return false;
    }

    VideoSubmitResult submitTask(VideoGenerationRequest request);

    VideoTaskResult queryTask(String providerTaskId);

    default int getWeight() {
        return 1;
    }

    default int getTimeoutMs() {
        return 600000;
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

    default BigDecimal getCostPerSecond() {
        return BigDecimal.ZERO;
    }
}
