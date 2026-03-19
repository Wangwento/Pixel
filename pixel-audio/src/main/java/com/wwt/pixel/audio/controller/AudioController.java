package com.wwt.pixel.audio.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wwt.pixel.audio.dto.AudioModelInfo;
import com.wwt.pixel.audio.service.AudioAssetService;
import com.wwt.pixel.audio.service.AudioService;
import com.wwt.pixel.common.dto.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/audio")
@RequiredArgsConstructor
public class AudioController {

    private final AudioService audioService;
    private final AudioAssetService audioAssetService;

    @GetMapping("/models")
    public Result<List<AudioModelInfo>> getModels(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(audioService.getModels());
    }

    @PostMapping("/music")
    public Result<Object> submitMusic(@RequestHeader("X-User-Id") Long userId,
                                      @Valid @RequestBody MusicRequest request) {
        return Result.success(audioService.submitMusic(userId, request));
    }

    @PostMapping("/lyrics")
    public Result<Object> submitLyrics(@Valid @RequestBody LyricsRequest request) {
        return Result.success(audioService.submitLyrics(request));
    }

    @PostMapping("/concat")
    public Result<Object> submitConcat(@Valid @RequestBody ConcatRequest request) {
        return Result.success(audioService.submitConcat(request));
    }

    @PostMapping("/uploads/audio")
    public Result<Object> createUpload(@Valid @RequestBody CreateUploadRequest request) {
        return Result.success(audioService.createUpload(request));
    }

    @PostMapping("/uploads/audio-url")
    public Result<Object> uploadByUrl(@Valid @RequestBody UploadByUrlRequest request) {
        return Result.success(audioService.uploadByUrl(request));
    }

    @PostMapping("/uploads/audio/{id}/finish")
    public Result<Object> finishUpload(@PathVariable("id") String id,
                                       @Valid @RequestBody FinishUploadRequest request) {
        return Result.success(audioService.finishUpload(id, request));
    }

    @GetMapping("/uploads/audio/{id}")
    public Result<Object> getUpload(@PathVariable("id") String id) {
        return Result.success(audioService.getUpload(id));
    }

    @PostMapping("/uploads/audio/{id}/initialize-clip")
    public Result<Object> initializeClip(@PathVariable("id") String id) {
        return Result.success(audioService.initializeClip(id));
    }

    @PostMapping("/persona")
    public Result<Object> createPersona(@Valid @RequestBody PersonaRequest request) {
        return Result.success(audioService.createPersona(request));
    }

    @PostMapping("/tags")
    public Result<Object> expandTags(@Valid @RequestBody TagsRequest request) {
        return Result.success(audioService.expandTags(request));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<Object> getTask(@PathVariable String taskId,
                                  @RequestParam(value = "action", required = false) String action) {
        return Result.success(audioService.getTask(taskId, action));
    }

    @PostMapping("/tasks/batch")
    public Result<Object> batchFetch(@Valid @RequestBody BatchFetchRequest request) {
        return Result.success(audioService.batchFetch(request));
    }

    @PostMapping("/oss/direct-upload-policy")
    public Result<Object> getDirectUploadPolicy(@RequestHeader("X-User-Id") Long userId,
                                                @Valid @RequestBody DirectUploadPolicyRequest request) {
        return Result.success(audioService.getDirectUploadPolicy(userId, request));
    }

    @PostMapping("/tasks/{taskId}/assets/finalize")
    public Result<Object> finalizeTaskAssets(@RequestHeader("X-User-Id") Long userId,
                                             @PathVariable String taskId,
                                             @Valid @RequestBody FinalizeTaskAssetsRequest request) {
        return Result.success(audioService.finalizeTaskAssets(userId, taskId, request));
    }

    @GetMapping("/timing/{clipId}")
    public Result<Object> getTiming(@PathVariable String clipId) {
        return Result.success(audioService.getTiming(clipId));
    }

    @GetMapping("/wav/{clipId}")
    public Result<Object> getWav(@PathVariable String clipId) {
        return Result.success(audioService.getWav(clipId));
    }

    // ── 音频资产管理 ──

    @GetMapping("/assets")
    public Result<Object> getAssets(@RequestHeader("X-User-Id") Long userId,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer pageSize,
                                    @RequestParam(required = false) Long folderId,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String startDate,
                                    @RequestParam(required = false) String endDate) {
        return Result.success(audioAssetService.getAssets(userId, page, pageSize, folderId, keyword, startDate, endDate));
    }

    @PutMapping("/assets/{id}/title")
    public Result<Void> updateAssetTitle(@RequestHeader("X-User-Id") Long userId,
                                         @PathVariable Long id,
                                         @RequestBody Map<String, String> body) {
        audioAssetService.updateTitle(userId, id, body.get("title"));
        return Result.success(null);
    }

    @PostMapping("/assets/move")
    public Result<Void> moveAssets(@RequestHeader("X-User-Id") Long userId,
                                   @RequestBody AssetBatchRequest body) {
        audioAssetService.moveAssets(userId, body.getAssetIds(), body.getFolderId());
        return Result.success(null);
    }

    @PostMapping("/assets/delete")
    public Result<Void> deleteAssets(@RequestHeader("X-User-Id") Long userId,
                                     @RequestBody AssetBatchRequest body) {
        audioAssetService.deleteAssets(userId, body.getAssetIds());
        return Result.success(null);
    }

    @Data
    public static class AssetBatchRequest {
        private List<Long> assetIds;
        private Long folderId;
    }

    @Data
    public static class MusicRequest {
        private String prompt;
        @NotBlank(message = "mv 不能为空")
        private String mv;
        @NotBlank(message = "title 不能为空")
        private String title;
        private String tags;
        private Integer continueAt;
        private String continueClipId;
        private String task;
        private Boolean makeInstrumental;
        private String generationType;
        private String negativeTags;
        private String personaId;
        private String artistClipId;
        private String continuedAlignedPrompt;
        private Integer stemTypeId;
        private String stemTypeGroupName;
        private String stemTask;

        @JsonProperty("continue_at")
        public Integer getContinueAt() {
            return continueAt;
        }

        @JsonProperty("continue_clip_id")
        public String getContinueClipId() {
            return continueClipId;
        }

        @JsonProperty("make_instrumental")
        public Boolean getMakeInstrumental() {
            return makeInstrumental;
        }

        @JsonProperty("generation_type")
        public String getGenerationType() {
            return generationType;
        }

        @JsonProperty("negative_tags")
        public String getNegativeTags() {
            return negativeTags;
        }

        @JsonProperty("persona_id")
        public String getPersonaId() {
            return personaId;
        }

        @JsonProperty("artist_clip_id")
        public String getArtistClipId() {
            return artistClipId;
        }

        @JsonProperty("continued_aligned_prompt")
        public String getContinuedAlignedPrompt() {
            return continuedAlignedPrompt;
        }

        @JsonProperty("stem_type_id")
        public Integer getStemTypeId() {
            return stemTypeId;
        }

        @JsonProperty("stem_type_group_name")
        public String getStemTypeGroupName() {
            return stemTypeGroupName;
        }

        @JsonProperty("stem_task")
        public String getStemTask() {
            return stemTask;
        }
    }

    @Data
    public static class LyricsRequest {
        @NotBlank(message = "prompt 不能为空")
        private String prompt;
        @JsonProperty("notify_hook")
        private String notifyHook;
    }

    @Data
    public static class ConcatRequest {
        @NotBlank(message = "clip_id 不能为空")
        @JsonProperty("clip_id")
        private String clipId;
        @JsonProperty("is_infill")
        private Boolean isInfill;
    }

    @Data
    public static class CreateUploadRequest {
        @NotBlank(message = "extension 不能为空")
        private String extension;
    }

    @Data
    public static class UploadByUrlRequest {
        @NotBlank(message = "url 不能为空")
        private String url;
    }

    @Data
    public static class FinishUploadRequest {
        @NotBlank(message = "upload_type 不能为空")
        @JsonProperty("upload_type")
        private String uploadType;
        @NotBlank(message = "upload_filename 不能为空")
        @JsonProperty("upload_filename")
        private String uploadFilename;
    }

    @Data
    public static class PersonaRequest {
        @NotBlank(message = "root_clip_id 不能为空")
        @JsonProperty("root_clip_id")
        private String rootClipId;
        @NotBlank(message = "name 不能为空")
        private String name;
        @NotBlank(message = "description 不能为空")
        private String description;
        @NotEmpty(message = "clips 不能为空")
        private List<String> clips;
        @JsonProperty("is_public")
        private Boolean isPublic;
    }

    @Data
    public static class TagsRequest {
        @NotBlank(message = "original_tags 不能为空")
        @JsonProperty("original_tags")
        private String originalTags;
    }

    @Data
    public static class BatchFetchRequest {
        @NotEmpty(message = "ids 不能为空")
        private List<String> ids;
        private String action;
    }

    @Data
    public static class DirectUploadPolicyRequest {
        @NotBlank(message = "task_id 不能为空")
        @JsonProperty("task_id")
        private String taskId;

        @NotBlank(message = "clip_id 不能为空")
        @JsonProperty("clip_id")
        private String clipId;

        @NotBlank(message = "asset_type 不能为空")
        @JsonProperty("asset_type")
        private String assetType;

        @NotBlank(message = "file_name 不能为空")
        @JsonProperty("file_name")
        private String fileName;

        @JsonProperty("content_type")
        private String contentType;
    }

    @Data
    public static class FinalizeTaskAssetsRequest {
        private String status;

        @JsonProperty("fail_reason")
        private String failReason;

        @JsonProperty("result_count")
        private Integer resultCount;

        @JsonProperty("make_instrumental")
        private Boolean makeInstrumental;

        @JsonProperty("response_payload")
        private Object responsePayload;

        @NotNull(message = "clips 不能为空")
        private List<UploadedClipRequest> clips;
    }

    @Data
    public static class UploadedClipRequest {
        @NotBlank(message = "clip_id 不能为空")
        @JsonProperty("clip_id")
        private String clipId;

        private String title;

        @JsonProperty("audio_url")
        private String audioUrl;

        @JsonProperty("cover_url")
        private String coverUrl;

        @JsonProperty("video_url")
        private String videoUrl;

        private String prompt;
        private String tags;

        @JsonProperty("model_name")
        private String modelName;

        private String status;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("raw_payload")
        private Map<String, Object> rawPayload;
    }
}
