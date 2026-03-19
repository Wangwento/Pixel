package com.wwt.pixel.admin.controller;

import com.wwt.pixel.admin.feign.VideoAdminServiceClient;
import com.wwt.pixel.common.constant.CommonConstant;
import com.wwt.pixel.common.dto.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/video/tasks")
@RequiredArgsConstructor
public class AdminVideoTaskController {

    private final VideoAdminServiceClient videoAdminServiceClient;

    @PostMapping("/recover")
    public Result<Map<String, Object>> recoverTask(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId,
                                                   @Valid @RequestBody RecoverTaskRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskQuery", request.getTaskQuery());
        payload.put("vendorCode", request.getVendorCode());
        return videoAdminServiceClient.recoverTask(adminId, payload);
    }

    @Data
    public static class RecoverTaskRequest {
        @NotBlank(message = "任务ID不能为空")
        private String taskQuery;
        private String vendorCode;
    }
}
