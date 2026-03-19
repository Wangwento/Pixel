package com.wwt.pixel.image.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
public class GenerationRequest {

    @NotBlank(message = "提示词不能为空")
    @Size(max = 5000, message = "提示词最长5000字符")
    private String prompt;

    @Size(max = 1000, message = "负面提示词最长1000字符")
    private String negativePrompt;

    private String style;

    private String sourceImageUrl;

    private String sourceImageBase64;

    private List<String> sourceImageUrls;

    private List<String> sourceImageBase64List;

    private String size = "1024x1024";

    private String quality = "standard";

    private String vendor;

    private String modelId;

    private String responseFormat = "url";

    private String aspectRatio;

    private String imageSize;

    private Map<String, Object> extraParams;

    public boolean hasSourceImages() {
        return !resolveSourceImageUrls().isEmpty() || !resolveSourceImageBase64List().isEmpty();
    }

    public List<String> resolveSourceImageUrls() {
        List<String> values = new ArrayList<>();
        if (sourceImageUrls != null) {
            sourceImageUrls.stream()
                    .filter(url -> url != null && !url.isBlank())
                    .forEach(values::add);
        }
        if (sourceImageUrl != null && !sourceImageUrl.isBlank()) {
            values.add(sourceImageUrl);
        }
        return Collections.unmodifiableList(values);
    }

    public List<String> resolveSourceImageBase64List() {
        List<String> values = new ArrayList<>();
        if (sourceImageBase64List != null) {
            sourceImageBase64List.stream()
                    .filter(base64 -> base64 != null && !base64.isBlank())
                    .forEach(values::add);
        }
        if (sourceImageBase64 != null && !sourceImageBase64.isBlank()) {
            values.add(sourceImageBase64);
        }
        return Collections.unmodifiableList(values);
    }

    public Map<String, Object> resolveExtraParams() {
        return extraParams == null ? Collections.emptyMap() : Collections.unmodifiableMap(extraParams);
    }
}
