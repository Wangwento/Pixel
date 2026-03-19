package com.wwt.pixel.video.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
public class VideoGenerationRequest {

    private Long userId;

    @NotBlank(message = "提示词不能为空")
    @Size(max = 5000, message = "提示词最长5000字符")
    private String prompt;

    private String vendor;

    private String modelId;

    private String aspectRatio;

    private Boolean hd;

    private String duration;

    private String notifyHook;

    private Boolean watermark;

    private Boolean privateMode;

    private Boolean enhancePrompt;

    private Boolean enableUpsample;

    private List<String> sourceImageUrls;

    private List<String> sourceImageBase64List;

    private Map<String, Object> extraParams;

    public void setDuration(String duration) {
        this.duration = StringUtils.hasText(duration) ? duration.trim() : null;
    }

    public boolean hasSourceImages() {
        return !resolveSourceImages().isEmpty();
    }

    public List<String> resolveSourceImages() {
        List<String> values = new ArrayList<>();
        if (sourceImageUrls != null) {
            sourceImageUrls.stream()
                    .filter(url -> url != null && !url.isBlank())
                    .forEach(values::add);
        }
        if (sourceImageBase64List != null) {
            sourceImageBase64List.stream()
                    .filter(base64 -> base64 != null && !base64.isBlank())
                    .forEach(values::add);
        }
        return Collections.unmodifiableList(values);
    }

    public Map<String, Object> resolveExtraParams() {
        return extraParams == null ? Collections.emptyMap() : Collections.unmodifiableMap(extraParams);
    }
}
