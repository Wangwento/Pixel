package com.wwt.pixel.video.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.video.domain.VideoAsset;
import com.wwt.pixel.video.domain.VideoGenerationRecord;
import com.wwt.pixel.video.domain.VideoGenerationRequest;
import com.wwt.pixel.video.domain.VideoSubmitResult;
import com.wwt.pixel.video.domain.VideoTaskResult;
import com.wwt.pixel.video.infrastructure.oss.VideoOssService;
import com.wwt.pixel.video.mapper.VideoAssetMapper;
import com.wwt.pixel.video.mapper.VideoGenerationRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoPersistenceService {

    private static final String DEFAULT_VIDEO_DURATION = "8";

    private static final DateTimeFormatter TITLE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final VideoGenerationRecordMapper videoGenerationRecordMapper;
    private final VideoAssetMapper videoAssetMapper;
    private final VideoOssService videoOssService;
    private final ObjectMapper objectMapper;

    public void createSubmitRecord(VideoGenerationRequest request,
                                   VideoSubmitResult submitResult,
                                   BigDecimal costPerSecond) {
        if (request == null || request.getUserId() == null || submitResult == null) {
            return;
        }
        String resolvedDuration = normalizeDuration(request.getDuration());
        VideoGenerationRecord record = VideoGenerationRecord.builder()
                .userId(request.getUserId())
                .taskId(submitResult.getTaskId())
                .providerTaskId(submitResult.getProviderTaskId())
                .requestType(request.hasSourceImages() ? "IMAGE2VIDEO" : "TEXT2VIDEO")
                .prompt(request.getPrompt())
                .sourceImages(serializeSourceImages(request))
                .vendor(firstNonBlank(submitResult.getVendor(), request.getVendor()))
                .model(firstNonBlank(submitResult.getModel(), request.getModelId()))
                .aspectRatio(request.getAspectRatio())
                .duration(resolvedDuration)
                .cost(calculateGenerationCost(resolvedDuration, costPerSecond))
                .hd(Boolean.TRUE.equals(request.getHd()))
                .notifyHook(request.getNotifyHook())
                .watermark(Boolean.TRUE.equals(request.getWatermark()))
                .privateMode(Boolean.TRUE.equals(request.getPrivateMode()))
                .status(firstNonBlank(submitResult.getTaskStatus(), "SUBMITTED"))
                .submitTime(LocalDateTime.now())
                .build();
        try {
            videoGenerationRecordMapper.insert(record);
            log.info("插入视频生成记录成功: recordId={}, taskId={}, userId={}",
                    record.getId(), record.getTaskId(), record.getUserId());
        } catch (DuplicateKeyException duplicateKeyException) {
            log.warn("视频生成记录已存在，跳过重复插入: taskId={}, providerTaskId={}",
                    submitResult.getTaskId(), submitResult.getProviderTaskId());
        } catch (Exception exception) {
            log.error("插入视频生成记录失败: taskId={}, providerTaskId={}",
                    submitResult.getTaskId(), submitResult.getProviderTaskId(), exception);
        }
    }

    private BigDecimal calculateGenerationCost(String duration, BigDecimal costPerSecond) {
        String resolvedDuration = normalizeDuration(duration);
        if (costPerSecond == null || costPerSecond.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal seconds = new BigDecimal(resolvedDuration);
            if (seconds.signum() <= 0) {
                return BigDecimal.ZERO;
            }
            return seconds.multiply(costPerSecond).setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            log.warn("视频时长格式非法，无法计算生成成本: duration={}", duration);
            return BigDecimal.ZERO;
        }
    }

    private String normalizeDuration(String duration) {
        return StringUtils.hasText(duration) ? duration.trim() : DEFAULT_VIDEO_DURATION;
    }

    @Transactional
    public VideoTaskResult syncTaskResult(VideoTaskResult taskResult) {
        if (taskResult == null) {
            return null;
        }

        VideoGenerationRecord record = findGenerationRecord(taskResult);
        MediaUrls mediaUrls = resolveMediaUrls(taskResult, record);
        applyMediaUrls(taskResult, mediaUrls);

        if (record == null) {
            return taskResult;
        }

        try {
            updateGenerationRecord(record, taskResult, mediaUrls);
            if (isSuccessStatus(taskResult.getTaskStatus())) {
                syncVideoAsset(record, taskResult, mediaUrls);
            }
        } catch (Exception exception) {
            log.error("同步视频任务结果失败: taskId={}, providerTaskId={}",
                    taskResult.getTaskId(), taskResult.getProviderTaskId(), exception);
        }
        return taskResult;
    }

    public VideoGenerationRecord findRecord(String taskId, String providerTaskId) {
        VideoGenerationRecord record = null;
        if (StringUtils.hasText(taskId)) {
            record = videoGenerationRecordMapper.findByTaskId(taskId.trim());
        }
        if (record == null && StringUtils.hasText(providerTaskId)) {
            record = videoGenerationRecordMapper.findByProviderTaskId(providerTaskId.trim());
        }
        return record;
    }

    public VideoAsset findAssetByGenerationRecordId(Long generationRecordId) {
        if (generationRecordId == null) {
            return null;
        }
        return videoAssetMapper.findByGenerationRecordId(generationRecordId);
    }

    public VideoTaskResult buildTaskResultFromRecord(VideoGenerationRecord record) {
        if (record == null) {
            return null;
        }
        VideoTaskResult taskResult = VideoTaskResult.builder()
                .taskId(record.getTaskId())
                .providerTaskId(record.getProviderTaskId())
                .taskStatus(record.getStatus())
                .progress(record.getProgress())
                .videoUrl(record.getResultVideoUrl())
                .coverUrl(record.getCoverUrl())
                .vendor(record.getVendor())
                .model(record.getModel())
                .failReason(record.getFailReason())
                .submitTime(toEpochMilli(record.getSubmitTime()))
                .startTime(toEpochMilli(record.getStartTime()))
                .finishTime(toEpochMilli(record.getFinishTime()))
                .build();
        if (videoOssService.isCurrentOssUrl(record.getResultVideoUrl())) {
            taskResult.setOssVideoUrl(record.getResultVideoUrl());
        }
        if (videoOssService.isCurrentOssUrl(record.getCoverUrl())) {
            taskResult.setOssCoverUrl(record.getCoverUrl());
        }
        return taskResult;
    }

    private VideoGenerationRecord findGenerationRecord(VideoTaskResult taskResult) {
        return findRecord(taskResult.getTaskId(), taskResult.getProviderTaskId());
    }

    private MediaUrls resolveMediaUrls(VideoTaskResult taskResult, VideoGenerationRecord record) {
        String persistedVideoUrl = record == null ? null : record.getResultVideoUrl();
        String persistedCoverUrl = record == null ? null : record.getCoverUrl();

        String finalVideoUrl = firstNonBlank(persistedVideoUrl, taskResult.getOssVideoUrl(), taskResult.getVideoUrl());
        String finalCoverUrl = firstNonBlank(persistedCoverUrl, taskResult.getOssCoverUrl(), taskResult.getCoverUrl());

        if (!isSuccessStatus(taskResult.getTaskStatus())) {
            return new MediaUrls(finalVideoUrl, finalCoverUrl);
        }

        String upstreamVideoUrl = taskResult.getVideoUrl();
        boolean videoAlreadyOnOss = videoOssService.isCurrentOssUrl(persistedVideoUrl);
        if (!StringUtils.hasText(finalVideoUrl)
                || (videoOssService.isEnabled() && StringUtils.hasText(upstreamVideoUrl) && !videoAlreadyOnOss)) {
            String uploadedVideoUrl = videoOssService.uploadVideoFromUrl(upstreamVideoUrl);
            finalVideoUrl = firstNonBlank(uploadedVideoUrl, finalVideoUrl, upstreamVideoUrl);
        } else {
            finalVideoUrl = firstNonBlank(finalVideoUrl, upstreamVideoUrl);
        }

        boolean coverAlreadyOnOss = videoOssService.isCurrentOssUrl(persistedCoverUrl);
        if (!StringUtils.hasText(finalCoverUrl)
                || (videoOssService.isEnabled() && StringUtils.hasText(finalVideoUrl) && !coverAlreadyOnOss)) {
            String uploadedCoverUrl = null;
            if (videoOssService.isCurrentOssUrl(finalVideoUrl)) {
                uploadedCoverUrl = videoOssService.uploadFirstFrameCoverFromVideo(finalVideoUrl);
            }
            finalCoverUrl = firstNonBlank(uploadedCoverUrl, finalCoverUrl);
            if (!StringUtils.hasText(finalCoverUrl) && videoOssService.isCurrentOssUrl(finalVideoUrl)) {
                finalCoverUrl = videoOssService.buildFirstFrameSnapshotUrl(finalVideoUrl);
            }
        }

        return new MediaUrls(finalVideoUrl, finalCoverUrl);
    }

    private void applyMediaUrls(VideoTaskResult taskResult, MediaUrls mediaUrls) {
        if (mediaUrls == null) {
            return;
        }
        if (StringUtils.hasText(mediaUrls.videoUrl())) {
            taskResult.setVideoUrl(mediaUrls.videoUrl());
            if (videoOssService.isCurrentOssUrl(mediaUrls.videoUrl())) {
                taskResult.setOssVideoUrl(mediaUrls.videoUrl());
            }
        }
        if (StringUtils.hasText(mediaUrls.coverUrl())) {
            taskResult.setCoverUrl(mediaUrls.coverUrl());
            if (videoOssService.isCurrentOssUrl(mediaUrls.coverUrl())) {
                taskResult.setOssCoverUrl(mediaUrls.coverUrl());
            }
        }
    }

    private void updateGenerationRecord(VideoGenerationRecord record, VideoTaskResult taskResult, MediaUrls mediaUrls) {
        record.setStatus(taskResult.getTaskStatus());
        record.setProgress(taskResult.getProgress());
        record.setResultVideoUrl(mediaUrls.videoUrl());
        record.setCoverUrl(mediaUrls.coverUrl());
        record.setFailReason(taskResult.getFailReason());
        record.setSubmitTime(resolveSubmitTime(taskResult, record));
        record.setStartTime(resolveStartTime(taskResult));
        record.setFinishTime(resolveFinishTime(taskResult));
        record.setVendor(taskResult.getVendor());
        record.setModel(taskResult.getModel());
        videoGenerationRecordMapper.updateTaskResult(record);
    }

    private void syncVideoAsset(VideoGenerationRecord record, VideoTaskResult taskResult, MediaUrls mediaUrls) {
        if (!StringUtils.hasText(mediaUrls.videoUrl())) {
            return;
        }

        VideoAsset existingAsset = videoAssetMapper.findByGenerationRecordId(record.getId());
        if (existingAsset == null) {
            VideoAsset asset = new VideoAsset();
            asset.setUserId(record.getUserId());
            asset.setGenerationRecordId(record.getId());
            asset.setTitle(buildDefaultTitle(record));
            asset.setVideoUrl(mediaUrls.videoUrl());
            asset.setCoverUrl(mediaUrls.coverUrl());
            asset.setPrompt(record.getPrompt());
            asset.setDuration(record.getDuration());
            asset.setSourceType("GENERATED");
            asset.setCreatedAt(resolveAssetCreatedAt(record));
            try {
                videoAssetMapper.insert(asset);
                log.info("插入视频资产成功: assetId={}, generationRecordId={}", asset.getId(), record.getId());
            } catch (DuplicateKeyException duplicateKeyException) {
                log.warn("视频资产已存在，跳过重复创建: generationRecordId={}", record.getId());
            }
            return;
        }

        if (needsAssetUpdate(existingAsset, mediaUrls, record)) {
            VideoAsset updateAsset = new VideoAsset();
            updateAsset.setId(existingAsset.getId());
            updateAsset.setTitle(buildDefaultTitle(record));
            updateAsset.setVideoUrl(mediaUrls.videoUrl());
            updateAsset.setCoverUrl(mediaUrls.coverUrl());
            updateAsset.setPrompt(record.getPrompt());
            updateAsset.setDuration(record.getDuration());
            videoAssetMapper.update(updateAsset);
        }
    }

    private boolean needsAssetUpdate(VideoAsset existingAsset, MediaUrls mediaUrls, VideoGenerationRecord record) {
        return !equalsNullable(existingAsset.getVideoUrl(), mediaUrls.videoUrl())
                || !equalsNullable(existingAsset.getCoverUrl(), mediaUrls.coverUrl())
                || !equalsNullable(existingAsset.getPrompt(), record.getPrompt())
                || !equalsNullable(existingAsset.getDuration(), record.getDuration())
                || !equalsNullable(existingAsset.getTitle(), buildDefaultTitle(record));
    }

    private String serializeSourceImages(VideoGenerationRequest request) {
        List<String> sourceImages = new ArrayList<>();
        if (request.getSourceImageUrls() != null) {
            request.getSourceImageUrls().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(sourceImages::add);
        }
        if (request.getSourceImageBase64List() != null) {
            int inlineIndex = 1;
            for (String base64 : request.getSourceImageBase64List()) {
                if (StringUtils.hasText(base64)) {
                    sourceImages.add("[inline-image-" + inlineIndex++ + "]");
                }
            }
        }
        if (sourceImages.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sourceImages);
        } catch (JsonProcessingException exception) {
            log.warn("序列化视频来源图片失败: taskPrompt={}", request.getPrompt(), exception);
            return null;
        }
    }

    private LocalDateTime resolveSubmitTime(VideoTaskResult taskResult, VideoGenerationRecord record) {
        LocalDateTime submitTime = toLocalDateTime(taskResult.getSubmitTime());
        if (submitTime != null) {
            return submitTime;
        }
        return record.getSubmitTime();
    }

    private LocalDateTime resolveStartTime(VideoTaskResult taskResult) {
        return toLocalDateTime(taskResult.getStartTime());
    }

    private LocalDateTime resolveFinishTime(VideoTaskResult taskResult) {
        LocalDateTime finishTime = toLocalDateTime(taskResult.getFinishTime());
        if (finishTime != null) {
            return finishTime;
        }
        return isSuccessStatus(taskResult.getTaskStatus()) ? LocalDateTime.now() : null;
    }

    private LocalDateTime resolveAssetCreatedAt(VideoGenerationRecord record) {
        if (record.getFinishTime() != null) {
            return record.getFinishTime();
        }
        if (record.getUpdatedAt() != null) {
            return record.getUpdatedAt();
        }
        if (record.getCreatedAt() != null) {
            return record.getCreatedAt();
        }
        return LocalDateTime.now();
    }

    private LocalDateTime toLocalDateTime(Long timestamp) {
        if (timestamp == null || timestamp <= 0) {
            return null;
        }
        Instant instant = timestamp > 9_999_999_999L
                ? Instant.ofEpochMilli(timestamp)
                : Instant.ofEpochSecond(timestamp);
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private Long toEpochMilli(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private boolean isSuccessStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "SUCCEEDED", "COMPLETED", "DONE", "FINISHED" -> true;
            default -> false;
        };
    }

    private String buildDefaultTitle(VideoGenerationRecord record) {
        String prompt = record.getPrompt() == null ? "" : record.getPrompt().trim();
        if (!prompt.isEmpty()) {
            return prompt.length() > 30 ? prompt.substring(0, 30) + "..." : prompt;
        }
        LocalDateTime time = record.getCreatedAt() == null ? LocalDateTime.now() : record.getCreatedAt();
        return "视频作品-" + time.format(TITLE_TIME_FORMATTER);
    }

    private boolean equalsNullable(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record MediaUrls(String videoUrl, String coverUrl) {
    }
}
