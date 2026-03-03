package com.wwt.pixel.image.infrastructure.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pixel.oss")
public class OssProperties {

    /**
     * 是否启用OSS存储
     */
    private boolean enabled = false;

    /**
     * OSS访问端点
     */
    private String endpoint;

    /**
     * AccessKey ID
     */
    private String accessKeyId;

    /**
     * AccessKey Secret
     */
    private String accessKeySecret;

    /**
     * Bucket名称
     */
    private String bucketName;

    /**
     * 文件存储目录前缀
     */
    private String pathPrefix = "avatar/";

    /**
     * 自定义域名（CDN加速域名，可选）
     */
    private String customDomain;
}
