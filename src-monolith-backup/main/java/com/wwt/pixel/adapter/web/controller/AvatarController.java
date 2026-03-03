package com.wwt.pixel.adapter.web.controller;

import com.wwt.pixel.application.service.AvatarGenerationAppService;
import com.wwt.pixel.common.Result;
import com.wwt.pixel.domain.model.GenerationRequest;
import com.wwt.pixel.domain.model.GenerationResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/avatar")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarGenerationAppService avatarGenerationAppService;

    @PostMapping("/generate")
    public Result<GenerationResult> generate(@Valid @RequestBody GenerationRequest request) {
        log.info("收到生成请求, prompt: {}, style: {}", request.getPrompt(), request.getStyle());
        GenerationResult result = avatarGenerationAppService.generateAvatar(request);
        return Result.success(result);
    }

    @PostMapping("/text2img")
    public Result<GenerationResult> text2img(@Valid @RequestBody GenerationRequest request) {
        log.info("文生图请求, prompt: {}", request.getPrompt());
        request.setSourceImageUrl(null);
        request.setSourceImageBase64(null);
        GenerationResult result = avatarGenerationAppService.generateAvatar(request);
        return Result.success(result);
    }

    @PostMapping("/img2img")
    public Result<GenerationResult> img2img(@Valid @RequestBody GenerationRequest request) {
        log.info("图生图请求, prompt: {}, hasSourceUrl: {}, hasSourceBase64: {}",
                request.getPrompt(),
                request.getSourceImageUrl() != null,
                request.getSourceImageBase64() != null);
        GenerationResult result = avatarGenerationAppService.generateAvatar(request);
        return Result.success(result);
    }
}
