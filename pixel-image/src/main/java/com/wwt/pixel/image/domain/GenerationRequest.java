package com.wwt.pixel.image.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GenerationRequest {

    @NotBlank(message = "提示词不能为空")
    @Size(max = 2000, message = "提示词最长2000字符")
    private String prompt;

    @Size(max = 1000, message = "负面提示词最长1000字符")
    private String negativePrompt;

    private String style;

    private String sourceImageUrl;

    private String sourceImageBase64;

    private String size = "1024x1024";

    private String quality = "standard";

    private String vendor;
}
