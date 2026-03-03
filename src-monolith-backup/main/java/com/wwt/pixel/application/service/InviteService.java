package com.wwt.pixel.application.service;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.domain.model.InviteRecord;
import com.wwt.pixel.domain.model.PointsRecord;
import com.wwt.pixel.domain.model.User;
import com.wwt.pixel.infrastructure.persistence.mapper.InviteRecordMapper;
import com.wwt.pixel.infrastructure.persistence.mapper.PointsRecordMapper;
import com.wwt.pixel.infrastructure.persistence.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邀请服务 (邀请奖励: 双方各100积分)
 *
 * 邀请规则:
 * - 被邀请人注册时填写邀请码
 * - 被邀请人完成首次图片生成后，双方各得100积分
 * - 邀请奖励只发放一次
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteService {

    private final UserMapper userMapper;
    private final InviteRecordMapper inviteRecordMapper;
    private final PointsRecordMapper pointsRecordMapper;

    public static final int INVITE_REWARD_POINTS = 100;  // 邀请奖励积分(双方各得)

    /**
     * 创建邀请记录(用户注册时调用)
     */
    @Transactional
    public void createInviteRecord(Long inviterId, Long inviteeId, String inviteCode) {
        // 检查是否已有邀请记录
        if (inviteRecordMapper.findByInviteeId(inviteeId) != null) {
            return; // 已有记录，跳过
        }

        InviteRecord record = InviteRecord.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .inviteCode(inviteCode)
                .status(InviteRecord.Status.REGISTERED)
                .inviterReward(0)
                .inviteeReward(0)
                .createdAt(LocalDateTime.now())
                .build();
        inviteRecordMapper.insert(record);

        log.info("创建邀请记录: inviter={}, invitee={}", inviterId, inviteeId);
    }

    /**
     * 完成邀请任务(被邀请人首次生成图片后调用)
     * 发放双方奖励
     */
    @Transactional
    public void completeInviteTask(Long inviteeId) {
        InviteRecord record = inviteRecordMapper.findByInviteeId(inviteeId);
        if (record == null || record.isCompleted()) {
            return; // 无邀请记录或已完成
        }

        Long inviterId = record.getInviterId();

        // 给邀请人发放奖励
        User inviter = userMapper.findById(inviterId);
        if (inviter != null) {
            inviter.addPoints(INVITE_REWARD_POINTS);
            userMapper.updatePoints(inviterId, inviter.getPoints(), inviter.getTotalPoints());
            recordPoints(inviterId, INVITE_REWARD_POINTS, inviter.getPoints(),
                    PointsRecord.Type.INVITE_REWARD, "invite_" + inviteeId, "邀请好友奖励");
        }

        // 给被邀请人发放奖励
        User invitee = userMapper.findById(inviteeId);
        if (invitee != null) {
            invitee.addPoints(INVITE_REWARD_POINTS);
            userMapper.updatePoints(inviteeId, invitee.getPoints(), invitee.getTotalPoints());
            recordPoints(inviteeId, INVITE_REWARD_POINTS, invitee.getPoints(),
                    PointsRecord.Type.INVITE_REWARD, "invited_by_" + inviterId, "受邀注册奖励");
        }

        // 更新邀请记录状态
        inviteRecordMapper.completeInvite(inviteeId, INVITE_REWARD_POINTS, LocalDateTime.now());

        log.info("邀请任务完成: inviter={}, invitee={}, reward={}", inviterId, inviteeId, INVITE_REWARD_POINTS);
    }

    /**
     * 获取用户的邀请统计
     */
    public InviteStats getInviteStats(Long userId) {
        int totalInvited = inviteRecordMapper.countByInviterId(userId);
        int completedCount = inviteRecordMapper.countByInviterIdAndStatus(userId, InviteRecord.Status.COMPLETED);
        int pendingCount = totalInvited - completedCount;

        User user = userMapper.findById(userId);
        String inviteCode = user != null ? user.getInviteCode() : null;

        InviteStats stats = new InviteStats();
        stats.setInviteCode(inviteCode);
        stats.setTotalInvited(totalInvited);
        stats.setCompletedCount(completedCount);
        stats.setPendingCount(pendingCount);
        stats.setTotalEarnedPoints(completedCount * INVITE_REWARD_POINTS);
        stats.setRewardPerInvite(INVITE_REWARD_POINTS);
        return stats;
    }

    /**
     * 获取邀请记录列表
     */
    public List<InviteRecord> getInviteRecords(Long userId, int page, int size) {
        return inviteRecordMapper.findByInviterId(userId, (page - 1) * size, size);
    }

    private void recordPoints(Long userId, int points, int balance, int type, String source, String description) {
        PointsRecord record = PointsRecord.builder()
                .userId(userId)
                .points(points)
                .balance(balance)
                .type(type)
                .source(source)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();
        pointsRecordMapper.insert(record);
    }

    /**
     * 邀请统计DTO
     */
    @Data
    public static class InviteStats {
        private String inviteCode;          // 我的邀请码
        private Integer totalInvited;       // 总邀请人数
        private Integer completedCount;     // 已完成人数(双方得奖)
        private Integer pendingCount;       // 待完成人数(注册未首图)
        private Integer totalEarnedPoints;  // 邀请累计获得积分
        private Integer rewardPerInvite;    // 每次邀请奖励
    }
}