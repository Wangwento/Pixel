package com.wwt.pixel.application.service;

import com.wwt.pixel.domain.model.GenerationRequest;
import com.wwt.pixel.domain.model.GenerationResult;
import com.wwt.pixel.domain.service.ImageGenerationService;
import com.wwt.pixel.infrastructure.oss.OssStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarGenerationAppService {

    private final ImageGenerationService imageGenerationService;
    private final OssStorageService ossStorageService;

    private final Map<String, GenerationResult> localCache = new ConcurrentHashMap<>();

    public GenerationResult generateAvatar(GenerationRequest request) {
        String cacheKey = buildCacheKey(request);

        GenerationResult cached = localCache.get(cacheKey);
        if (cached != null) {
            log.info("命中本地缓存, key: {}", cacheKey);
            return GenerationResult.builder()
                    .imageUrl(cached.getImageUrl())
                    .imageBase64(cached.getImageBase64())
                    .ossUrl(cached.getOssUrl())
                    .revisedPrompt(cached.getRevisedPrompt())
                    .vendor(cached.getVendor())
                    .model(cached.getModel())
                    .generationTimeMs(0L)
                    .fromCache(true)
                    .build();
        }

        GenerationResult result;
        if (StringUtils.hasText(request.getSourceImageUrl())
                || StringUtils.hasText(request.getSourceImageBase64())) {
            result = imageGenerationService.generateImageFromImage(request);
        } else {
            result = imageGenerationService.generateImage(request);
        }

        // 上传到OSS
        String ossUrl = uploadToOss(result);
        if (StringUtils.hasText(ossUrl)) {
            result = GenerationResult.builder()
                    .imageUrl(result.getImageUrl())
                    .imageBase64(result.getImageBase64())
                    .ossUrl(ossUrl)
                    .revisedPrompt(result.getRevisedPrompt())
                    .vendor(result.getVendor())
                    .model(result.getModel())
                    .generationTimeMs(result.getGenerationTimeMs())
                    .fromCache(false)
                    .build();
        }

        localCache.put(cacheKey, result);

        return result;
    }

    /**
     * 上传图片到OSS
     */
    private String uploadToOss(GenerationResult result) {
        if (!ossStorageService.isEnabled()) {
            return null;
        }

        try {
            // 优先使用Base64数据
            if (StringUtils.hasText(result.getImageBase64())) {
                return ossStorageService.uploadBase64Image(result.getImageBase64());
            }
            // 其次从URL下载上传
            if (StringUtils.hasText(result.getImageUrl())) {
                return ossStorageService.uploadFromUrl(result.getImageUrl());
            }
        } catch (Exception e) {
            log.error("上传图片到OSS失败", e);
        }
        return null;
    }

    private String buildCacheKey(GenerationRequest request) {
        String content = request.getPrompt()
                + "|" + (request.getStyle() != null ? request.getStyle() : "")
                + "|" + (request.getNegativePrompt() != null ? request.getNegativePrompt() : "")
                + "|" + request.getSize()
                + "|" + request.getQuality();

        return "avatar:" + DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
    }
}
