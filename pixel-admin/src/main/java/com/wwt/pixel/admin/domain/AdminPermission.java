package com.wwt.pixel.admin.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminPermission {
    private Long id;
    private String permissionCode;
    private String permissionName;
    private String module;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
