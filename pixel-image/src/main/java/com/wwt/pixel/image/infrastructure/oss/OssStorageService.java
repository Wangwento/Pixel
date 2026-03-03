package com.wwt.pixel.image.infrastructure.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
public class OssStorageService {

    private final OSS ossClient;
    private final OssProperties ossProperties;
    private final boolean enabled;

    @Autowired
    public OssStorageService(
            @Autowired(required = false) OSS ossClient,
            OssProperties ossProperties) {
        this.ossClient = ossClient;
        this.ossProperties = ossProperties;
        this.enabled = ossProperties.isEnabled() && ossClient != null;

        if (enabled) {
            log.info("OSS存储服务已启用");
        } else {
            log.info("OSS存储服务未启用");
        }
    }

    /**
     * 检查OSS是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 上传Base64图片到OSS
     *
     * @param base64Data Base64编码的图片数据（可包含data:image前缀）
     * @return 图片访问URL
     */
    public String uploadBase64Image(String base64Data) {
        if (!enabled) {
            log.debug("OSS未启用，跳过上传");
            return null;
        }

        try {
            // 解析Base64数据
            String pureBase64 = base64Data;
            String contentType = "image/png";

            if (base64Data.contains(",")) {
                String[] parts = base64Data.split(",", 2);
                if (parts[0].contains("image/jpeg")) {
                    contentType = "image/jpeg";
                } else if (parts[0].contains("image/webp")) {
                    contentType = "image/webp";
                } else if (parts[0].contains("image/gif")) {
                    contentType = "image/gif";
                }
                pureBase64 = parts[1];
            }

            // 清理Base64字符串（移除换行符、空格等非法字符）
            pureBase64 = pureBase64.replaceAll("\\s+", "")
                    .replaceAll("[^A-Za-z0-9+/=]", "");

            // 修复Base64 padding
            int padding = pureBase64.length() % 4;
            if (padding > 0) {
                pureBase64 += "=".repeat(4 - padding);
            }

            byte[] imageBytes = Base64.getDecoder().decode(pureBase64);
            return uploadBytes(imageBytes, contentType, getFileExtension(contentType));

        } catch (Exception e) {
            log.error("Base64图片上传失败", e);
            return null;
        }
    }

    /**
     * 从URL下载图片并上传到OSS
     *
     * @param imageUrl 图片URL
     * @return OSS访问URL
     */
    public String uploadFromUrl(String imageUrl) {
        if (!enabled) {
            log.debug("OSS未启用，跳过上传");
            return null;
        }

        try {
            URL url = new URL(imageUrl);
            try (InputStream inputStream = url.openStream()) {
                byte[] imageBytes = inputStream.readAllBytes();
                String contentType = guessContentType(imageUrl);
                return uploadBytes(imageBytes, contentType, getFileExtension(contentType));
            }
        } catch (Exception e) {
            log.error("从URL上传图片失败: {}", imageUrl, e);
            return null;
        }
    }

    /**
     * 上传字节数组到OSS
     */
    private String uploadBytes(byte[] data, String contentType, String extension) {
        String objectKey = generateObjectKey(extension);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(data.length);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        PutObjectRequest putRequest = new PutObjectRequest(
                ossProperties.getBucketName(),
                objectKey,
                inputStream,
                metadata
        );

        ossClient.putObject(putRequest);
        String accessUrl = buildAccessUrl(objectKey);
        log.info("图片上传成功: {}", accessUrl);

        return accessUrl;
    }

    /**
     * 生成对象存储Key
     * 格式: {prefix}{date}/{uuid}.{ext}
     */
    private String generateObjectKey(String extension) {
        String prefix = ossProperties.getPathPrefix();
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        return prefix + datePath + "/" + filename;
    }

    /**
     * 构建访问URL
     */
    private String buildAccessUrl(String objectKey) {
        if (StringUtils.hasText(ossProperties.getCustomDomain())) {
            String domain = ossProperties.getCustomDomain();
            if (!domain.startsWith("http")) {
                domain = "https://" + domain;
            }
            if (!domain.endsWith("/")) {
                domain += "/";
            }
            return domain + objectKey;
        }

        // 默认使用bucket域名
        String endpoint = ossProperties.getEndpoint();
        String bucket = ossProperties.getBucketName();

        // 移除endpoint的协议前缀
        String host = endpoint.replace("https://", "").replace("http://", "");

        return String.format("https://%s.%s/%s", bucket, host, objectKey);
    }

    private String guessContentType(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".jpg") || lower.contains(".jpeg")) {
            return "image/jpeg";
        } else if (lower.contains(".webp")) {
            return "image/webp";
        } else if (lower.contains(".gif")) {
            return "image/gif";
        }
        return "image/png";
    }

    private String getFileExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }
}