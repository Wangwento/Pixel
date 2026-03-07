package com.wwt.pixel.image.controller;

import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.image.ai.MultiVendorImageService;
import com.wwt.pixel.image.domain.GenerationResult;
import com.wwt.pixel.image.dto.GenerationResponse;
import com.wwt.pixel.image.dto.ModelInfo;
import com.wwt.pixel.image.feign.UserServiceClient;
import com.wwt.pixel.image.service.AvatarGenerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

/**
 * 头像生成控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/avatar")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarGenerationService avatarGenerationService;
    private final MultiVendorImageService multiVendorImageService;
    private final UserServiceClient userServiceClient;

    /**
     * 获取可用模型列表（根据用户VIP等级标记可用性）
     */
    @GetMapping("/models")
    public Result<List<ModelInfo>> getModels(@RequestHeader("X-User-Id") Long userId) {
        int vipLevel = 0;
        try {
            Result<Integer> vipResult = userServiceClient.getVipLevel(userId);
            if (vipResult.getCode() == 200 && vipResult.getData() != null) {
                vipLevel = vipResult.getData();
            }
        } catch (Exception e) {
            log.warn("获取用户VIP等级失败，默认为0: {}", e.getMessage());
        }
        List<ModelInfo> models = multiVendorImageService.getModelList(vipLevel);
        return Result.success(models);
    }

    /**
     * 文生图（带幂等处理）
     */
    @PostMapping("/text2img")
    public Result<GenerationResponse> text2img(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody Text2ImgRequest request) {

        GenerationResult result = avatarGenerationService.generateAvatar(
                userId, request.getPrompt(), request.getStyle(), request.getModelId(),
                request.getAspectRatio(), request.getImageSize());
        return Result.success(toGenerationResponse(result));
    }

    /**
     * 图生图（参考图必传，支持最多10张）
     */
    @PostMapping(value = "/img2img", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GenerationResponse> img2img(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "style", required = false) String style,
            @RequestParam(value = "modelId", required = false) String modelId,
            @RequestParam(value = "responseFormat", required = false) String responseFormat,
            @RequestParam(value = "aspectRatio", required = false) String aspectRatio,
            @RequestParam(value = "imageSize", required = false) String imageSize,
            @RequestParam(value = "image", required = false) List<MultipartFile> images,
            @RequestParam(value = "imageUrl", required = false) List<String> imageUrls) {

        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException("描述不能为空");
        }
        int imageCount = (images == null ? 0 : images.size()) + (imageUrls == null ? 0 : imageUrls.size());
        if (imageCount == 0) {
            throw new BusinessException("请至少上传一张参考图片");
        }
        if (imageCount > 10) {
            throw new BusinessException("参考图片最多上传10张");
        }

        GenerationResult result = avatarGenerationService.generateAvatarFromImages(
                userId,
                prompt,
                style,
                modelId,
                images == null ? Collections.emptyList() : images,
                imageUrls == null ? Collections.emptyList() : imageUrls,
                responseFormat,
                aspectRatio,
                imageSize);
        return Result.success(toGenerationResponse(result));
    }

    private GenerationResponse toGenerationResponse(GenerationResult result) {
        if ("RUNNING".equals(result.getTaskStatus())) {
            return GenerationResponse.running(result.getTaskId());
        } else if ("SUCCESS".equals(result.getTaskStatus())) {
            return GenerationResponse.success(result);
        } else {
            return GenerationResponse.failed(result.getTaskId(), "生成失败");
        }
    }

    @Data
    public static class Text2ImgRequest {
        @NotBlank(message = "描述不能为空")
        private String prompt;
        private String style;
        private String modelId;
        private String aspectRatio;
        private String imageSize;
    }
}
