package com.wwt.pixel.video.controller;

import com.wwt.pixel.common.constant.CommonConstant;
import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.video.ai.MultiVendorVideoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/video/admin/tasks")
@RequiredArgsConstructor
public class AdminVideoTaskController {

    private final MultiVendorVideoService multiVendorVideoService;

    @PostMapping("/recover")
    public Result<Map<String, Object>> recoverTask(
            @RequestHeader(value = CommonConstant.HEADER_ADMIN_ID, required = false) Long adminId,
            @Valid @RequestBody RecoverTaskRequest request) {
        return Result.success(multiVendorVideoService.recoverTask(request.getTaskQuery(), request.getVendorCode()));
    }

    @Data
    public static class RecoverTaskRequest {
        @NotBlank(message = "任务ID不能为空")
        private String taskQuery;
        private String vendorCode;
    }
}
