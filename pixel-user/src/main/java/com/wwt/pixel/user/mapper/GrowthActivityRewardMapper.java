package com.wwt.pixel.user.mapper;

import com.wwt.pixel.user.domain.GrowthActivityReward;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户增长活动奖励 Mapper
 */
@Mapper
public interface GrowthActivityRewardMapper {

    @Select("""
        SELECT * FROM growth_activity_reward
        WHERE activity_id = #{activityId}
          AND status = 1
        ORDER BY sort_order ASC, id ASC
        """)
    List<GrowthActivityReward> findByActivityId(Long activityId);
}
