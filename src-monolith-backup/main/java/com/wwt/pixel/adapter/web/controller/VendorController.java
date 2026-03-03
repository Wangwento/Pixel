package com.wwt.pixel.adapter.web.controller;

import com.wwt.pixel.common.Result;
import com.wwt.pixel.infrastructure.ai.ImageVendor;
import com.wwt.pixel.infrastructure.ai.MultiVendorImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vendor")
@RequiredArgsConstructor
public class VendorController {

    private final MultiVendorImageService multiVendorImageService;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> vendors = Arrays.stream(ImageVendor.values())
                .map(v -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("code", v.getCode());
                    map.put("name", v.getName());
                    map.put("enabled", v.isEnabled());
                    return map;
                })
                .collect(Collectors.toList());
        return Result.success(vendors);
    }

    @GetMapping("/available")
    public Result<List<String>> available() {
        return Result.success(multiVendorImageService.getAvailableVendors());
    }
}