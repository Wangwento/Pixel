package com.wwt.pixel.admin.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiProvider {
    private Long id;
    private String providerCode;
    private String providerName;
    private String baseUrl;
    private Boolean enabled;
    private Integer weight;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}