package com.wwt.pixel.admin.controller;

import com.wwt.pixel.admin.service.AiConfigSyncService;
import com.wwt.pixel.common.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/config")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiConfigSyncService syncService;

    @PostMapping("/sync")
    public Result<Void> syncToNacos() {
        syncService.syncToNacos();
        return Result.success(null);
    }
}