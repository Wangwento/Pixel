package com.wwt.pixel.image.service;

import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.image.domain.HotImage;
import com.wwt.pixel.image.domain.ImageAsset;
import com.wwt.pixel.image.feign.UserServiceClient;
import com.wwt.pixel.image.mapper.HotImageMapper;
import com.wwt.pixel.image.mapper.ImageAssetMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 热门图片服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotImageService {

    private final HotImageMapper hotImageMapper;
    private final ImageAssetMapper imageAssetMapper;
    private final UserServiceClient userServiceClient;

    /**
     * 用户提交图片到热门
     */
    @Transactional
    public HotImage submit(Long userId, Long imageAssetId, String description) {
        // 确认图片属于该用户
        ImageAsset asset = imageAssetMapper.findByIdAndUserId(imageAssetId, userId);
        if (asset == null) {
            throw new BusinessException("图片资产不存在");
        }

        // 检查重复提交(同一图片，且非已拒绝状态)
        HotImage existing = hotImageMapper.findByUserAndAssetNotRejected(userId, imageAssetId);
        if (existing != null) {
            throw new BusinessException("该图片已提交过，请勿重复提交");
        }

        HotImage hotImage = new HotImage();
        hotImage.setUserId(userId);
        hotImage.setImageAssetId(imageAssetId);
        hotImage.setMediaType("image");
        hotImage.setImageUrl(asset.getImageUrl());
        hotImage.setTitle(asset.getTitle());
        hotImage.setDescription(description);
        hotImage.setStatus(HotImage.STATUS_PENDING);
        hotImage.setRewardClaimed(HotImage.REWARD_NOT_CLAIMED);
        hotImage.setRewardPoints(100);
        hotImageMapper.insert(hotImage);

        log.info("用户提交热门图片: userId={}, imageAssetId={}, hotImageId={}", userId, imageAssetId, hotImage.getId());
        return hotImage;
    }

    /**
     * 我的提交记录(分页)
     */
    public PageResult mySubmissions(Long userId, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        List<HotImage> list = hotImageMapper.findByUserId(userId, offset, pageSize);
        fillUserNicknames(list);
        int total = hotImageMapper.countByUserId(userId);
        return PageResult.builder().list(list).total(total).page(page).pageSize(pageSize).build();
    }

    /**
     * 获取用户热门图片通知
     */
    public List<HotImageNotification> getNotifications(Long userId) {
        List<HotImageNotification> notifications = new ArrayList<>();

        // 已通过未领取
        List<HotImage> approvedUnclaimed = hotImageMapper.findApprovedUnclaimed(userId);
        for (HotImage hi : approvedUnclaimed) {
            notifications.add(HotImageNotification.builder()
                    .hotImageId(hi.getId())
                    .title("热门图片审核通过")
                    .description("你的图片「" + (hi.getTitle() != null ? hi.getTitle() : "作品") + "」已通过热门审核")
                    .imageUrl(hi.getImageUrl())
                    .status("CLAIMABLE")
                    .actionType("CLAIM_HOT")
                    .rewardSummary("+" + hi.getRewardPoints() + "积分")
                    .createdAt(hi.getReviewedAt())
                    .build());
        }

        // 已拒绝(近30天)
        List<HotImage> rejected = hotImageMapper.findRecentRejected(userId);
        for (HotImage hi : rejected) {
            notifications.add(HotImageNotification.builder()
                    .hotImageId(hi.getId())
                    .title("热门图片审核未通过")
                    .description("你的图片「" + (hi.getTitle() != null ? hi.getTitle() : "作品") + "」未通过审核"
                            + (hi.getRejectReason() != null ? "，原因：" + hi.getRejectReason() : ""))
                    .imageUrl(hi.getImageUrl())
                    .status("REJECTED")
                    .actionType("NONE")
                    .createdAt(hi.getReviewedAt())
                    .build());
        }

        return notifications;
    }

    /**
     * 领取热门图片奖励
     */
    @Transactional
    public Map<String, Object> claimReward(Long hotImageId, Long userId) {
        HotImage hotImage = hotImageMapper.findByIdForUpdate(hotImageId);
        if (hotImage == null || !userId.equals(hotImage.getUserId())) {
            throw new BusinessException("记录不存在");
        }
        if (hotImage.getStatus() != HotImage.STATUS_APPROVED) {
            throw new BusinessException("该图片未通过审核");
        }
        if (hotImage.getRewardClaimed() == HotImage.REWARD_CLAIMED) {
            throw new BusinessException("奖励已领取");
        }

        int points = hotImage.getRewardPoints() != null ? hotImage.getRewardPoints() : 100;

        // 调用 pixel-user 加积分
        Map<String, Object> body = new HashMap<>();
        body.put("points", points);
        body.put("source", "hot_image_reward");
        body.put("description", "热门图片审核通过奖励");
        Result<Void> feignResult = userServiceClient.addPoints(userId, body);
        if (feignResult == null || feignResult.getCode() != 200) {
            throw new BusinessException("积分发放失败，请稍后重试");
        }

        // 标记已领取
        hotImageMapper.claimReward(hotImageId, LocalDateTime.now());

        Map<String, Object> result = new HashMap<>();
        result.put("pointsAdded", points);
        return result;
    }

    /**
     * 公共热门图片列表(已通过，分页)
     */
    public PageResult listApproved(int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        List<HotImage> list = hotImageMapper.findApproved(offset, pageSize);
        fillUserNicknames(list);
        int total = hotImageMapper.countApproved();
        return PageResult.builder().list(list).total(total).page(page).pageSize(pageSize).build();
    }

    /**
     * 管理端审核列表
     */
    public PageResult adminList(Integer status, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        List<HotImage> list = hotImageMapper.findByStatusWithPaging(status, offset, pageSize);
        fillUserNicknames(list);
        int total = hotImageMapper.countByStatus(status);
        return PageResult.builder().list(list).total(total).page(page).pageSize(pageSize).build();
    }

    private void fillUserNicknames(List<HotImage> hotImages) {
        if (hotImages == null || hotImages.isEmpty()) {
            return;
        }

        List<Long> userIds = hotImages.stream()
                .map(HotImage::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }

        try {
            Map<String, List<Long>> body = new HashMap<>();
            body.put("userIds", userIds);
            Result<List<UserServiceClient.UserBasicInfoDTO>> result = userServiceClient.getUserBasicInfoBatch(body);
            if (result == null || result.getCode() != 200 || result.getData() == null || result.getData().isEmpty()) {
                return;
            }

            Map<Long, String> nicknameMap = new LinkedHashMap<>();
            for (UserServiceClient.UserBasicInfoDTO info : result.getData()) {
                if (info == null || info.getId() == null) {
                    continue;
                }
                String nickname = hasText(info.getNickname()) ? info.getNickname() : info.getUsername();
                nicknameMap.put(info.getId(), nickname);
            }

            for (HotImage hotImage : hotImages) {
                if (hotImage.getUserId() == null) {
                    continue;
                }
                hotImage.setNickname(nicknameMap.get(hotImage.getUserId()));
            }
        } catch (Exception exception) {
            log.warn("填充热门图片昵称失败, userIds={}", userIds, exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 管理员通过
     */
    @Transactional
    public void approve(Long hotImageId, Long reviewerId) {
        HotImage hotImage = hotImageMapper.findByIdForUpdate(hotImageId);
        if (hotImage == null) {
            throw new BusinessException("记录不存在");
        }
        if (hotImage.getStatus() != HotImage.STATUS_PENDING) {
            throw new BusinessException("该记录不是待审核状态");
        }
        hotImageMapper.approve(hotImageId, HotImage.STATUS_APPROVED, reviewerId, LocalDateTime.now());
        log.info("热门图片审核通过: hotImageId={}, reviewerId={}", hotImageId, reviewerId);
    }

    /**
     * 管理员拒绝
     */
    @Transactional
    public void reject(Long hotImageId, Long reviewerId, String rejectReason) {
        HotImage hotImage = hotImageMapper.findByIdForUpdate(hotImageId);
        if (hotImage == null) {
            throw new BusinessException("记录不存在");
        }
        if (hotImage.getStatus() != HotImage.STATUS_PENDING) {
            throw new BusinessException("该记录不是待审核状态");
        }
        hotImageMapper.reject(hotImageId, HotImage.STATUS_REJECTED, rejectReason, reviewerId, LocalDateTime.now());
        log.info("热门图片审核拒绝: hotImageId={}, reviewerId={}, reason={}", hotImageId, reviewerId, rejectReason);
    }

    /**
     * 管理员下架
     */
    @Transactional
    public void offline(Long hotImageId, Long reviewerId) {
        HotImage hotImage = hotImageMapper.findByIdForUpdate(hotImageId);
        if (hotImage == null) {
            throw new BusinessException("记录不存在");
        }
        if (hotImage.getStatus() != HotImage.STATUS_APPROVED) {
            throw new BusinessException("只有已通过的热门记录才可下架");
        }
        hotImageMapper.offline(hotImageId, HotImage.STATUS_OFFLINE, reviewerId, LocalDateTime.now());
        log.info("热门图片已下架: hotImageId={}, reviewerId={}", hotImageId, reviewerId);
    }

    /**
     * 管理员彻底删除
     */
    @Transactional
    public void delete(Long hotImageId, Long reviewerId) {
        HotImage hotImage = hotImageMapper.findByIdForUpdate(hotImageId);
        if (hotImage == null) {
            throw new BusinessException("记录不存在");
        }
        if (hotImage.getStatus() != HotImage.STATUS_OFFLINE) {
            throw new BusinessException("只有已下架的记录才可删除");
        }
        hotImageMapper.deleteById(hotImageId);
        log.info("热门图片已删除: hotImageId={}, reviewerId={}", hotImageId, reviewerId);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageResult {
        private List<HotImage> list;
        private Integer total;
        private Integer page;
        private Integer pageSize;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HotImageNotification {
        private Long hotImageId;
        private String title;
        private String description;
        private String imageUrl;
        private String status;       // CLAIMABLE | REJECTED
        private String actionType;   // CLAIM_HOT | NONE
        private String rewardSummary;
        private LocalDateTime createdAt;
    }
}
