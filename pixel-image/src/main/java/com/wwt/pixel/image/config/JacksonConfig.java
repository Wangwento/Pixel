package com.wwt.pixel.image.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * 放宽 Jackson 字符串长度限制，AI厂商返回的 base64 图片可能超过默认 20MB 限制
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonStreamConstraintsCustomizer() {
        return builder -> builder.postConfigurer(objectMapper ->
                objectMapper.getFactory().setStreamReadConstraints(
                        StreamReadConstraints.builder()
                                .maxStringLength(50_000_000) // 50MB
                                .build()
                )
        );
    }
}