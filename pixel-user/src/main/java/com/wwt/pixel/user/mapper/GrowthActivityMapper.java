package com.wwt.pixel.user.mapper;

import com.wwt.pixel.user.domain.GrowthActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户增长活动 Mapper
 */
@Mapper
public interface GrowthActivityMapper {

    @Select("""
        SELECT * FROM growth_activity
        WHERE trigger_type = #{triggerType}
          AND status = 1
          AND (start_time IS NULL OR start_time <= NOW())
          AND (end_time IS NULL OR end_time >= NOW())
        ORDER BY priority DESC, id ASC
        LIMIT 1
        """)
    GrowthActivity findActiveByTriggerType(String triggerType);

    @Select("SELECT * FROM growth_activity WHERE id = #{id}")
    GrowthActivity findById(Long id);
}
