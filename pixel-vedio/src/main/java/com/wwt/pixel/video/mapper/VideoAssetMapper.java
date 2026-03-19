package com.wwt.pixel.video.mapper;

import com.wwt.pixel.video.domain.VideoAsset;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface VideoAssetMapper {

    @Insert("""
        INSERT INTO video_asset (
            user_id, generation_record_id, folder_id, title, video_url, cover_url,
            prompt, duration, source_type, created_at, updated_at
        ) VALUES (
            #{userId}, #{generationRecordId}, #{folderId}, #{title}, #{videoUrl}, #{coverUrl},
            #{prompt}, #{duration}, #{sourceType}, #{createdAt}, NOW()
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(VideoAsset asset);

    @Select("""
        SELECT * FROM video_asset
        WHERE generation_record_id = #{generationRecordId}
        LIMIT 1
        """)
    VideoAsset findByGenerationRecordId(Long generationRecordId);

    @Update("""
        UPDATE video_asset
        SET video_url = COALESCE(#{videoUrl}, video_url),
            cover_url = COALESCE(#{coverUrl}, cover_url),
            title = COALESCE(#{title}, title),
            prompt = COALESCE(#{prompt}, prompt),
            duration = COALESCE(#{duration}, duration),
            updated_at = NOW()
        WHERE id = #{id}
        """)
    int update(VideoAsset asset);

    @Select("""
        <script>
        SELECT * FROM video_asset
        WHERE user_id = #{userId}
        <if test="folderId != null">
            AND folder_id = #{folderId}
        </if>
        <if test="keyword != null and keyword != ''">
            AND (title LIKE CONCAT('%', #{keyword}, '%') OR prompt LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        <if test="startDate != null and startDate != ''">
            AND created_at &gt;= #{startDate}
        </if>
        <if test="endDate != null and endDate != ''">
            AND created_at &lt;= #{endDate}
        </if>
        ORDER BY created_at DESC
        LIMIT #{offset}, #{pageSize}
        </script>
        """)
    List<VideoAsset> listAssets(@Param("userId") Long userId,
                                 @Param("folderId") Long folderId,
                                 @Param("keyword") String keyword,
                                 @Param("startDate") String startDate,
                                 @Param("endDate") String endDate,
                                 @Param("offset") int offset,
                                 @Param("pageSize") int pageSize);

    @Select("""
        <script>
        SELECT COUNT(*) FROM video_asset
        WHERE user_id = #{userId}
        <if test="folderId != null">
            AND folder_id = #{folderId}
        </if>
        <if test="keyword != null and keyword != ''">
            AND (title LIKE CONCAT('%', #{keyword}, '%') OR prompt LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        <if test="startDate != null and startDate != ''">
            AND created_at &gt;= #{startDate}
        </if>
        <if test="endDate != null and endDate != ''">
            AND created_at &lt;= #{endDate}
        </if>
        </script>
        """)
    int countAssets(@Param("userId") Long userId,
                    @Param("folderId") Long folderId,
                    @Param("keyword") String keyword,
                    @Param("startDate") String startDate,
                    @Param("endDate") String endDate);

    @Update("""
        UPDATE video_asset
        SET title = #{title}, updated_at = NOW()
        WHERE id = #{assetId} AND user_id = #{userId}
        """)
    int updateTitle(@Param("userId") Long userId, @Param("assetId") Long assetId, @Param("title") String title);

    @Update("""
        UPDATE video_asset
        SET folder_id = #{folderId}, updated_at = NOW()
        WHERE id IN
        <foreach collection="assetIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
        AND user_id = #{userId}
        """)
    int moveAssets(@Param("userId") Long userId, @Param("assetIds") List<Long> assetIds, @Param("folderId") Long folderId);

    @Delete("""
        DELETE FROM video_asset
        WHERE id IN
        <foreach collection="assetIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
        AND user_id = #{userId}
        """)
    int deleteAssets(@Param("userId") Long userId, @Param("assetIds") List<Long> assetIds);
}

