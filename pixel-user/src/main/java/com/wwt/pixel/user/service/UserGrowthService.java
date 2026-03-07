package com.wwt.pixel.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.user.domain.GrowthActivity;
import com.wwt.pixel.user.domain.GrowthActivityReward;
import com.wwt.pixel.user.domain.PointsRecord;
import com.wwt.pixel.user.domain.QuotaRecord;
import com.wwt.pixel.user.domain.User;
import com.wwt.pixel.user.domain.UserGrowthRecord;
import com.wwt.pixel.user.mapper.GrowthActivityMapper;
import com.wwt.pixel.user.mapper.GrowthActivityRewardMapper;
import com.wwt.pixel.user.mapper.PointsRecordMapper;
import com.wwt.pixel.user.mapper.QuotaRecordMapper;
import com.wwt.pixel.user.mapper.UserGrowthRecordMapper;
import com.wwt.pixel.user.mapper.UserMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 用户增长任务与奖励服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGrowthService {

    private static final String TRIGGER_REGISTER = "register";
    private static final String TRIGGER_PROFILE_COMPLETE = "profile_complete";
    private static final String STATUS_ACTION_REQUIRED = "ACTION_REQUIRED";
    private static final String STATUS_CLAIMABLE = "CLAIMABLE";
    private static final String STATUS_CLAIMED = "CLAIMED";
    private static final String ACTION_COMPLETE_PROFILE = "COMPLETE_PROFILE";
    private static final String ACTION_CLAIM = "CLAIM";
    private static final String ACTION_NONE = "NONE";

    private final UserMapper userMapper;
    private final GrowthActivityMapper growthActivityMapper;
    private final GrowthActivityRewardMapper growthActivityRewardMapper;
    private final UserGrowthRecordMapper userGrowthRecordMapper;
    private final PointsRecordMapper pointsRecordMapper;
    private final QuotaRecordMapper quotaRecordMapper;
    private final ObjectMapper objectMapper;

    /**
     * 注册后创建新人礼包待领取任务
     */
    @Transactional
    public void createRegisterGiftTask(Long userId) {
        User user = userMapper.findByIdForUpdate(userId);
        if (user == null || !shouldCreateRegisterGiftTask(user)) {
            return;
        }
        GrowthActivity activity = growthActivityMapper.findActiveByTriggerType(TRIGGER_REGISTER);
        if (activity == null) {
            log.warn("未找到启用中的注册礼包活动: userId={}", userId);
            return;
        }
        ensureGrowthRecord(userId, activity, TRIGGER_REGISTER, "auth.register");
    }

    /**
     * 获取通知任务列表
     */
    @Transactional
    public TaskListResult listTasks(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        ensureRegisterGiftTaskIfNeeded(user);

        List<GrowthTaskDTO> tasks = new ArrayList<>();
        List<UserGrowthRecord> records = userGrowthRecordMapper.findByUserId(userId);

        for (UserGrowthRecord record : records) {
            GrowthActivity activity = growthActivityMapper.findById(record.getActivityId());
            if (activity == null) {
                continue;
            }
            tasks.add(toRecordTask(activity, record));
        }

        GrowthActivity profileActivity = growthActivityMapper.findActiveByTriggerType(TRIGGER_PROFILE_COMPLETE);
        if (profileActivity != null) {
            UserGrowthRecord profileRecord = userGrowthRecordMapper.findLatestByActivityAndUser(profileActivity.getId(), userId);
            boolean profileCompleted = user.getProfileCompleted() != null && user.getProfileCompleted() == 1;
            if (profileRecord == null && !profileCompleted) {
                tasks.add(toActionTask(profileActivity));
            }
        }

        tasks.sort(
                Comparator.comparingInt(this::taskRank)
                        .thenComparing((GrowthTaskDTO task) -> task.getCreatedAt() == null ? LocalDateTime.MIN : task.getCreatedAt())
                        .reversed()
        );

        int pendingCount = (int) tasks.stream()
                .filter(task -> !STATUS_CLAIMED.equals(task.getStatus()))
                .count();

        return TaskListResult.builder()
                .pendingCount(pendingCount)
                .tasks(tasks)
                .build();
    }

    /**
     * 完善资料，并将奖励转为待领取任务
     */
    @Transactional
    public GrowthTaskDTO completeProfileTask(Long userId, String nickname, String avatar) {
        User user = userMapper.findByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        GrowthActivity activity = growthActivityMapper.findActiveByTriggerType(TRIGGER_PROFILE_COMPLETE);
        if (activity == null) {
            throw new BusinessException("当前暂无可领取的完善资料奖励");
        }

        UserGrowthRecord existing = userGrowthRecordMapper.findLatestByActivityAndUser(activity.getId(), userId);
        if (user.getProfileCompleted() != null && user.getProfileCompleted() == 1) {
            if (existing != null) {
                return toRecordTask(activity, existing);
            }
            throw new BusinessException("资料已完善");
        }

        userMapper.updateProfile(userId, nickname, avatar, 1);

        UserGrowthRecord record = ensureGrowthRecord(userId, activity, TRIGGER_PROFILE_COMPLETE, "user.profile.complete");
        log.info("完善资料成功，奖励待领取: userId={}, activityCode={}", userId, activity.getActivityCode());
        return toRecordTask(activity, record);
    }

    /**
     * 领取奖励
     */
    @Transactional
    public ClaimResult claimReward(Long userId, Long recordId) {
        UserGrowthRecord record = userGrowthRecordMapper.findByIdForUpdate(recordId);
        if (record == null || !userId.equals(record.getUserId())) {
            throw new BusinessException("奖励记录不存在");
        }
        if (record.getRewardStatus() == null || record.getRewardStatus() != 0) {
            throw new BusinessException("奖励已领取或不可领取");
        }

        User user = userMapper.findByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        GrowthActivity activity = growthActivityMapper.findById(record.getActivityId());
        List<RewardSnapshotItem> rewards = parseRewardSnapshot(record.getRewardSnapshot());
        if (rewards.isEmpty() && activity != null) {
            rewards = buildRewardSnapshot(activity.getId());
        }
        if (rewards.isEmpty()) {
            throw new BusinessException("奖励配置不存在");
        }

        int pointsAdded = 0;
        int quotaAdded = 0;

        for (RewardSnapshotItem reward : rewards) {
            int rewardValue = reward.getValue().intValue();
            if ("points".equals(reward.getType())) {
                user.addPoints(rewardValue);
                pointsAdded += rewardValue;
            } else if ("quota".equals(reward.getType())) {
                user.addFreeQuota(rewardValue);
                quotaAdded += rewardValue;
            } else {
                log.warn("暂不支持的增长奖励类型: recordId={}, rewardType={}", recordId, reward.getType());
            }
        }

        if (pointsAdded > 0) {
            userMapper.updatePoints(userId, user.getPoints(), user.getTotalPoints());
            pointsRecordMapper.insert(PointsRecord.builder()
                    .userId(userId)
                    .points(pointsAdded)
                    .balance(user.getPoints())
                    .type(resolvePointsRecordType(activity))
                    .source(activity == null ? "growth-reward" : activity.getActivityCode())
                    .description(activity == null ? "成长奖励" : activity.getActivityName())
                    .build());
        }

        if (quotaAdded > 0) {
            userMapper.updateFreeQuota(userId, user.getFreeQuota(), user.getFreeQuotaTotal());
            quotaRecordMapper.insert(QuotaRecord.builder()
                    .userId(userId)
                    .quota(quotaAdded)
                    .balance(user.getFreeQuota())
                    .type(7)
                    .source(activity == null ? "growth-reward" : activity.getActivityCode())
                    .description(activity == null ? "成长奖励" : activity.getActivityName())
                    .build());
        }

        LocalDateTime now = LocalDateTime.now();
        userGrowthRecordMapper.updateRewardStatus(recordId, 1, now);
        record.setRewardStatus(1);
        record.setGrantedAt(now);

        return ClaimResult.builder()
                .pointsAdded(pointsAdded)
                .quotaAdded(quotaAdded)
                .task(toRecordTask(activity, record))
                .build();
    }

    private void ensureRegisterGiftTaskIfNeeded(User user) {
        if (!shouldCreateRegisterGiftTask(user)) {
            return;
        }
        GrowthActivity activity = growthActivityMapper.findActiveByTriggerType(TRIGGER_REGISTER);
        if (activity == null) {
            return;
        }
        ensureGrowthRecord(user.getId(), activity, TRIGGER_REGISTER, "user.growth.list");
    }

    private boolean shouldCreateRegisterGiftTask(User user) {
        return (user.getTotalPoints() == null || user.getTotalPoints() == 0)
                && (user.getFreeQuotaTotal() == null || user.getFreeQuotaTotal() == 0);
    }

    private UserGrowthRecord ensureGrowthRecord(Long userId, GrowthActivity activity, String bizKey, String triggerSource) {
        UserGrowthRecord existing = userGrowthRecordMapper.findByActivityAndUserAndBizKey(activity.getId(), userId, bizKey);
        if (existing != null) {
            return existing;
        }

        UserGrowthRecord record = new UserGrowthRecord();
        record.setActivityId(activity.getId());
        record.setUserId(userId);
        record.setBizKey(bizKey);
        record.setTriggerType(activity.getTriggerType());
        record.setTriggerSource(triggerSource);
        record.setHitStatus(1);
        record.setRewardStatus(0);
        record.setRewardSnapshot(toRewardSnapshotJson(buildRewardSnapshot(activity.getId())));
        record.setTriggeredAt(LocalDateTime.now());
        try {
            userGrowthRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.warn("增长任务重复创建，返回已存在记录: userId={}, activityCode={}, bizKey={}",
                    userId, activity.getActivityCode(), bizKey);
            return userGrowthRecordMapper.findByActivityAndUserAndBizKey(activity.getId(), userId, bizKey);
        }
        return record;
    }

    private GrowthTaskDTO toRecordTask(GrowthActivity activity, UserGrowthRecord record) {
        List<RewardSnapshotItem> rewards = parseRewardSnapshot(record.getRewardSnapshot());
        String status = (record.getRewardStatus() != null && record.getRewardStatus() == 1)
                ? STATUS_CLAIMED
                : STATUS_CLAIMABLE;
        return GrowthTaskDTO.builder()
                .recordId(record.getId())
                .activityCode(activity.getActivityCode())
                .title(activity.getActivityName())
                .description(activity.getDescription())
                .triggerType(activity.getTriggerType())
                .status(status)
                .actionType(STATUS_CLAIMED.equals(status) ? ACTION_NONE : ACTION_CLAIM)
                .rewardSummary(toRewardSummary(rewards))
                .createdAt(record.getCreatedAt() == null ? record.getTriggeredAt() : record.getCreatedAt())
                .claimedAt(record.getGrantedAt())
                .build();
    }

    private GrowthTaskDTO toActionTask(GrowthActivity activity) {
        List<RewardSnapshotItem> rewards = buildRewardSnapshot(activity.getId());
        return GrowthTaskDTO.builder()
                .activityCode(activity.getActivityCode())
                .title(activity.getActivityName())
                .description(activity.getDescription())
                .triggerType(activity.getTriggerType())
                .status(STATUS_ACTION_REQUIRED)
                .actionType(ACTION_COMPLETE_PROFILE)
                .rewardSummary(toRewardSummary(rewards))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private int taskRank(GrowthTaskDTO task) {
        if (STATUS_CLAIMABLE.equals(task.getStatus())) {
            return 3;
        }
        if (STATUS_ACTION_REQUIRED.equals(task.getStatus())) {
            return 2;
        }
        return 1;
    }

    private List<RewardSnapshotItem> buildRewardSnapshot(Long activityId) {
        List<GrowthActivityReward> rewards = growthActivityRewardMapper.findByActivityId(activityId);
        List<RewardSnapshotItem> snapshot = new ArrayList<>();
        for (GrowthActivityReward reward : rewards) {
            snapshot.add(RewardSnapshotItem.builder()
                    .type(reward.getRewardType())
                    .value(reward.getRewardValue())
                    .unit(reward.getRewardUnit())
                    .build());
        }
        return snapshot;
    }

    private String toRewardSnapshotJson(List<RewardSnapshotItem> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new BusinessException("奖励快照序列化失败");
        }
    }

    private List<RewardSnapshotItem> parseRewardSnapshot(String rewardSnapshot) {
        if (rewardSnapshot == null || rewardSnapshot.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(rewardSnapshot, new TypeReference<List<RewardSnapshotItem>>() {});
        } catch (JsonProcessingException e) {
            log.error("奖励快照解析失败: {}", rewardSnapshot, e);
            return new ArrayList<>();
        }
    }

    private String toRewardSummary(List<RewardSnapshotItem> rewards) {
        List<String> parts = new ArrayList<>();
        for (RewardSnapshotItem reward : rewards) {
            int value = reward.getValue().intValue();
            if ("points".equals(reward.getType())) {
                parts.add("+" + value + "积分");
            } else if ("quota".equals(reward.getType())) {
                parts.add("+" + value + "次免费额度");
            } else if ("vip_days".equals(reward.getType())) {
                parts.add("+" + value + "天会员");
            }
        }
        return String.join(" · ", parts);
    }

    private int resolvePointsRecordType(GrowthActivity activity) {
        if (activity == null) {
            return 12;
        }
        if (TRIGGER_REGISTER.equals(activity.getTriggerType())) {
            return 11;
        }
        if (TRIGGER_PROFILE_COMPLETE.equals(activity.getTriggerType())) {
            return 10;
        }
        return 12;
    }

    @Data
    @Builder
    public static class TaskListResult {
        private Integer pendingCount;
        private List<GrowthTaskDTO> tasks;
    }

    @Data
    @Builder
    public static class ClaimResult {
        private Integer pointsAdded;
        private Integer quotaAdded;
        private GrowthTaskDTO task;
    }

    @Data
    @Builder
    public static class GrowthTaskDTO {
        private Long recordId;
        private String activityCode;
        private String title;
        private String description;
        private String triggerType;
        private String status;
        private String actionType;
        private String rewardSummary;
        private LocalDateTime createdAt;
        private LocalDateTime claimedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RewardSnapshotItem {
        private String type;
        private BigDecimal value;
        private String unit;
    }
}
