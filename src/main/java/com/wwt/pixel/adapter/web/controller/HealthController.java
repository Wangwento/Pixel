package com.wwt.pixel.adapter.web.controller;

import com.wwt.pixel.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${spring.application.name}")
    private String applicationName;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> info = new HashMap<>();
        info.put("application", applicationName);
        info.put("status", "UP");
        info.put("version", "1.0.0");
        info.put("timestamp", System.currentTimeMillis());
        return Result.success(info);
    }

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("pong");
    }
}