package com.wwt.pixel.audio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.audio.controller.AudioController;
import com.wwt.pixel.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioDirectUploadService {

    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Value("${pixel.audio.oss.enabled:${pixel.oss.enabled:false}}")
    private boolean enabled;

    @Value("${pixel.audio.oss.endpoint:${pixel.oss.endpoint:}}")
    private String endpoint;

    @Value("${pixel.audio.oss.access-key-id:${pixel.oss.access-key-id:}}")
    private String accessKeyId;

    @Value("${pixel.audio.oss.access-key-secret:${pixel.oss.access-key-secret:}}")
    private String accessKeySecret;

    @Value("${pixel.audio.oss.bucket-name:${pixel.oss.bucket-name:}}")
    private String bucketName;

    @Value("${pixel.audio.oss.path-prefix:audio/generated/}")
    private String pathPrefix;

    @Value("${pixel.audio.oss.custom-domain:${pixel.oss.custom-domain:}}")
    private String customDomain;

    private final ObjectMapper objectMapper;

    public Map<String, Object> createDirectUploadPolicy(Long userId, AudioController.DirectUploadPolicyRequest request) {
        validateEnabled();
        String assetType = normalizeAssetType(request.getAssetType());
        String contentType = normalizeContentType(assetType, request.getContentType(), request.getFileName());
        String extension = resolveExtension(request.getFileName(), contentType);
        String objectKey = buildObjectKey(userId, request.getTaskId(), request.getClipId(), assetType, extension);
        Instant expiresAt = Instant.now().plusSeconds(600);

        Map<String, Object> policyDocument = new LinkedHashMap<>();
        policyDocument.put("expiration", DateTimeFormatter.ISO_INSTANT.format(expiresAt));
        policyDocument.put("conditions", List.of(
                List.of("eq", "$key", objectKey),
                List.of("content-length-range", 0, assetType.equals("audio") ? 104857600 : 10485760),
                Map.of("success_action_status", "200")
        ));

        try {
            String policyJson = objectMapper.writeValueAsString(policyDocument);
            String encodedPolicy = Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
            String signature = sign(encodedPolicy);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("host", buildUploadHost());
            result.put("bucket", bucketName);
            result.put("key", objectKey);
            result.put("accessKeyId", accessKeyId);
            result.put("policy", encodedPolicy);
            result.put("signature", signature);
            result.put("successActionStatus", "200");
            result.put("contentType", contentType);
            result.put("publicUrl", buildPublicUrl(objectKey));
            result.put("expiresAt", expiresAt.getEpochSecond());
            result.put("assetType", assetType);
            return result;
        } catch (Exception exception) {
            log.error("生成音频 OSS 直传凭证失败", exception);
            throw new BusinessException(500, "生成 OSS 直传凭证失败");
        }
    }

    private void validateEnabled() {
        if (!enabled
                || !StringUtils.hasText(endpoint)
                || !StringUtils.hasText(accessKeyId)
                || !StringUtils.hasText(accessKeySecret)
                || !StringUtils.hasText(bucketName)) {
            throw new BusinessException(500, "音频 OSS 直传未启用，请先补齐 pixel.audio.oss 或 pixel.oss 配置");
        }
    }

    private String buildUploadHost() {
        return "https://" + bucketName + "." + endpoint.replace("https://", "").replace("http://", "");
    }

    private String buildPublicUrl(String objectKey) {
        if (StringUtils.hasText(customDomain)) {
            String domain = customDomain.startsWith("http") ? customDomain : "https://" + customDomain;
            return domain.endsWith("/") ? domain + objectKey : domain + "/" + objectKey;
        }
        return buildUploadHost() + "/" + objectKey;
    }

    private String normalizeAssetType(String assetType) {
        String normalized = StringUtils.hasText(assetType) ? assetType.trim().toLowerCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "audio", "cover" -> normalized;
            default -> throw new BusinessException(400, "asset_type 仅支持 audio 或 cover");
        };
    }

    private String buildObjectKey(Long userId, String taskId, String clipId, String assetType, String extension) {
        String safePrefix = ensureTrailingSlash(StringUtils.hasText(pathPrefix) ? pathPrefix.trim() : "audio/generated/");
        String datePath = LocalDate.now(ZoneOffset.UTC).format(DATE_PATH_FORMATTER);
        return safePrefix
                + sanitizePath(String.valueOf(userId == null ? 0L : userId)) + "/"
                + datePath + "/"
                + sanitizePath(taskId) + "/"
                + sanitizePath(clipId) + "/"
                + assetType + "." + extension;
    }

    private String ensureTrailingSlash(String prefix) {
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private String sanitizePath(String value) {
        return (StringUtils.hasText(value) ? value.trim() : "unknown").replaceAll("[^a-zA-Z0-9/_-]", "_");
    }

    private String normalizeContentType(String assetType, String contentType, String fileName) {
        String normalized = StringUtils.hasText(contentType)
                ? contentType.trim().split(";", 2)[0].toLowerCase(Locale.ROOT)
                : "";
        if (StringUtils.hasText(normalized)) {
            if (assetType.equals("audio") && normalized.startsWith("audio/")) {
                return normalized;
            }
            if (assetType.equals("cover") && normalized.startsWith("image/")) {
                return normalized;
            }
        }
        String lowerFileName = StringUtils.hasText(fileName) ? fileName.toLowerCase(Locale.ROOT) : "";
        if (assetType.equals("audio")) {
            if (lowerFileName.endsWith(".wav")) {
                return "audio/wav";
            }
            if (lowerFileName.endsWith(".ogg")) {
                return "audio/ogg";
            }
            return "audio/mpeg";
        }
        if (lowerFileName.endsWith(".png")) {
            return "image/png";
        }
        if (lowerFileName.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private String resolveExtension(String fileName, String contentType) {
        if (StringUtils.hasText(fileName) && fileName.contains(".")) {
            String suffix = fileName.substring(fileName.lastIndexOf('.') + 1).trim().toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(suffix)) {
                return suffix;
            }
        }
        return switch (contentType) {
            case "audio/wav" -> "wav";
            case "audio/ogg" -> "ogg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> contentType.startsWith("audio/") ? "mp3" : "jpg";
        };
    }

    private String sign(String encodedPolicy) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] digest = mac.doFinal(encodedPolicy.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
}
