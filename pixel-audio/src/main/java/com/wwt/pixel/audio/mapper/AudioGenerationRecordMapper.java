package com.wwt.pixel.audio.mapper;

import com.wwt.pixel.audio.domain.AudioGenerationRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AudioGenerationRecordMapper {

    @Insert("""
        INSERT INTO audio_generation_record (
            user_id, task_id, provider_task_id, request_type, prompt, title, tags, continue_clip_id,
            vendor, model, cost, make_instrumental, request_payload, response_payload,
            status, result_count, fail_reason, submit_time, start_time, finish_time, created_at, updated_at
        ) VALUES (
            #{userId}, #{taskId}, #{providerTaskId}, #{requestType}, #{prompt}, #{title}, #{tags}, #{continueClipId},
            #{vendor}, #{model}, #{cost}, #{makeInstrumental}, #{requestPayload}, #{responsePayload},
            #{status}, #{resultCount}, #{failReason}, #{submitTime}, #{startTime}, #{finishTime}, NOW(), NOW()
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AudioGenerationRecord record);

    @Select("""
        SELECT * FROM audio_generation_record
        WHERE task_id = #{taskId}
        LIMIT 1
        """)
    AudioGenerationRecord findByTaskId(String taskId);

    @Select("""
        SELECT * FROM audio_generation_record
        WHERE provider_task_id = #{providerTaskId}
        LIMIT 1
        """)
    AudioGenerationRecord findByProviderTaskId(String providerTaskId);

    @Update("""
        UPDATE audio_generation_record
        SET status = #{status},
            response_payload = COALESCE(#{responsePayload}, response_payload),
            result_count = COALESCE(#{resultCount}, result_count),
            fail_reason = #{failReason},
            start_time = COALESCE(#{startTime}, start_time),
            finish_time = COALESCE(#{finishTime}, finish_time),
            make_instrumental = COALESCE(#{makeInstrumental}, make_instrumental),
            vendor = COALESCE(#{vendor}, vendor),
            model = COALESCE(#{model}, model),
            updated_at = NOW()
        WHERE id = #{id}
        """)
    int updateTaskResult(AudioGenerationRecord record);
}
