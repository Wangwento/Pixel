package com.wwt.pixel.infrastructure.ai.adapter;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.domain.model.GenerationRequest;
import com.wwt.pixel.domain.model.GenerationResult;
import com.wwt.pixel.infrastructure.ai.ImageModelAdapter;
import com.wwt.pixel.infrastructure.ai.ImageVendor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@ConditionalOnProperty(name = "pixel.ai.openai.enabled", havingValue = "true", matchIfMissing = true)
public class OpenAiImageAdapter implements ImageModelAdapter {

    private final OpenAiImageModel openAiImageModel;

    @Value("${pixel.ai.openai.weight:1}")
    private int weight;

    @Value("${pixel.ai.openai.timeout:60000}")
    private int timeoutMs;

    public OpenAiImageAdapter(OpenAiImageModel openAiImageModel) {
        this.openAiImageModel = openAiImageModel;
    }

    @Override
    public ImageVendor getVendor() {
        return ImageVendor.OPENAI;
    }

    @Override
    public boolean isAvailable() {
        return openAiImageModel != null;
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            String finalPrompt = buildPrompt(request);
            log.debug("[OpenAI] 生成图片, prompt: {}", finalPrompt);

            OpenAiImageOptions options = OpenAiImageOptions.builder()
                    .withModel("dall-e-3")
                    .withQuality(request.getQuality())
                    .withN(1)
                    .withWidth(parseWidth(request.getSize()))
                    .withHeight(parseHeight(request.getSize()))
                    .build();

            ImagePrompt imagePrompt = new ImagePrompt(finalPrompt, options);
            ImageResponse response = openAiImageModel.call(imagePrompt);

            String imageUrl = response.getResult().getOutput().getUrl();
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[OpenAI] 图片生成成功, 耗时: {}ms", elapsed);

            return GenerationResult.builder()
                    .imageUrl(imageUrl)
                    .revisedPrompt(finalPrompt)
                    .vendor(getVendor().getCode())
                    .model("dall-e-3")
                    .generationTimeMs(elapsed)
                    .fromCache(false)
                    .build();

        } catch (Exception e) {
            log.error("[OpenAI] 图片生成失败", e);
            throw new BusinessException(1003, "OpenAI服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public int getWeight() {
        return weight;
    }

    @Override
    public int getTimeoutMs() {
        return timeoutMs;
    }

    private String buildPrompt(GenerationRequest request) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(request.getStyle())) {
            sb.append(request.getStyle()).append(" style, ");
        }
        sb.append(request.getPrompt());
        sb.append(", high quality, detailed");
        if (StringUtils.hasText(request.getNegativePrompt())) {
            sb.append(", avoid: ").append(request.getNegativePrompt());
        }
        return sb.toString();
    }

    private Integer parseWidth(String size) {
        if (!StringUtils.hasText(size)) return 1024;
        try {
            return Integer.parseInt(size.toLowerCase().split("x")[0]);
        } catch (Exception e) {
            return 1024;
        }
    }

    private Integer parseHeight(String size) {
        if (!StringUtils.hasText(size)) return 1024;
        String[] parts = size.toLowerCase().split("x");
        try {
            return parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        } catch (Exception e) {
            return 1024;
        }
    }
}