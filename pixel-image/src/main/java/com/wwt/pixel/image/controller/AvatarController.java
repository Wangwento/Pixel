package com.wwt.pixel.image.controller;

import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.image.domain.GenerationResult;
import com.wwt.pixel.image.service.AvatarGenerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 头像生成控制器
 */
@RestController
@RequestMapping("/api/avatar")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarGenerationService avatarGenerationService;

    /**
     * 文生图
     */
    @PostMapping("/text2img")
    public Result<GenerationResult> text2img(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody Text2ImgRequest request) {
        GenerationResult result = avatarGenerationService.generateAvatar(
                userId, request.getPrompt(), request.getStyle());
        return Result.success(result);
    }

    @Data
    public static class Text2ImgRequest {
        @NotBlank(message = "描述不能为空")
        private String prompt;
        private String style;  // 可选的风格
    }
}
