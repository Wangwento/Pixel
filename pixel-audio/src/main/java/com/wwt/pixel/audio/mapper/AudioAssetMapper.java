package com.wwt.pixel.audio.mapper;

import com.wwt.pixel.audio.domain.AudioAsset;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AudioAssetMapper {

    @Insert("""
        INSERT INTO audio_asset (
            user_id, generation_record_id, folder_id, clip_id, title, audio_url, video_url, cover_url,
            prompt, tags, model, source_type, status, raw_payload, created_at, updated_at
        ) VALUES (
            #{userId}, #{generationRecordId}, #{folderId}, #{clipId}, #{title}, #{audioUrl}, #{videoUrl}, #{coverUrl},
            #{prompt}, #{tags}, #{model}, #{sourceType}, #{status}, #{rawPayload}, #{createdAt}, NOW()
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AudioAsset asset);

    @Select("""
        SELECT * FROM audio_asset
        WHERE clip_id = #{clipId}
        LIMIT 1
        """)
    AudioAsset findByClipId(String clipId);

    @Update("""
        UPDATE audio_asset
        SET title = COALESCE(#{title}, title),
            audio_url = COALESCE(#{audioUrl}, audio_url),
            video_url = COALESCE(#{videoUrl}, video_url),
            cover_url = COALESCE(#{coverUrl}, cover_url),
            prompt = COALESCE(#{prompt}, prompt),
            tags = COALESCE(#{tags}, tags),
            model = COALESCE(#{model}, model),
            source_type = COALESCE(#{sourceType}, source_type),
            status = COALESCE(#{status}, status),
            raw_payload = COALESCE(#{rawPayload}, raw_payload),
            updated_at = NOW()
        WHERE id = #{id}
        """)
    int update(AudioAsset asset);

    int countByUser(@Param("userId") Long userId,
                    @Param("folderId") Long folderId,
                    @Param("keyword") String keyword,
                    @Param("startDate") String startDate,
                    @Param("endDate") String endDate);

    List<AudioAsset> selectByUser(@Param("userId") Long userId,
                                  @Param("folderId") Long folderId,
                                  @Param("keyword") String keyword,
                                  @Param("startDate") String startDate,
                                  @Param("endDate") String endDate,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    @Update("UPDATE audio_asset SET title = #{title}, updated_at = NOW() WHERE id = #{id} AND user_id = #{userId}")
    int updateTitle(@Param("id") Long id, @Param("userId") Long userId, @Param("title") String title);

    @Update("UPDATE audio_asset SET folder_id = #{folderId}, updated_at = NOW() WHERE id = #{id} AND user_id = #{userId}")
    int updateFolderId(@Param("id") Long id, @Param("userId") Long userId, @Param("folderId") Long folderId);

    @Delete("DELETE FROM audio_asset WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);
}
