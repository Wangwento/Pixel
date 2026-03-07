package com.wwt.pixel.user.mapper;

import com.wwt.pixel.user.domain.UserGrowthRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户增长活动记录 Mapper
 */
@Mapper
public interface UserGrowthRecordMapper {

    @Insert("""
        INSERT INTO user_growth_record (
            activity_id, user_id, biz_key, trigger_type, trigger_source,
            hit_status, reward_status, reward_snapshot, triggered_at, granted_at
        ) VALUES (
            #{activityId}, #{userId}, #{bizKey}, #{triggerType}, #{triggerSource},
            #{hitStatus}, #{rewardStatus}, #{rewardSnapshot}, #{triggeredAt}, #{grantedAt}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserGrowthRecord record);

    @Select("""
        SELECT * FROM user_growth_record
        WHERE user_id = #{userId}
        ORDER BY created_at DESC, id DESC
        """)
    List<UserGrowthRecord> findByUserId(Long userId);

    @Select("""
        SELECT * FROM user_growth_record
        WHERE activity_id = #{activityId}
          AND user_id = #{userId}
          AND biz_key = #{bizKey}
        LIMIT 1
        """)
    UserGrowthRecord findByActivityAndUserAndBizKey(@Param("activityId") Long activityId,
                                                    @Param("userId") Long userId,
                                                    @Param("bizKey") String bizKey);

    @Select("""
        SELECT * FROM user_growth_record
        WHERE activity_id = #{activityId}
          AND user_id = #{userId}
        ORDER BY id DESC
        LIMIT 1
        """)
    UserGrowthRecord findLatestByActivityAndUser(@Param("activityId") Long activityId,
                                                 @Param("userId") Long userId);

    @Select("""
        SELECT * FROM user_growth_record
        WHERE id = #{id}
        FOR UPDATE
        """)
    UserGrowthRecord findByIdForUpdate(Long id);

    @Update("""
        UPDATE user_growth_record
        SET reward_status = #{rewardStatus}, granted_at = #{grantedAt}, updated_at = NOW()
        WHERE id = #{id}
        """)
    int updateRewardStatus(@Param("id") Long id,
                           @Param("rewardStatus") Integer rewardStatus,
                           @Param("grantedAt") java.time.LocalDateTime grantedAt);
}
