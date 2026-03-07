package com.wwt.pixel.image.ai.config;

import com.wwt.pixel.image.ai.ImageModelAdapter;
import com.wwt.pixel.image.ai.adapter.GeminiChatImageAdapter;
import com.wwt.pixel.image.ai.adapter.OpenAiCompatibleAdapter;
import com.wwt.pixel.image.ai.adapter.PlatoImageAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiVendorAutoConfiguration {

    private final AiVendorProperties properties;

    @Bean
    public List<ImageModelAdapter> compatibleImageAdapters() {
        List<ImageModelAdapter> adapters = new ArrayList<>();

        if (properties.getVendors() != null) {
            for (AiVendorProperties.CompatibleVendorConfig vendor : properties.getVendors()) {
                if (vendor == null) {
                    continue;
                }

                if (!vendor.isEnabled()) {
                    log.debug("跳过禁用的厂商: {}", vendor.getCode());
                    continue;
                }

                if (!StringUtils.hasText(vendor.getApiKey()) || !StringUtils.hasText(vendor.getBaseUrl())) {
                    log.debug("厂商 {} 缺少必要配置 (apiKey/baseUrl)，跳过", vendor.getCode());
                    continue;
                }

                if (!StringUtils.hasText(vendor.getCode())) {
                    log.warn("厂商配置缺少code，跳过");
                    continue;
                }

                String modelName = vendor.getModel() != null ? vendor.getModel() : "dall-e-3";
                ImageModelAdapter adapter;

                boolean supportsImageInput = vendor.isSupportsImageInput()
                        || modelName.toLowerCase().contains("nano-banana")
                        || "plato".equalsIgnoreCase(vendor.getCode());

                // Gemini 模型使用 Chat API 生成图片
                if (modelName.toLowerCase().contains("gemini")) {
                    adapter = new GeminiChatImageAdapter(
                            vendor.getCode(),
                            vendor.getName() != null ? vendor.getName() : vendor.getCode(),
                            vendor.getApiKey(),
                            vendor.getBaseUrl(),
                            modelName,
                            vendor.getWeight(),
                            vendor.getTimeout(),
                            vendor.getModelId(),
                            vendor.getModelDisplayName(),
                            vendor.getMinVipLevel()
                    );
                    log.info("注册Gemini Chat适配器: {} ({})", vendor.getName(), vendor.getCode());
                } else if (supportsImageInput) {
                    // 支持图生图的兼容厂商使用 /v1/images/edits + multipart/form-data
                    adapter = new PlatoImageAdapter(
                            vendor.getCode(),
                            vendor.getName() != null ? vendor.getName() : vendor.getCode(),
                            vendor.getApiKey(),
                            vendor.getBaseUrl(),
                            modelName,
                            vendor.getWeight(),
                            vendor.getTimeout(),
                            vendor.getModelId(),
                            vendor.getModelDisplayName(),
                            vendor.getMinVipLevel(),
                            vendor.getAspectRatio(),
                            vendor.getImageSize()
                    );
                    log.info("注册支持图生图的兼容适配器: {} ({})", vendor.getName(), vendor.getCode());
                } else {
                    adapter = new OpenAiCompatibleAdapter(
                            vendor.getCode(),
                            vendor.getName() != null ? vendor.getName() : vendor.getCode(),
                            vendor.getApiKey(),
                            vendor.getBaseUrl(),
                            modelName,
                            vendor.getWeight(),
                            vendor.getTimeout(),
                            vendor.getModelId(),
                            vendor.getModelDisplayName(),
                            vendor.getMinVipLevel()
                    );
                    log.info("注册OpenAI兼容厂商: {} ({})", vendor.getName(), vendor.getCode());
                }

                adapters.add(adapter);
            }
        }

        return adapters;
    }
}
