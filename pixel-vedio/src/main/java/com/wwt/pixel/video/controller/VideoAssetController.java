package com.wwt.pixel.video.controller;

import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.video.domain.VideoAsset;
import com.wwt.pixel.video.service.VideoAssetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
public class VideoAssetController {

    private final VideoAssetService videoAssetService;

    @GetMapping("/assets")
    public Result<Map<String, Object>> listAssets(@RequestHeader("X-User-Id") Long userId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "24") int pageSize,
                                                  @RequestParam(required = false) Long folderId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate) {
        VideoAssetService.AssetPageResult result = videoAssetService.listAssets(
                userId, folderId, keyword, startDate, endDate, page, pageSize);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getList());
        data.put("total", result.getTotal());
        data.put("page", result.getPage());
        data.put("pageSize", result.getPageSize());
        return Result.success(data);
    }

    @PutMapping("/assets/{assetId}/title")
    public Result<VideoAsset> updateAssetTitle(@RequestHeader("X-User-Id") Long userId,
                                               @PathVariable Long assetId,
                                               @Valid @RequestBody AssetTitleRequest request) {
        return Result.success(videoAssetService.updateAssetTitle(userId, assetId, request.getTitle()));
    }

    @PostMapping("/assets/move")
    public Result<String> moveAssets(@RequestHeader("X-User-Id") Long userId,
                                     @Valid @RequestBody MoveAssetsRequest request) {
        videoAssetService.moveAssets(userId, request.getAssetIds(), request.getFolderId());
        return Result.success("视频移动成功", null);
    }

    @PostMapping("/assets/delete")
    public Result<String> deleteAssets(@RequestHeader("X-User-Id") Long userId,
                                       @Valid @RequestBody DeleteAssetsRequest request) {
        videoAssetService.deleteAssets(userId, request.getAssetIds());
        return Result.success("视频删除成功", null);
    }

    @Data
    public static class AssetTitleRequest {
        @NotBlank(message = "标题不能为空")
        private String title;
    }

    @Data
    public static class MoveAssetsRequest {
        @NotEmpty(message = "请选择要移动的视频")
        private List<Long> assetIds;
        private Long folderId;
    }

    @Data
    public static class DeleteAssetsRequest {
        @NotEmpty(message = "请选择要删除的视频")
        private List<Long> assetIds;
    }
}