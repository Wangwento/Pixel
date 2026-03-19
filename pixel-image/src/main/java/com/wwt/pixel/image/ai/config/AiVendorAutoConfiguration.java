package com.wwt.pixel.image.ai.config;

import com.wwt.pixel.image.ai.ImageModelAdapter;
import com.wwt.pixel.image.ai.adapter.GeminiChatImageAdapter;
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

                ImageModelAdapter adapter = CompatibleImageAdapterFactory.createAdapter(vendor);

                if (adapter instanceof GeminiChatImageAdapter) {
                    log.info("注册Gemini Chat适配器: {} ({})", vendor.getName(), vendor.getCode());
                } else if (adapter.supportsImageInput()) {
                    log.info("注册支持图生图的兼容适配器: {} ({})", vendor.getName(), vendor.getCode());
                } else {
                    log.info("注册OpenAI兼容厂商: {} ({})", vendor.getName(), vendor.getCode());
                }

                adapters.add(adapter);
            }
        }

        return adapters;
    }
}
