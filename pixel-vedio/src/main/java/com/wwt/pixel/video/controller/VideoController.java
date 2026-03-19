package com.wwt.pixel.video.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.video.ai.MultiVendorVideoService;
import com.wwt.pixel.video.config.UserServiceClient;
import com.wwt.pixel.video.domain.VideoGenerationRequest;
import com.wwt.pixel.video.domain.VideoSubmitResult;
import com.wwt.pixel.video.domain.VideoTaskResult;
import com.wwt.pixel.video.dto.ModelInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 视频生成控制器
 */
@RestController
@RequestMapping("/api/video")
@Slf4j
@RequiredArgsConstructor
public class VideoController {

    private static final int MAX_REFERENCE_IMAGE_COUNT = 10;

    private final MultiVendorVideoService multiVendorVideoService;
    private final UserServiceClient userServiceClient;
    private final ObjectMapper objectMapper;

    @GetMapping("/models")
    public Result<List<ModelInfo>> getModels(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        int vipLevel = 0;
        if (userId != null) {
            try {
                Result<Integer> vipResult = userServiceClient.getVipLevel(userId);
                if (vipResult.getCode() == 200 && vipResult.getData() != null) {
                    vipLevel = vipResult.getData();
                }
            } catch (Exception e) {
                log.warn("获取视频模型 VIP 等级失败，默认按普通用户处理: {}", e.getMessage());
            }
        }
        return Result.success(multiVendorVideoService.getModelList(vipLevel));
    }

    @PostMapping("/text2video")
    public Result<VideoSubmitResult> text2video(@RequestHeader("X-User-Id") Long userId,
                                                @Valid @RequestBody Text2VideoRequest request) {
        VideoGenerationRequest generationRequest = new VideoGenerationRequest();
        fillCommonFields(generationRequest, request);
        generationRequest.setUserId(userId);
        return Result.success(multiVendorVideoService.submitTextTask(generationRequest));
    }

    @PostMapping(value = "/img2video", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<VideoSubmitResult> img2video(@RequestHeader("X-User-Id") Long userId,
                                               @Valid @RequestBody Image2VideoRequest request) {
        List<String> imageUrls = new ArrayList<>();
        List<String> imageBase64List = new ArrayList<>();
        splitImages(request.getImages(), imageUrls, imageBase64List);
        validateImageCount(imageUrls.size() + imageBase64List.size());

        VideoGenerationRequest generationRequest = new VideoGenerationRequest();
        fillCommonFields(generationRequest, request);
        generationRequest.setUserId(userId);
        generationRequest.setSourceImageUrls(imageUrls);
        generationRequest.setSourceImageBase64List(imageBase64List);
        return Result.success(multiVendorVideoService.submitImageTask(generationRequest));
    }

    @PostMapping(value = "/img2video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<VideoSubmitResult> img2video(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "vendor", required = false) String vendor,
            @RequestParam(value = "modelId", required = false) String modelId,
            @RequestParam(value = "aspectRatio", required = false) String aspectRatio,
            @RequestParam(value = "hd", required = false) Boolean hd,
            @RequestParam(value = "duration", required = false) String duration,
            @RequestParam(value = "enhancePrompt", required = false) Boolean enhancePrompt,
            @RequestParam(value = "enableUpsample", required = false) Boolean enableUpsample,
            @RequestParam(value = "notifyHook", required = false) String notifyHook,
            @RequestParam(value = "watermark", required = false) Boolean watermark,
            @RequestParam(value = "private", required = false) Boolean privateMode,
            @RequestParam(value = "params", required = false) String paramsJson,
            @RequestParam(value = "image", required = false) List<MultipartFile> images,
            @RequestParam(value = "imageUrl", required = false) List<String> imageUrls) {

        validatePrompt(prompt);
        int totalImageCount = safeSize(images) + safeSize(imageUrls);
        validateImageCount(totalImageCount);

        VideoGenerationRequest generationRequest = new VideoGenerationRequest();
        generationRequest.setUserId(userId);
        generationRequest.setPrompt(prompt);
        generationRequest.setVendor(vendor);
        generationRequest.setModelId(modelId);
        generationRequest.setAspectRatio(aspectRatio);
        generationRequest.setHd(hd);
        generationRequest.setDuration(duration);
        generationRequest.setEnhancePrompt(enhancePrompt);
        generationRequest.setEnableUpsample(enableUpsample);
        generationRequest.setNotifyHook(notifyHook);
        generationRequest.setWatermark(watermark);
        generationRequest.setPrivateMode(privateMode);
        Map<String, Object> params = parseParams(paramsJson);
        generationRequest.setExtraParams(params);
        applyDynamicParams(generationRequest, params);
        generationRequest.setSourceImageUrls(imageUrls == null ? Collections.emptyList() : imageUrls);
        generationRequest.setSourceImageBase64List(encodeImages(images));
        return Result.success(multiVendorVideoService.submitImageTask(generationRequest));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<VideoTaskResult> getTask(@PathVariable String taskId) {
        return Result.success(multiVendorVideoService.queryTask(taskId));
    }

    private void fillCommonFields(VideoGenerationRequest generationRequest, BaseVideoRequest request) {
        generationRequest.setPrompt(request.getPrompt());
        generationRequest.setVendor(request.getVendor());
        generationRequest.setModelId(request.getModelId());
        generationRequest.setAspectRatio(request.getAspectRatio());
        generationRequest.setHd(request.getHd());
        generationRequest.setDuration(request.getDuration());
        generationRequest.setEnhancePrompt(request.getEnhancePrompt());
        generationRequest.setEnableUpsample(request.getEnableUpsample());
        generationRequest.setNotifyHook(request.getNotifyHook());
        generationRequest.setWatermark(request.getWatermark());
        generationRequest.setPrivateMode(request.getPrivateMode());
        generationRequest.setExtraParams(request.getParams());
        applyDynamicParams(generationRequest, request.getParams());
    }

    private void applyDynamicParams(VideoGenerationRequest generationRequest, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        if (!StringUtils.hasText(generationRequest.getAspectRatio())) {
            generationRequest.setAspectRatio(firstString(params, "aspect_ratio", "aspectRatio"));
        }
        if (!StringUtils.hasText(generationRequest.getDuration())) {
            generationRequest.setDuration(firstString(params, "duration"));
        }
        if (generationRequest.getHd() == null) {
            generationRequest.setHd(firstBoolean(params, "hd"));
        }
        if (generationRequest.getEnhancePrompt() == null) {
            generationRequest.setEnhancePrompt(firstBoolean(params, "enhance_prompt", "enhancePrompt"));
        }
        if (generationRequest.getEnableUpsample() == null) {
            generationRequest.setEnableUpsample(firstBoolean(params, "enable_upsample", "enableUpsample"));
        }
        if (!StringUtils.hasText(generationRequest.getNotifyHook())) {
            generationRequest.setNotifyHook(firstString(params, "notify_hook", "notifyHook"));
        }
        if (generationRequest.getWatermark() == null) {
            generationRequest.setWatermark(firstBoolean(params, "watermark"));
        }
        if (generationRequest.getPrivateMode() == null) {
            generationRequest.setPrivateMode(firstBoolean(params, "private", "privateMode"));
        }
    }

    private Map<String, Object> parseParams(String paramsJson) {
        if (!StringUtils.hasText(paramsJson)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(paramsJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException("参数格式错误: params 必须是合法 JSON");
        }
    }

    private String firstString(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private Boolean firstBoolean(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return Boolean.parseBoolean(text);
            }
        }
        return null;
    }

    private void splitImages(List<String> images, List<String> imageUrls, List<String> imageBase64List) {
        if (CollectionUtils.isEmpty(images)) {
            return;
        }
        for (String image : images) {
            if (!StringUtils.hasText(image)) {
                continue;
            }
            if (isUrl(image)) {
                imageUrls.add(image);
            } else {
                imageBase64List.add(normalizeBase64(image));
            }
        }
    }

    private List<String> encodeImages(List<MultipartFile> images) {
        if (CollectionUtils.isEmpty(images)) {
            return Collections.emptyList();
        }
        List<String> results = new ArrayList<>();
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                continue;
            }
            try {
                results.add(Base64.getEncoder().encodeToString(image.getBytes()));
            } catch (IOException e) {
                throw new BusinessException(500, "读取参考图片失败: " + image.getOriginalFilename());
            }
        }
        return results;
    }

    private void validatePrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException("描述不能为空");
        }
        if (prompt.length() > 5000) {
            throw new BusinessException("描述最长5000字符");
        }
    }

    private void validateImageCount(int imageCount) {
        if (imageCount <= 0) {
            throw new BusinessException("请至少上传一张参考图片");
        }
        if (imageCount > MAX_REFERENCE_IMAGE_COUNT) {
            throw new BusinessException("参考图片最多上传10张");
        }
    }

    private int safeSize(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private boolean isUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private String normalizeBase64(String value) {
        int commaIndex = value.indexOf(',');
        if (commaIndex > -1 && value.substring(0, commaIndex).contains("base64")) {
            return value.substring(commaIndex + 1);
        }
        return value;
    }

    @Data
    public static class BaseVideoRequest {
        @NotBlank(message = "描述不能为空")
        @Size(max = 5000, message = "描述最长5000字符")
        private String prompt;
        private String vendor;
        private String modelId;
        private String aspectRatio;
        private Boolean hd;
        private String duration;
        private Boolean enhancePrompt;
        private Boolean enableUpsample;
        private String notifyHook;
        private Boolean watermark;
        private Map<String, Object> params;
        @JsonProperty("private")
        private Boolean privateMode;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Text2VideoRequest extends BaseVideoRequest {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Image2VideoRequest extends BaseVideoRequest {
        @NotEmpty(message = "请至少提供一张参考图片")
        @Size(max = MAX_REFERENCE_IMAGE_COUNT, message = "参考图片最多上传10张")
        private List<String> images = new ArrayList<>();
    }
}
