package com.wwt.pixel.video.mapper;

import com.wwt.pixel.video.domain.VideoGenerationRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VideoGenerationRecordMapper {

    @Insert("""
        INSERT INTO video_generation_record (
            user_id, task_id, provider_task_id, request_type, prompt, source_images,
            vendor, model, aspect_ratio, duration, cost, hd, notify_hook, watermark, private_mode,
            status, progress, result_video_url, cover_url, fail_reason,
            submit_time, start_time, finish_time, created_at, updated_at
        ) VALUES (
            #{userId}, #{taskId}, #{providerTaskId}, #{requestType}, #{prompt}, #{sourceImages},
            #{vendor}, #{model}, #{aspectRatio}, #{duration}, #{cost}, #{hd}, #{notifyHook}, #{watermark}, #{privateMode},
            #{status}, #{progress}, #{resultVideoUrl}, #{coverUrl}, #{failReason},
            #{submitTime}, #{startTime}, #{finishTime}, NOW(), NOW()
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(VideoGenerationRecord record);

    @Select("""
        SELECT * FROM video_generation_record
        WHERE task_id = #{taskId}
        LIMIT 1
        """)
    VideoGenerationRecord findByTaskId(String taskId);

    @Select("""
        SELECT * FROM video_generation_record
        WHERE provider_task_id = #{providerTaskId}
        LIMIT 1
        """)
    VideoGenerationRecord findByProviderTaskId(String providerTaskId);

    @Update("""
        UPDATE video_generation_record
        SET status = #{status},
            progress = COALESCE(#{progress}, progress),
            result_video_url = COALESCE(#{resultVideoUrl}, result_video_url),
            cover_url = COALESCE(#{coverUrl}, cover_url),
            fail_reason = COALESCE(#{failReason}, fail_reason),
            submit_time = COALESCE(#{submitTime}, submit_time),
            start_time = COALESCE(#{startTime}, start_time),
            finish_time = COALESCE(#{finishTime}, finish_time),
            vendor = COALESCE(#{vendor}, vendor),
            model = COALESCE(#{model}, model),
            updated_at = NOW()
        WHERE id = #{id}
        """)
    int updateTaskResult(VideoGenerationRecord record);
}
