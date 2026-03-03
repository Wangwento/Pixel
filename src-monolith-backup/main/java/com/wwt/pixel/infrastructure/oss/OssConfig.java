package com.wwt.pixel.infrastructure.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pixel.oss.enabled", havingValue = "true")
public class OssConfig {

    private final OssProperties ossProperties;

    @Bean
    public OSS ossClient() {
        log.info("初始化阿里云OSS客户端, endpoint: {}, bucket: {}",
                ossProperties.getEndpoint(), ossProperties.getBucketName());
        return new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
        );
    }
}