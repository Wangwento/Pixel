package com.wwt.pixel.audio.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class AudioConfig {

    private final AudioProviderProperties properties;

    @Bean
    public RestTemplate audioRestTemplate(RestTemplateBuilder builder) {
        int timeout = Math.max(properties.getTimeout(), 10_000);
        return builder
                .setConnectTimeout(Duration.ofMillis(timeout))
                .setReadTimeout(Duration.ofMillis(timeout))
                .build();
    }
}
