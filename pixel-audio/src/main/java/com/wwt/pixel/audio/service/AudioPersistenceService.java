package com.wwt.pixel.audio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.audio.config.AudioProviderProperties;
import com.wwt.pixel.audio.controller.AudioController;
import com.wwt.pixel.audio.domain.AudioAsset;
import com.wwt.pixel.audio.domain.AudioGenerationRecord;
import com.wwt.pixel.audio.mapper.AudioAssetMapper;
import com.wwt.pixel.audio.mapper.AudioGenerationRecordMapper;
import com.wwt.pixel.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioPersistenceService {

    private static final DateTimeFormatter TITLE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AudioGenerationRecordMapper audioGenerationRecordMapper;
    private final AudioAssetMapper audioAssetMapper;
    private final ObjectMapper objectMapper;

    public void createSubmitRecord(Long userId,
                                   AudioController.MusicRequest request,
                                   AudioProviderProperties.CompatibleVendorConfig vendor,
                                   Object response) {
        if (userId == null || request == null || vendor == null) {
            return;
        }
        String taskId = extractString(response, "taskId");
        String providerTaskId = firstNonBlank(
                extractString(response, "providerTaskId"),
                extractString(response, "task_id"),
                extractString(response, "id")
        );
        if (!StringUtils.hasText(taskId) || !StringUtils.hasText(providerTaskId)) {
            return;
        }

        AudioGenerationRecord record = AudioGenerationRecord.builder()
                .userId(userId)
                .taskId(taskId)
                .providerTaskId(providerTaskId)
                .requestType(resolveRequestType(request))
                .prompt(trimToNull(request.getPrompt()))
                .title(trimToNull(request.getTitle()))
                .tags(trimToNull(request.getTags()))
                .continueClipId(trimToNull(request.getContinueClipId()))
                .vendor(vendor.getCode())
                .model(vendor.getModelId())
                .cost(vendor.getCostPerUnit() == null ? BigDecimal.ZERO : vendor.getCostPerUnit())
                .makeInstrumental(Boolean.TRUE.equals(request.getMakeInstrumental()))
                .requestPayload(toJson(request))
                .responsePayload(toJson(response))
                .status(resolveStatus(response))
                .resultCount(resolveClipCount(response))
                .failReason(resolveFailReason(response))
                .submitTime(LocalDateTime.now())
                .build();
        try {
            audioGenerationRecordMapper.insert(record);
            log.info("插入音频生成记录成功: recordId={}, taskId={}, userId={}",
                    record.getId(), record.getTaskId(), record.getUserId());
        } catch (DuplicateKeyException duplicateKeyException) {
            log.warn("音频生成记录已存在，跳过重复插入: taskId={}, providerTaskId={}",
                    taskId, providerTaskId);
        } catch (Exception exception) {
            log.error("插入音频生成记录失败: taskId={}, providerTaskId={}",
                    taskId, providerTaskId, exception);
        }
    }

    @Transactional
    public void syncTaskResult(Object response) {
        if (!(response instanceof Map<?, ?> rawMap)) {
            return;
        }
        Map<String, Object> responseMap = normalizeResponseMap(asStringKeyMap(rawMap));
        AudioGenerationRecord record = findGenerationRecord(responseMap);
        if (record == null) {
            return;
        }
        try {
            updateGenerationRecord(record, responseMap);
        } catch (Exception exception) {
            log.error("同步音频任务结果失败: taskId={}, providerTaskId={}",
                    record.getTaskId(), record.getProviderTaskId(), exception);
        }
    }

    @Transactional
    public void syncBatchTaskResults(Object response) {
        if (response instanceof List<?> list) {
            for (Object item : list) {
                syncTaskResult(item);
            }
            return;
        }
        if (response instanceof Map<?, ?> rawMap) {
            Map<String, Object> responseMap = normalizeResponseMap(asStringKeyMap(rawMap));
            Object data = responseMap.get("data");
            if (data instanceof List<?> list) {
                for (Object item : list) {
                    syncTaskResult(item);
                }
                return;
            }
            syncTaskResult(responseMap);
        }
    }

    public AudioGenerationRecord findGenerationRecord(String taskId, String providerTaskId) {
        AudioGenerationRecord record = null;
        if (StringUtils.hasText(taskId)) {
            record = audioGenerationRecordMapper.findByTaskId(taskId);
        }
        if (record == null && StringUtils.hasText(providerTaskId)) {
            record = audioGenerationRecordMapper.findByProviderTaskId(providerTaskId);
        }
        return record;
    }

    private AudioGenerationRecord findGenerationRecord(Map<String, Object> responseMap) {
        return findGenerationRecord(
                extractString(responseMap, "taskId"),
                firstNonBlank(
                        extractString(responseMap, "providerTaskId"),
                        extractString(responseMap, "task_id"),
                        extractString(responseMap, "id")
                )
        );
    }

    private void updateGenerationRecord(AudioGenerationRecord record, Map<String, Object> responseMap) {
        AudioGenerationRecord updateRecord = AudioGenerationRecord.builder()
                .id(record.getId())
                .status(resolvePersistedStatus(record, responseMap))
                .responsePayload(toJson(responseMap))
                .resultCount(resolveClipCount(responseMap))
                .failReason(resolveFailReason(responseMap))
                .vendor(firstNonBlank(extractString(responseMap, "vendor"), record.getVendor()))
                .model(firstNonBlank(extractString(responseMap, "model"), record.getModel()))
                .startTime(resolveStartTime(record, responseMap))
                .finishTime(resolveFinishTime(responseMap))
                .build();
        audioGenerationRecordMapper.updateTaskResult(updateRecord);
    }

    @Transactional
    public void finalizeUploadedAssets(Long userId,
                                      String taskId,
                                      AudioController.FinalizeTaskAssetsRequest request) {
        AudioGenerationRecord record = findGenerationRecord(taskId, null);
        if (record == null) {
            throw new BusinessException(404, "未找到对应的音频生成记录");
        }
        if (userId != null && record.getUserId() != null && !Objects.equals(userId, record.getUserId())) {
            throw new BusinessException(403, "无权操作该音频任务");
        }

        String requestedStatus = firstNonBlank(trimToNull(request.getStatus()), record.getStatus(), "FAILED");
        boolean success = isSuccessStatus(requestedStatus);

        if (success) {
            List<AudioController.UploadedClipRequest> uploadedClips = request.getClips() == null ? List.of() : request.getClips();
            if (uploadedClips.isEmpty()) {
                throw new BusinessException(400, "成功回填时 clips 不能为空");
            }
            for (AudioController.UploadedClipRequest uploadedClip : uploadedClips) {
                if (!StringUtils.hasText(uploadedClip.getAudioUrl())) {
                    throw new BusinessException(400, "成功回填时 audio_url 不能为空");
                }
                if (!StringUtils.hasText(uploadedClip.getCoverUrl())) {
                    throw new BusinessException(400, "成功回填时 cover_url 不能为空");
                }
                upsertUploadedClipAsset(record, uploadedClip);
            }
        }

        AudioGenerationRecord updateRecord = AudioGenerationRecord.builder()
                .id(record.getId())
                .status(success ? "SUCCESS" : "FAILED")
                .resultCount(request.getResultCount() == null
                        ? (request.getClips() == null ? 0 : request.getClips().size())
                        : request.getResultCount())
                .failReason(success ? null : trimToNull(request.getFailReason()))
                .makeInstrumental(request.getMakeInstrumental())
                .responsePayload(toJson(request.getResponsePayload()))
                .finishTime(LocalDateTime.now())
                .build();
        audioGenerationRecordMapper.updateTaskResult(updateRecord);
    }

    private void syncAudioAssets(AudioGenerationRecord record, Map<String, Object> responseMap) {
        List<Map<String, Object>> clips = extractClips(responseMap);
        if (clips.isEmpty()) {
            return;
        }
        for (Map<String, Object> clip : clips) {
            String clipId = extractString(clip, "id");
            if (!StringUtils.hasText(clipId)) {
                continue;
            }
            AudioAsset existingAsset = audioAssetMapper.findByClipId(clipId);
            if (existingAsset == null) {
                AudioAsset asset = buildAudioAsset(record, clip);
                try {
                    audioAssetMapper.insert(asset);
                    log.info("创建音频资产成功: assetId={}, clipId={}, generationRecordId={}",
                            asset.getId(), asset.getClipId(), record.getId());
                } catch (DuplicateKeyException duplicateKeyException) {
                    log.warn("音频资产已存在，跳过重复创建: clipId={}", clipId);
                }
                continue;
            }
            if (needsAssetUpdate(existingAsset, clip, record)) {
                AudioAsset updateAsset = buildAudioAsset(record, clip);
                updateAsset.setId(existingAsset.getId());
                audioAssetMapper.update(updateAsset);
            }
        }
    }

    private void upsertUploadedClipAsset(AudioGenerationRecord record, AudioController.UploadedClipRequest uploadedClip) {
        Map<String, Object> clipMap = new LinkedHashMap<>();
        clipMap.put("id", uploadedClip.getClipId());
        clipMap.put("clip_id", uploadedClip.getClipId());
        clipMap.put("title", uploadedClip.getTitle());
        clipMap.put("audio_url", uploadedClip.getAudioUrl());
        clipMap.put("cover_url", uploadedClip.getCoverUrl());
        clipMap.put("video_url", uploadedClip.getVideoUrl());
        clipMap.put("prompt", uploadedClip.getPrompt());
        clipMap.put("tags", uploadedClip.getTags());
        clipMap.put("model_name", uploadedClip.getModelName());
        clipMap.put("status", uploadedClip.getStatus());
        clipMap.put("created_at", uploadedClip.getCreatedAt());
        if (uploadedClip.getRawPayload() != null && !uploadedClip.getRawPayload().isEmpty()) {
            clipMap.putAll(uploadedClip.getRawPayload());
            clipMap.put("raw_payload", uploadedClip.getRawPayload());
        }

        AudioAsset existingAsset = audioAssetMapper.findByClipId(uploadedClip.getClipId());
        if (existingAsset == null) {
            AudioAsset asset = buildAudioAsset(record, clipMap);
            try {
                audioAssetMapper.insert(asset);
                log.info("前端直传回填音频资产成功: assetId={}, clipId={}, generationRecordId={}",
                        asset.getId(), asset.getClipId(), record.getId());
            } catch (DuplicateKeyException duplicateKeyException) {
                log.warn("前端直传回填音频资产已存在，跳过重复创建: clipId={}", uploadedClip.getClipId());
            }
            return;
        }
        if (needsAssetUpdate(existingAsset, clipMap, record)) {
            AudioAsset updateAsset = buildAudioAsset(record, clipMap, existingAsset.getId());
            audioAssetMapper.update(updateAsset);
        }
    }

    private AudioAsset buildAudioAsset(AudioGenerationRecord record, Map<String, Object> clip) {
        return buildAudioAsset(record, clip, null);
    }

    private AudioAsset buildAudioAsset(AudioGenerationRecord record, Map<String, Object> clip, Long assetId) {
        Map<String, Object> metadata = extractNestedMap(clip, "metadata");
        AudioAsset asset = new AudioAsset();
        asset.setId(assetId);
        asset.setUserId(record.getUserId());
        asset.setGenerationRecordId(record.getId());
        asset.setFolderId(0L);
        asset.setClipId(firstNonBlank(extractString(clip, "clip_id"), extractString(clip, "id")));
        asset.setTitle(firstNonBlank(extractString(clip, "title"), record.getTitle(), buildDefaultTitle(record)));
        asset.setAudioUrl(firstNonBlank(extractString(clip, "audio_url"), extractString(clip, "audioUrl")));
        asset.setVideoUrl(extractString(clip, "video_url"));
        asset.setCoverUrl(firstNonBlank(
                extractString(clip, "cover_url"),
                extractString(clip, "image_large_url"),
                extractString(clip, "image_url"),
                extractString(clip, "avatar_image_url")
        ));
        asset.setPrompt(firstNonBlank(extractString(clip, "prompt"), extractString(metadata, "prompt"), record.getPrompt()));
        asset.setTags(firstNonBlank(extractString(clip, "tags"), extractString(metadata, "tags"), record.getTags()));
        asset.setModel(firstNonBlank(extractString(clip, "model_name"), record.getModel()));
        asset.setSourceType(record.getRequestType());
        asset.setStatus(firstNonBlank(extractString(clip, "status"), record.getStatus()));
        asset.setRawPayload(firstNonBlank(toJson(clip.get("raw_payload")), toJson(clip)));
        asset.setCreatedAt(resolveAssetCreatedAt(clip, record));
        return asset;
    }

    private boolean needsAssetUpdate(AudioAsset existingAsset, Map<String, Object> clip, AudioGenerationRecord record) {
        AudioAsset latest = buildAudioAsset(record, clip, existingAsset.getId());
        return !Objects.equals(existingAsset.getTitle(), latest.getTitle())
                || !Objects.equals(existingAsset.getAudioUrl(), latest.getAudioUrl())
                || !Objects.equals(existingAsset.getVideoUrl(), latest.getVideoUrl())
                || !Objects.equals(existingAsset.getCoverUrl(), latest.getCoverUrl())
                || !Objects.equals(existingAsset.getPrompt(), latest.getPrompt())
                || !Objects.equals(existingAsset.getTags(), latest.getTags())
                || !Objects.equals(existingAsset.getModel(), latest.getModel())
                || !Objects.equals(existingAsset.getStatus(), latest.getStatus())
                || !Objects.equals(existingAsset.getRawPayload(), latest.getRawPayload());
    }

    private List<Map<String, Object>> extractClips(Map<String, Object> responseMap) {
        Object clips = responseMap.get("clips");
        if (!(clips instanceof List<?>)) {
            clips = responseMap.get("data");
        }
        if (!(clips instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> clipMaps = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawMap) {
                clipMaps.add(asStringKeyMap(rawMap));
            }
        }
        return clipMaps;
    }

    private String resolveRequestType(AudioController.MusicRequest request) {
        return firstNonBlank(trimToNull(request.getTask()), "generate");
    }

    private String resolveStatus(Object response) {
        if (response instanceof Map<?, ?> rawMap) {
            Map<String, Object> normalized = normalizeResponseMap(asStringKeyMap(rawMap));
            return firstNonBlank(extractString(normalized, "status"), extractString(normalized, "state"), "SUBMITTED");
        }
        return "SUBMITTED";
    }

    private Integer resolveClipCount(Object response) {
        if (response instanceof Map<?, ?> rawMap) {
            return extractClips(normalizeResponseMap(asStringKeyMap(rawMap))).size();
        }
        return 0;
    }

    private String resolveFailReason(Object response) {
        if (response instanceof Map<?, ?> rawMap) {
            Map<String, Object> normalized = normalizeResponseMap(asStringKeyMap(rawMap));
            return firstNonBlank(
                    extractString(normalized, "failReason"),
                    extractString(normalized, "fail_reason"),
                    extractString(normalized, "error"),
                    extractString(normalized, "message")
            );
        }
        return firstNonBlank(
                extractString(response, "failReason"),
                extractString(response, "fail_reason"),
                extractString(response, "error"),
                extractString(response, "message")
        );
    }

    private String resolvePersistedStatus(AudioGenerationRecord record, Map<String, Object> responseMap) {
        String upstreamStatus = firstNonBlank(resolveStatus(responseMap), record.getStatus(), "SUBMITTED");
        if (isSuccessStatus(upstreamStatus)) {
            return isSuccessStatus(record.getStatus()) ? "SUCCESS" : "UPLOADING";
        }
        return upstreamStatus.trim().toUpperCase(Locale.ROOT);
    }

    private LocalDateTime resolveStartTime(AudioGenerationRecord record, Map<String, Object> responseMap) {
        if (record.getStartTime() != null) {
            return record.getStartTime();
        }
        String status = resolveStatus(responseMap);
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "RUNNING", "PROCESSING", "STREAMING", "COMPLETE", "COMPLETED", "SUCCESS", "SUCCEEDED" -> LocalDateTime.now();
            default -> null;
        };
    }

    private LocalDateTime resolveFinishTime(Map<String, Object> responseMap) {
        String status = resolveStatus(responseMap);
        if (isSuccessStatus(status) || isFailureStatus(status)) {
            return LocalDateTime.now();
        }
        return null;
    }

    private LocalDateTime resolveAssetCreatedAt(Map<String, Object> clip, AudioGenerationRecord record) {
        String createdAt = extractString(clip, "created_at");
        if (StringUtils.hasText(createdAt)) {
            try {
                return OffsetDateTime.parse(createdAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch (Exception ignored) {
            }
        }
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

    private boolean isSuccessStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "SUCCEEDED", "COMPLETE", "COMPLETED", "DONE", "FINISHED" -> true;
            default -> false;
        };
    }

    private boolean isFailureStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "FAILED", "FAILURE", "ERROR", "CANCELLED" -> true;
            default -> false;
        };
    }

    private String buildDefaultTitle(AudioGenerationRecord record) {
        String title = trimToNull(record.getTitle());
        if (StringUtils.hasText(title)) {
            return title;
        }
        String prompt = trimToNull(record.getPrompt());
        if (StringUtils.hasText(prompt)) {
            return prompt.length() > 30 ? prompt.substring(0, 30) + "..." : prompt;
        }
        LocalDateTime time = record.getCreatedAt() == null ? LocalDateTime.now() : record.getCreatedAt();
        return "音频作品-" + time.format(TITLE_TIME_FORMATTER);
    }

    private Map<String, Object> extractNestedMap(Map<String, Object> source, String key) {
        Object nested = source.get(key);
        if (nested instanceof Map<?, ?> rawMap) {
            return asStringKeyMap(rawMap);
        }
        return Map.of();
    }

    private Map<String, Object> normalizeResponseMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        if (source.get("data") instanceof Map<?, ?> rawNestedMap) {
            Map<String, Object> nested = asStringKeyMap(rawNestedMap);
            if (looksLikeTaskPayload(nested)) {
                Map<String, Object> merged = new LinkedHashMap<>(nested);
                mergeRootMetadata(merged, source);
                return normalizeResponseMap(merged);
            }
        }
        return source;
    }

    private boolean looksLikeTaskPayload(Map<String, Object> payload) {
        return payload.containsKey("task_id")
                || payload.containsKey("platform")
                || payload.containsKey("action")
                || payload.containsKey("status")
                || payload.containsKey("state")
                || payload.get("clips") instanceof List<?>
                || payload.get("data") instanceof List<?>;
    }

    private void mergeRootMetadata(Map<String, Object> target, Map<String, Object> source) {
        putIfAbsent(target, "taskId", source.get("taskId"));
        putIfAbsent(target, "providerTaskId", source.get("providerTaskId"));
        putIfAbsent(target, "vendor", source.get("vendor"));
        putIfAbsent(target, "model", source.get("model"));
        putIfAbsent(target, "recordStatus", source.get("recordStatus"));
        putIfAbsent(target, "assetReady", source.get("assetReady"));
    }

    private void putIfAbsent(Map<String, Object> target, String key, Object value) {
        if (!target.containsKey(key) && value != null) {
            target.put(key, value);
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            log.warn("序列化音频对象失败", exception);
            return null;
        }
    }

    private Map<String, Object> asStringKeyMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String extractString(Object source, String key) {
        if (!(source instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Object value = rawMap.get(key);
        return value == null ? null : trimToNull(String.valueOf(value));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
