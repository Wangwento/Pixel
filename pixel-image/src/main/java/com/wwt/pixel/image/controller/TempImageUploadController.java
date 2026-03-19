package com.wwt.pixel.image.controller;

import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.image.infrastructure.oss.OssStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class TempImageUploadController {

    private final OssStorageService ossStorageService;

    /**
     * 上传临时图片（用于参数传递）
     */
    @PostMapping("/temp-image")
    public Result<String> uploadTempImage(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return Result.error(400, "文件不能为空");
            }

            // 验证文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return Result.error(400, "只支持图片文件");
            }

            // 验证文件大小（5MB）
            if (file.getSize() > 5 * 1024 * 1024) {
                return Result.error(400, "图片大小不能超过5MB");
            }

            // 上传到OSS临时目录
            String url = ossStorageService.uploadTempImage(file);
            log.info("临时图片上传成功: {}", url);

            return Result.success(url);
        } catch (Exception e) {
            log.error("临时图片上传失败", e);
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除临时图片
     */
    @DeleteMapping("/temp-image")
    public Result<Void> deleteTempImage(@RequestParam("url") String url) {
        try {
            ossStorageService.deleteTempImage(url);
            log.info("临时图片删除成功: {}", url);
            return Result.success(null);
        } catch (Exception e) {
            log.error("临时图片删除失败: {}", url, e);
            return Result.error(500, "删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除临时图片
     */
    @PostMapping("/cleanup-temp-images")
    public Result<Void> cleanupTempImages(@RequestBody List<String> urls) {
        try {
            if (urls == null || urls.isEmpty()) {
                return Result.success(null);
            }

            int successCount = 0;
            for (String url : urls) {
                try {
                    ossStorageService.deleteTempImage(url);
                    successCount++;
                } catch (Exception e) {
                    log.warn("删除临时图片失败: {}", url, e);
                }
            }

            log.info("批量删除临时图片完成: 成功{}/总数{}", successCount, urls.size());
            return Result.success(null);
        } catch (Exception e) {
            log.error("批量删除临时图片失败", e);
            return Result.error(500, "批量删除失败: " + e.getMessage());
        }
    }
}