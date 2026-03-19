package com.wwt.pixel.image.mapper;

import com.wwt.pixel.image.domain.ImageAsset;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 图片资产 Mapper
 */
@Mapper
public interface ImageAssetMapper {

    @Insert("""
        INSERT INTO image_asset (
            user_id, generation_record_id, image_index, folder_id, title, image_url,
            prompt, style, source_type, created_at, updated_at
        ) VALUES (
            #{userId}, #{generationRecordId}, #{imageIndex}, #{folderId}, #{title}, #{imageUrl},
            #{prompt}, #{style}, #{sourceType}, #{createdAt}, NOW()
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ImageAsset asset);

    @Select("""
        SELECT * FROM image_asset
        WHERE generation_record_id = #{generationRecordId}
        ORDER BY image_index ASC, id ASC
        """)
    List<ImageAsset> findByGenerationRecordIdOrderByImageIndex(Long generationRecordId);

    @Select("""
        SELECT * FROM image_asset
        WHERE id = #{id}
          AND user_id = #{userId}
        LIMIT 1
        """)
    ImageAsset findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
        <script>
        SELECT * FROM image_asset
        WHERE user_id = #{userId}
        <if test='folderId != null'>
          AND folder_id = #{folderId}
        </if>
        <if test='keyword != null and keyword != ""'>
          AND (
            title LIKE CONCAT('%', #{keyword}, '%')
            OR prompt LIKE CONCAT('%', #{keyword}, '%')
          )
        </if>
        <if test='startDate != null and startDate != ""'>
          AND created_at <![CDATA[>=]]> CONCAT(#{startDate}, ' 00:00:00')
        </if>
        <if test='endDate != null and endDate != ""'>
          AND created_at <![CDATA[<=]]> CONCAT(#{endDate}, ' 23:59:59')
        </if>
        ORDER BY created_at DESC, id DESC
        LIMIT #{offset}, #{limit}
        </script>
        """)
    List<ImageAsset> findByUserIdWithPaging(@Param("userId") Long userId,
                                            @Param("folderId") Long folderId,
                                            @Param("keyword") String keyword,
                                            @Param("startDate") String startDate,
                                            @Param("endDate") String endDate,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    @Select("""
        <script>
        SELECT COUNT(*) FROM image_asset
        WHERE user_id = #{userId}
        <if test='folderId != null'>
          AND folder_id = #{folderId}
        </if>
        <if test='keyword != null and keyword != ""'>
          AND (
            title LIKE CONCAT('%', #{keyword}, '%')
            OR prompt LIKE CONCAT('%', #{keyword}, '%')
          )
        </if>
        <if test='startDate != null and startDate != ""'>
          AND created_at <![CDATA[>=]]> CONCAT(#{startDate}, ' 00:00:00')
        </if>
        <if test='endDate != null and endDate != ""'>
          AND created_at <![CDATA[<=]]> CONCAT(#{endDate}, ' 23:59:59')
        </if>
        </script>
        """)
    int countByUserId(@Param("userId") Long userId,
                      @Param("folderId") Long folderId,
                      @Param("keyword") String keyword,
                      @Param("startDate") String startDate,
                      @Param("endDate") String endDate);

    @Update("""
        UPDATE image_asset
        SET title = #{title}, updated_at = NOW()
        WHERE id = #{id}
          AND user_id = #{userId}
        """)
    int updateTitle(@Param("id") Long id, @Param("userId") Long userId, @Param("title") String title);

    @Update("""
        <script>
        UPDATE image_asset
        SET folder_id = #{folderId}, updated_at = NOW()
        WHERE user_id = #{userId}
          AND id IN
          <foreach collection='assetIds' item='assetId' open='(' separator=',' close=')'>
            #{assetId}
          </foreach>
        </script>
        """)
    int moveAssets(@Param("userId") Long userId,
                   @Param("folderId") Long folderId,
                   @Param("assetIds") List<Long> assetIds);

    @Delete("""
        <script>
        DELETE FROM image_asset
        WHERE user_id = #{userId}
          AND id IN
          <foreach collection='assetIds' item='assetId' open='(' separator=',' close=')'>
            #{assetId}
          </foreach>
        </script>
        """)
    int deleteAssets(@Param("userId") Long userId, @Param("assetIds") List<Long> assetIds);

    @Select("""
        <script>
        SELECT COUNT(*) FROM image_asset
        WHERE user_id = #{userId}
          AND id IN
          <foreach collection='assetIds' item='assetId' open='(' separator=',' close=')'>
            #{assetId}
          </foreach>
        </script>
        """)
    int countByIdsAndUserId(@Param("userId") Long userId, @Param("assetIds") List<Long> assetIds);

    @Select("""
        SELECT COUNT(*) FROM image_asset
        WHERE user_id = #{userId}
          AND folder_id = #{folderId}
        """)
    int countByUserIdAndFolderId(@Param("userId") Long userId, @Param("folderId") Long folderId);
}
