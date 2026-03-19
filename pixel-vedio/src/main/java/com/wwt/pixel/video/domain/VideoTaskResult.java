package com.wwt.pixel.video.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoTaskResult {
    private String taskId;
    private String providerTaskId;
    private String taskStatus;
    private String progress;
    private String videoUrl;
    private String coverUrl;
    private String ossVideoUrl;
    private String ossCoverUrl;
    private String vendor;
    private String model;
    private String failReason;
    private Long submitTime;
    private Long startTime;
    private Long finishTime;
}
