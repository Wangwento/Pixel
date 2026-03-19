package com.wwt.pixel.image.controller;

import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.image.domain.AssetFolder;
import com.wwt.pixel.image.domain.ImageAsset;
import com.wwt.pixel.image.service.AssetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片资产控制器
 */
@RestController
@RequestMapping("/api/image")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping("/folders")
    public Result<List<AssetFolder>> listFolders(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(assetService.listFolders(userId));
    }

    @PostMapping("/folders")
    public Result<AssetFolder> createFolder(@RequestHeader("X-User-Id") Long userId,
                                            @Valid @RequestBody FolderRequest request) {
        return Result.success(assetService.createFolder(userId, request.getFolderName(), request.getParentId()));
    }

    @PutMapping("/folders/{folderId}")
    public Result<String> renameFolder(@RequestHeader("X-User-Id") Long userId,
                                       @PathVariable Long folderId,
                                       @Valid @RequestBody FolderRenameRequest request) {
        assetService.renameFolder(userId, folderId, request.getFolderName());
        return Result.success("文件夹重命名成功", null);
    }

    @DeleteMapping("/folders/{folderId}")
    public Result<String> deleteFolder(@RequestHeader("X-User-Id") Long userId,
                                       @PathVariable Long folderId) {
        assetService.deleteFolder(userId, folderId);
        return Result.success("文件夹删除成功", null);
    }

    @GetMapping("/assets")
    public Result<Map<String, Object>> listAssets(@RequestHeader("X-User-Id") Long userId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "24") int pageSize,
                                                  @RequestParam(required = false) Long folderId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate) {
        AssetService.AssetPageResult result = assetService.listAssets(
                userId, folderId, keyword, startDate, endDate, page, pageSize);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getList());
        data.put("total", result.getTotal());
        data.put("page", result.getPage());
        data.put("pageSize", result.getPageSize());
        return Result.success(data);
    }

    @PutMapping("/assets/{assetId}/title")
    public Result<ImageAsset> updateAssetTitle(@RequestHeader("X-User-Id") Long userId,
                                               @PathVariable Long assetId,
                                               @Valid @RequestBody AssetTitleRequest request) {
        return Result.success(assetService.updateAssetTitle(userId, assetId, request.getTitle()));
    }

    @PostMapping("/assets/move")
    public Result<String> moveAssets(@RequestHeader("X-User-Id") Long userId,
                                     @Valid @RequestBody MoveAssetsRequest request) {
        assetService.moveAssets(userId, request.getAssetIds(), request.getFolderId());
        return Result.success("图片移动成功", null);
    }

    @PostMapping("/assets/delete")
    public Result<String> deleteAssets(@RequestHeader("X-User-Id") Long userId,
                                       @Valid @RequestBody DeleteAssetsRequest request) {
        assetService.deleteAssets(userId, request.getAssetIds());
        return Result.success("图片删除成功", null);
    }

    @Data
    public static class FolderRequest {
        @NotBlank(message = "文件夹名称不能为空")
        private String folderName;
        private Long parentId;
    }

    @Data
    public static class FolderRenameRequest {
        @NotBlank(message = "文件夹名称不能为空")
        private String folderName;
    }

    @Data
    public static class AssetTitleRequest {
        @NotBlank(message = "标题不能为空")
        private String title;
    }

    @Data
    public static class MoveAssetsRequest {
        @NotEmpty(message = "请选择要移动的图片")
        private List<Long> assetIds;
        private Long folderId;
    }

    @Data
    public static class DeleteAssetsRequest {
        @NotEmpty(message = "请选择要删除的图片")
        private List<Long> assetIds;
    }
}
