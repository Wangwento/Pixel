package com.wwt.pixel.video.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoSubmitResult {
    private String taskId;
    private String providerTaskId;
    private String taskStatus;
    private String vendor;
    private String model;
    private String message;
}
