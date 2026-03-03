package com.wwt.pixel.application.service;

import com.wwt.pixel.domain.model.*;
import com.wwt.pixel.infrastructure.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 标签推荐服务
 *
 * TODO: 后期可优化的方向
 * 1. 使用Redis缓存用户标签
 * 2. 引入协同过滤算法找相似用户
 * 3. 使用向量数据库做语义相似推荐
 * 4. 定时任务批量更新用户行为标签
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagRecommendService {

    private final TagMapper tagMapper;
    private final UserTagMapper userTagMapper;
    private final UserBehaviorMapper userBehaviorMapper;

    // ==================== 标签管理 ====================

    /**
     * 获取所有标签(按分类)
     */
    public Map<String, List<Tag>> getAllTagsGrouped() {
        List<Tag> tags = tagMapper.findAll();
        return tags.stream().collect(Collectors.groupingBy(Tag::getCategory));
    }

    /**
     * 获取分类标签
     */
    public List<Tag> getTagsByCategory(String category) {
        return tagMapper.findByCategory(category);
    }

    // ==================== 用户标签 ====================

    /**
     * 用户选择偏好标签
     */
    @Transactional
    public void setUserPreferences(Long userId, List<Long> tagIds) {
        // 删除旧的用户选择标签
        userTagMapper.deleteBySource(userId, 1);

        // 添加新标签
        for (Long tagId : tagIds) {
            UserTag userTag = new UserTag();
            userTag.setUserId(userId);
            userTag.setTagId(tagId);
            userTag.setWeight(new BigDecimal("30.00"));  // 用户选择基础权重30
            userTag.setSource(1);  // 用户选择
            userTag.setUseCount(0);
            userTagMapper.insert(userTag);
        }
        log.info("用户设置偏好标签: userId={}, tags={}", userId, tagIds);
    }

    /**
     * 获取用户标签(带权重)
     */
    public List<UserTag> getUserTags(Long userId) {
        return userTagMapper.findByUserId(userId);
    }

    /**
     * 获取用户Top N标签
     */
    public List<UserTag> getTopUserTags(Long userId, int limit) {
        return userTagMapper.findTopByUserId(userId, limit);
    }

    // ==================== 行为记录与分析 ====================

    /**
     * 记录用户行为
     */
    @Transactional
    public void recordBehavior(Long userId, String behaviorType, String targetType,
                               String targetId, Map<String, Object> extraData) {
        UserBehavior behavior = UserBehavior.builder()
                .userId(userId)
                .behaviorType(behaviorType)
                .targetType(targetType)
                .targetId(targetId)
                .extraData(extraData)
                .build();
        userBehaviorMapper.insert(behavior);

        // 如果是生成行为，更新用户标签权重
        if (UserBehavior.BehaviorType.GENERATE.equals(behaviorType)) {
            updateUserTagsFromBehavior(userId, extraData);
        }
    }

    /**
     * 根据行为更新用户标签
     */
    @Transactional
    public void updateUserTagsFromBehavior(Long userId, Map<String, Object> extraData) {
        if (extraData == null) return;

        // 从extraData中提取style
        String style = (String) extraData.get("style");
        if (style == null) return;

        // 查找对应标签
        Tag tag = tagMapper.findByNameEn(style);
        if (tag == null) return;

        // 更新或创建用户标签
        UserTag userTag = userTagMapper.findByUserIdAndTagId(userId, tag.getId());
        if (userTag == null) {
            userTag = new UserTag();
            userTag.setUserId(userId);
            userTag.setTagId(tag.getId());
            userTag.setWeight(new BigDecimal("10.00"));  // 行为标签基础权重10
            userTag.setSource(2);  // 行为分析
            userTag.setUseCount(1);
            userTag.setLastUseTime(LocalDateTime.now());
            userTagMapper.insert(userTag);
        } else {
            userTag.incrementUse();
            userTagMapper.update(userTag);
        }
    }

    // ==================== 推荐算法 ====================

    /**
     * 获取推荐风格模板
     * 基于用户标签权重推荐
     */
    public List<String> getRecommendedStyles(Long userId, int limit) {
        List<UserTag> userTags = userTagMapper.findTopByUserId(userId, limit * 2);

        if (userTags.isEmpty()) {
            // 新用户返回热门风格
            return getDefaultStyles(limit);
        }

        // 筛选style类型的标签
        return userTags.stream()
                .filter(ut -> ut.getTag() != null && "style".equals(ut.getTag().getCategory()))
                .limit(limit)
                .map(ut -> ut.getTag().getNameEn())
                .collect(Collectors.toList());
    }

    /**
     * 基于用户偏好生成推荐prompt增强
     */
    public String enhancePromptWithUserPreference(Long userId, String originalPrompt) {
        List<UserTag> topTags = userTagMapper.findTopByUserId(userId, 3);

        if (topTags.isEmpty()) {
            return originalPrompt;
        }

        // 提取用户偏好关键词
        String preferences = topTags.stream()
                .filter(ut -> ut.getTag() != null)
                .map(ut -> ut.getTag().getNameEn())
                .collect(Collectors.joining(", "));

        // TODO: 可以用LLM来智能融合prompt
        return originalPrompt + ", style preference: " + preferences;
    }

    /**
     * 获取默认热门风格
     */
    private List<String> getDefaultStyles(int limit) {
        // TODO: 可以基于全站统计热门风格
        return List.of("anime", "cyberpunk", "realistic", "oil-painting", "minimalist")
                .subList(0, Math.min(limit, 5));
    }

    /**
     * TODO: 协同过滤推荐 - 找相似用户
     * 后期可实现：
     * 1. 计算用户标签向量相似度
     * 2. 找到相似用户
     * 3. 推荐相似用户喜欢但当前用户没用过的风格
     */
    public List<String> getCollaborativeRecommendations(Long userId, int limit) {
        // TODO: 实现协同过滤算法
        return getDefaultStyles(limit);
    }
}
