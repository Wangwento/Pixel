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
            log.info("开始从URL流式转存图片: {}", imageUrl);
            URL url = new URL(imageUrl);
            var connection = url.openConnection();
            connection.setConnectTimeout(10000); // 连接超时10秒
            connection.setReadTimeout(60000); // 读取超时60秒（大图片需要更长时间）

            try (InputStream inputStream = connection.getInputStream()) {
                long contentLength = connection.getContentLengthLong();
                String contentType = normalizeContentType(connection.getContentType(), imageUrl);

                if (contentLength > 0) {
                    log.info("图片响应头获取成功，大小: {} KB, Content-Type: {}", contentLength / 1024, contentType);
                    return uploadStream(inputStream, contentLength, contentType, getFileExtension(contentType));
                }

                log.warn("图片响应未提供Content-Length，回退为内存上传: {}", imageUrl);
                byte[] imageBytes = inputStream.readAllBytes();
                log.info("图片下载完成，大小: {} KB", imageBytes.length / 1024);
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
     * 流式上传到OSS，避免整张图读入堆内存
     */
    private String uploadStream(InputStream inputStream, long contentLength, String contentType, String extension) {
        String objectKey = generateObjectKey(extension);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(contentLength);

        PutObjectRequest putRequest = new PutObjectRequest(
                ossProperties.getBucketName(),
                objectKey,
                inputStream,
                metadata
        );

        ossClient.putObject(putRequest);
        String accessUrl = buildAccessUrl(objectKey);
        log.info("图片流式上传成功: {}", accessUrl);

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

    private String normalizeContentType(String contentType, String imageUrl) {
        if (!StringUtils.hasText(contentType)) {
            return guessContentType(imageUrl);
        }

        String normalized = contentType.split(";", 2)[0].trim().toLowerCase();
        if (!normalized.startsWith("image/")) {
            return guessContentType(imageUrl);
        }
        return normalized;
    }

    private String getFileExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    /**
     * 上传临时图片（用于参数传递）
     */
    public String uploadTempImage(org.springframework.web.multipart.MultipartFile file) {
        if (!enabled) {
            throw new RuntimeException("OSS存储服务未启用");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf(".") + 1)
                    : "jpg";

            // 临时文件路径: temp/YYYY/MM/DD/uuid.ext
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String objectKey = "temp/" + datePath + "/" + UUID.randomUUID() + "." + extension;

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());

            ossClient.putObject(ossProperties.getBucketName(), objectKey, file.getInputStream(), metadata);
            return buildAccessUrl(objectKey);
        } catch (Exception e) {
            log.error("上传临时图片失败", e);
            throw new RuntimeException("上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除临时图片
     */
    public void deleteTempImage(String url) {
        if (!enabled || !StringUtils.hasText(url)) {
            return;
        }

        try {
            String objectKey = extractObjectKey(url);
            if (objectKey != null && objectKey.startsWith("temp/")) {
                ossClient.deleteObject(ossProperties.getBucketName(), objectKey);
                log.info("临时图片删除成功: {}", objectKey);
            }
        } catch (Exception e) {
            log.error("删除临时图片失败: {}", url, e);
        }
    }

    /**
     * 从URL提取objectKey
     */
    private String extractObjectKey(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        int idx = url.indexOf(ossProperties.getBucketName());
        if (idx > 0) {
            String path = url.substring(idx + ossProperties.getBucketName().length());
            return path.startsWith("/") ? path.substring(1) : path;
        }
        return null;
    }
}
