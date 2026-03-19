package com.wwt.pixel.admin.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminRole {
    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
