package com.wwt.pixel.adapter.web.controller;

import com.wwt.pixel.application.service.TagRecommendService;
import com.wwt.pixel.common.Result;
import com.wwt.pixel.domain.model.Tag;
import com.wwt.pixel.domain.model.UserTag;
import com.wwt.pixel.infrastructure.security.UserContext;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 标签控制器
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagRecommendService tagRecommendService;

    /**
     * 获取所有标签(按分类)
     */
    @GetMapping
    public Result<Map<String, List<Tag>>> getAllTags() {
        return Result.success(tagRecommendService.getAllTagsGrouped());
    }

    /**
     * 获取分类标签
     */
    @GetMapping("/category/{category}")
    public Result<List<Tag>> getTagsByCategory(@PathVariable String category) {
        return Result.success(tagRecommendService.getTagsByCategory(category));
    }

    /**
     * 设置用户偏好标签
     */
    @PostMapping("/preferences")
    public Result<Void> setUserPreferences(@Valid @RequestBody PreferencesRequest request) {
        Long userId = UserContext.requireCurrentUserId();
        tagRecommendService.setUserPreferences(userId, request.getTagIds());
        return Result.success();
    }

    /**
     * 获取用户标签
     */
    @GetMapping("/my")
    public Result<List<UserTag>> getMyTags() {
        Long userId = UserContext.requireCurrentUserId();
        return Result.success(tagRecommendService.getUserTags(userId));
    }

    /**
     * 获取推荐风格
     */
    @GetMapping("/recommend/styles")
    public Result<List<String>> getRecommendedStyles(
            @RequestParam(defaultValue = "5") int limit) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            // 未登录返回默认推荐
            return Result.success(List.of("anime", "cyberpunk", "realistic"));
        }
        return Result.success(tagRecommendService.getRecommendedStyles(userId, limit));
    }

    // ========== 请求DTO ==========

    @Data
    public static class PreferencesRequest {
        private List<Long> tagIds;
    }
}