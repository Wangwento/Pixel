package com.wwt.pixel.video.infrastructure.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 视频OSS存储服务：上传视频文件和封面图到阿里云OSS
 */
@Slf4j
@Service
public class VideoOssService {

    private final OSS ossClient;
    private final VideoOssProperties ossProperties;
    private final boolean enabled;

    @Autowired
    public VideoOssService(
            @Autowired(required = false) OSS ossClient,
            VideoOssProperties ossProperties) {
        this.ossClient = ossClient;
        this.ossProperties = ossProperties;
        this.enabled = ossProperties.isEnabled() && ossClient != null;
        if (enabled) {
            log.info("视频OSS存储服务已启用");
        } else {
            log.info("视频OSS存储服务未启用");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 从URL下载视频并上传到OSS
     *
     * @param videoUrl 视频URL
     * @return OSS访问URL，失败返回null
     */
    public String uploadVideoFromUrl(String videoUrl) {
        if (!enabled || !StringUtils.hasText(videoUrl)) {
            return null;
        }
        if (isCurrentOssUrl(videoUrl)) {
            return videoUrl;
        }
        try {
            URL url = new URL(videoUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(120000);
            conn.setRequestProperty("User-Agent", "PixelVideoService/1.0");
            try (InputStream inputStream = conn.getInputStream()) {
                byte[] videoBytes = inputStream.readAllBytes();
                String contentType = guessVideoContentType(videoUrl, conn.getContentType());
                String ext = getVideoExtension(contentType);
                String objectKey = generateObjectKey("video", ext);
                return uploadBytes(videoBytes, contentType, objectKey);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            log.error("视频上传OSS失败: {}", videoUrl, e);
            return null;
        }
    }

    /**
     * 从URL下载封面图并上传到OSS
     *
     * @param coverUrl 封面图URL
     * @return OSS访问URL，失败返回null
     */
    public String uploadCoverFromUrl(String coverUrl) {
        if (!enabled || !StringUtils.hasText(coverUrl)) {
            return null;
        }
        try {
            URL url = new URL(coverUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "PixelVideoService/1.0");
            try (InputStream inputStream = conn.getInputStream()) {
                byte[] imageBytes = inputStream.readAllBytes();
                String contentType = guessImageContentType(coverUrl, conn.getContentType());
                String ext = getImageExtension(contentType);
                String objectKey = generateObjectKey("cover", ext);
                return uploadBytes(imageBytes, contentType, objectKey);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            log.error("封面图上传OSS失败: {}", coverUrl, e);
            return null;
        }
    }

    public String uploadFirstFrameCoverFromVideo(String videoUrl) {
        if (!enabled || !StringUtils.hasText(videoUrl)) {
            return null;
        }
        String snapshotUrl = buildFirstFrameSnapshotUrl(videoUrl);
        return uploadCoverFromUrl(snapshotUrl);
    }

    public String buildFirstFrameSnapshotUrl(String videoUrl) {
        if (!StringUtils.hasText(videoUrl)) {
            return null;
        }
        String separator = videoUrl.contains("?") ? "&" : "?";
        return videoUrl + separator + "x-oss-process=video/snapshot,t_0,f_jpg,w_0,h_0";
    }

    public boolean isCurrentOssUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        if (StringUtils.hasText(ossProperties.getCustomDomain())) {
            String normalizedDomain = normalizeAccessPrefix(ossProperties.getCustomDomain());
            return url.startsWith(normalizedDomain);
        }
        if (!StringUtils.hasText(ossProperties.getEndpoint()) || !StringUtils.hasText(ossProperties.getBucketName())) {
            return false;
        }
        String host = ossProperties.getEndpoint()
                .replace("https://", "")
                .replace("http://", "");
        String expectedPrefix = "https://" + ossProperties.getBucketName() + "." + host + "/";
        return url.startsWith(expectedPrefix);
    }

    private String uploadBytes(byte[] data, String contentType, String objectKey) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(data.length);
        ByteArrayInputStream is = new ByteArrayInputStream(data);
        ossClient.putObject(new PutObjectRequest(
                ossProperties.getBucketName(), objectKey, is, metadata));
        String accessUrl = buildAccessUrl(objectKey);
        log.info("文件上传OSS成功: {} ({}KB)", accessUrl, data.length / 1024);
        return accessUrl;
    }

    private String generateObjectKey(String subDir, String extension) {
        String prefix = ossProperties.getPathPrefix();
        if (!StringUtils.hasText(prefix)) {
            prefix = "";
        } else if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        String datePath = LocalDate.now().format(
                DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = UUID.randomUUID().toString().replace("-", "")
                + "." + extension;
        return prefix + subDir + "/" + datePath + "/" + filename;
    }

    private String buildAccessUrl(String objectKey) {
        if (StringUtils.hasText(ossProperties.getCustomDomain())) {
            return normalizeAccessPrefix(ossProperties.getCustomDomain()) + objectKey;
        }
        String host = ossProperties.getEndpoint()
                .replace("https://", "").replace("http://", "");
        return String.format("https://%s.%s/%s",
                ossProperties.getBucketName(), host, objectKey);
    }

    private String guessVideoContentType(String url, String respType) {
        if (StringUtils.hasText(respType) && respType.startsWith("video/")) {
            return respType.split(";")[0].trim();
        }
        String lower = url.toLowerCase();
        if (lower.contains(".webm")) return "video/webm";
        if (lower.contains(".mov")) return "video/quicktime";
        return "video/mp4";
    }

    private String getVideoExtension(String contentType) {
        return switch (contentType) {
            case "video/webm" -> "webm";
            case "video/quicktime" -> "mov";
            default -> "mp4";
        };
    }

    private String guessImageContentType(String url, String respType) {
        if (StringUtils.hasText(respType) && respType.startsWith("image/")) {
            return respType.split(";")[0].trim();
        }
        if (url != null && url.contains("f_jpg")) {
            return "image/jpeg";
        }
        String lower = url.toLowerCase();
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "image/jpeg";
        if (lower.contains(".webp")) return "image/webp";
        return "image/png";
    }

    private String getImageExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }

    private String normalizeAccessPrefix(String domain) {
        String normalized = domain;
        if (!normalized.startsWith("http")) {
            normalized = "https://" + normalized;
        }
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }
}
