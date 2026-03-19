package com.wwt.pixel.image.controller;

import com.wwt.pixel.common.constant.CommonConstant;
import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.image.domain.HotImage;
import com.wwt.pixel.image.service.HotImageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 热门图片控制器
 */
@RestController
@RequestMapping("/api/image/hot")
@RequiredArgsConstructor
public class HotImageController {

    private final HotImageService hotImageService;

    // ==================== 用户端 ====================

    /**
     * 提交图片到热门
     */
    @PostMapping("/submit")
    public Result<HotImage> submit(@RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
                                   @Valid @RequestBody SubmitRequest request) {
        HotImage hotImage = hotImageService.submit(userId, request.getImageAssetId(), request.getDescription());
        return Result.success(hotImage);
    }

    /**
     * 我的提交记录
     */
    @GetMapping("/my")
    public Result<Map<String, Object>> mySubmissions(@RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int pageSize) {
        HotImageService.PageResult result = hotImageService.mySubmissions(userId, page, pageSize);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getList());
        data.put("total", result.getTotal());
        data.put("page", result.getPage());
        data.put("pageSize", result.getPageSize());
        return Result.success(data);
    }

    /**
     * 热门图片通知数据
     */
    @GetMapping("/notifications")
    public Result<List<HotImageService.HotImageNotification>> getNotifications(
            @RequestHeader(CommonConstant.HEADER_USER_ID) Long userId) {
        return Result.success(hotImageService.getNotifications(userId));
    }

    /**
     * 领取热门图片奖励
     */
    @PostMapping("/claim/{id}")
    public Result<Map<String, Object>> claimReward(@RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
                                                    @PathVariable Long id) {
        Map<String, Object> result = hotImageService.claimReward(id, userId);
        return Result.success(result);
    }

    // ==================== 公共端 (无需登录) ====================

    /**
     * 热门图片列表(已通过，分页)
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> listApproved(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        HotImageService.PageResult result = hotImageService.listApproved(page, pageSize);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getList());
        data.put("total", result.getTotal());
        data.put("page", result.getPage());
        data.put("pageSize", result.getPageSize());
        return Result.success(data);
    }

    // ==================== 管理端 ====================

    /**
     * 管理员审核列表
     */
    @GetMapping("/admin/list")
    public Result<Map<String, Object>> adminList(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        HotImageService.PageResult result = hotImageService.adminList(status, page, pageSize);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getList());
        data.put("total", result.getTotal());
        data.put("page", result.getPage());
        data.put("pageSize", result.getPageSize());
        return Result.success(data);
    }

    /**
     * 管理员通过
     */
    @PutMapping("/admin/{id}/approve")
    public Result<String> approve(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId,
                                  @PathVariable Long id) {
        hotImageService.approve(id, adminId);
        return Result.success("审核通过", null);
    }

    /**
     * 管理员拒绝
     */
    @PutMapping("/admin/{id}/reject")
    public Result<String> reject(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId,
                                 @PathVariable Long id,
                                 @Valid @RequestBody RejectRequest request) {
        hotImageService.reject(id, adminId, request.getRejectReason());
        return Result.success("已拒绝", null);
    }

    /**
     * 管理员下架
     */
    @PutMapping("/admin/{id}/offline")
    public Result<String> offline(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId,
                                  @PathVariable Long id) {
        hotImageService.offline(id, adminId);
        return Result.success("已下架", null);
    }

    /**
     * 管理员彻底删除
     */
    @DeleteMapping("/admin/{id}")
    public Result<String> delete(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId,
                                 @PathVariable Long id) {
        hotImageService.delete(id, adminId);
        return Result.success("删除成功", null);
    }

    @Data
    public static class SubmitRequest {
        @NotNull(message = "请选择图片")
        private Long imageAssetId;
        @Size(max = 500, message = "描述不能超过500个字符")
        private String description;
    }

    @Data
    public static class RejectRequest {
        @NotNull(message = "请填写拒绝原因")
        @Size(min = 1, max = 200, message = "拒绝原因不能超过200个字符")
        private String rejectReason;
    }
}
