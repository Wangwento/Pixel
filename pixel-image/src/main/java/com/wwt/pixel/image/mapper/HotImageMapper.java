package com.wwt.pixel.image.mapper;

import com.wwt.pixel.image.domain.HotImage;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 热门图片 Mapper
 */
@Mapper
public interface HotImageMapper {

    @Insert("""
        INSERT INTO hot_image (
            user_id, image_asset_id, video_asset_id, image_url, media_type, cover_url, title, description,
            status, reward_claimed, reward_points, created_at
        ) VALUES (
            #{userId}, #{imageAssetId}, #{videoAssetId}, #{imageUrl}, #{mediaType}, #{coverUrl}, #{title}, #{description},
            #{status}, #{rewardClaimed}, #{rewardPoints}, NOW()
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(HotImage hotImage);

    @Select("SELECT * FROM hot_image WHERE id = #{id}")
    HotImage findById(Long id);

    @Select("SELECT * FROM hot_image WHERE id = #{id} FOR UPDATE")
    HotImage findByIdForUpdate(Long id);

    @Select("""
        SELECT * FROM hot_image
        WHERE user_id = #{userId} AND image_asset_id = #{imageAssetId}
          AND status != 2
        LIMIT 1
        """)
    HotImage findByUserAndAssetNotRejected(@Param("userId") Long userId,
                                            @Param("imageAssetId") Long imageAssetId);

    @Select("""
        SELECT * FROM hot_image
        WHERE user_id = #{userId} AND video_asset_id = #{videoAssetId}
          AND status != 2
        LIMIT 1
        """)
    HotImage findByUserAndVideoAssetNotRejected(@Param("userId") Long userId,
                                                @Param("videoAssetId") Long videoAssetId);

    @Select("""
        SELECT * FROM hot_image
        WHERE user_id = #{userId}
        ORDER BY created_at DESC
        LIMIT #{offset}, #{limit}
        """)
    List<HotImage> findByUserId(@Param("userId") Long userId,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM hot_image WHERE user_id = #{userId}")
    int countByUserId(Long userId);

    /**
     * 用户通知: 已通过未领取
     */
    @Select("""
        SELECT * FROM hot_image
        WHERE user_id = #{userId} AND status = 1 AND reward_claimed = 0
        ORDER BY reviewed_at DESC
        """)
    List<HotImage> findApprovedUnclaimed(Long userId);

    /**
     * 用户通知: 已拒绝(近30天)
     */
    @Select("""
        SELECT * FROM hot_image
        WHERE user_id = #{userId} AND status = 2
          AND reviewed_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
        ORDER BY reviewed_at DESC
        """)
    List<HotImage> findRecentRejected(Long userId);

    /**
     * 公共热门列表 (已通过)
     */
    @Select("""
        SELECT * FROM hot_image
        WHERE status = 1
        ORDER BY reviewed_at DESC
        LIMIT #{offset}, #{limit}
        """)
    List<HotImage> findApproved(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM hot_image WHERE status = 1")
    int countApproved();

    /**
     * 管理端列表 (按status筛选)
     */
    @Select("""
        <script>
        SELECT * FROM hot_image
        <where>
          <if test='status != null'>
            AND status = #{status}
          </if>
        </where>
        ORDER BY created_at DESC
        LIMIT #{offset}, #{limit}
        </script>
        """)
    List<HotImage> findByStatusWithPaging(@Param("status") Integer status,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    @Select("""
        <script>
        SELECT COUNT(*) FROM hot_image
        <where>
          <if test='status != null'>
            AND status = #{status}
          </if>
        </where>
        </script>
        """)
    int countByStatus(@Param("status") Integer status);

    @Update("""
        UPDATE hot_image
        SET status = #{status}, reviewer_id = #{reviewerId}, reviewed_at = #{reviewedAt}
        WHERE id = #{id}
        """)
    int approve(@Param("id") Long id, @Param("status") int status,
                @Param("reviewerId") Long reviewerId, @Param("reviewedAt") LocalDateTime reviewedAt);

    @Update("""
        UPDATE hot_image
        SET status = #{status}, reject_reason = #{rejectReason},
            reviewer_id = #{reviewerId}, reviewed_at = #{reviewedAt}
        WHERE id = #{id}
        """)
    int reject(@Param("id") Long id, @Param("status") int status,
               @Param("rejectReason") String rejectReason,
               @Param("reviewerId") Long reviewerId, @Param("reviewedAt") LocalDateTime reviewedAt);

    @Update("""
        UPDATE hot_image
        SET reward_claimed = 1, claimed_at = #{claimedAt}
        WHERE id = #{id}
        """)
    int claimReward(@Param("id") Long id, @Param("claimedAt") LocalDateTime claimedAt);

    @Update("""
        UPDATE hot_image
        SET status = #{status}, reviewer_id = #{reviewerId}, reviewed_at = #{reviewedAt}
        WHERE id = #{id}
        """)
    int offline(@Param("id") Long id, @Param("status") int status,
                @Param("reviewerId") Long reviewerId, @Param("reviewedAt") LocalDateTime reviewedAt);

    @Delete("DELETE FROM hot_image WHERE id = #{id}")
    int deleteById(Long id);
}
