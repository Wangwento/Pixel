package com.wwt.pixel.admin.feign;

import com.wwt.pixel.common.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "pixel-vedio", path = "/api/video/admin/tasks")
public interface VideoAdminServiceClient {

    @PostMapping("/recover")
    Result<Map<String, Object>> recoverTask(@RequestHeader("X-Admin-Id") Long adminId,
                                            @RequestBody Map<String, Object> request);
}
