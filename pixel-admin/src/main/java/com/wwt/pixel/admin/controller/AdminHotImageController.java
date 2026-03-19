package com.wwt.pixel.admin.controller;

import com.wwt.pixel.admin.feign.HotImageServiceClient;
import com.wwt.pixel.common.constant.CommonConstant;
import com.wwt.pixel.common.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端 - 热门图片审核控制器
 * 薄代理层，通过 Feign 调用 pixel-image 服务
 */
@RestController
@RequestMapping("/api/admin/hot-image")
@RequiredArgsConstructor
public class AdminHotImageController {

    private final HotImageServiceClient hotImageServiceClient;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        return hotImageServiceClient.list(adminId, status, page, pageSize);
    }

    @PutMapping("/{id}/approve")
    public Result<String> approve(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId,
                                  @PathVariable Long id) {
        return hotImageServiceClient.approve(adminId, id);
    }

    @PutMapping("/{id}/reject")
    public Result<String> reject(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId,
                                 @PathVariable Long id,
                                 @RequestBody Map<String, String> body) {
        return hotImageServiceClient.reject(adminId, id, body);
    }

    @PutMapping("/{id}/offline")
    public Result<String> offline(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId,
                                  @PathVariable Long id) {
        return hotImageServiceClient.offline(adminId, id);
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId,
                                 @PathVariable Long id) {
        return hotImageServiceClient.delete(adminId, id);
    }
}
