package com.wwt.pixel.admin.feign;

import com.wwt.pixel.common.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 热门图片服务 Feign 客户端 (调用 pixel-image)
 */
@FeignClient(name = "pixel-image", path = "/api/image/hot/admin")
public interface HotImageServiceClient {

    @GetMapping("/list")
    Result<Map<String, Object>> list(@RequestHeader("X-Admin-Id") Long adminId,
                                     @RequestParam(value = "status", required = false) Integer status,
                                     @RequestParam(value = "page", defaultValue = "1") int page,
                                     @RequestParam(value = "pageSize", defaultValue = "20") int pageSize);

    @PutMapping("/{id}/approve")
    Result<String> approve(@RequestHeader("X-Admin-Id") Long adminId,
                           @PathVariable("id") Long id);

    @PutMapping("/{id}/reject")
    Result<String> reject(@RequestHeader("X-Admin-Id") Long adminId,
                          @PathVariable("id") Long id,
                          @RequestBody Map<String, String> body);

    @PutMapping("/{id}/offline")
    Result<String> offline(@RequestHeader("X-Admin-Id") Long adminId,
                           @PathVariable("id") Long id);

    @DeleteMapping("/{id}")
    Result<String> delete(@RequestHeader("X-Admin-Id") Long adminId,
                          @PathVariable("id") Long id);
}
