package com.wwt.pixel.infrastructure.persistence.mapper;

import com.wwt.pixel.domain.model.UserBehavior;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户行为Mapper
 */
@Mapper
public interface UserBehaviorMapper {

    @Insert("""
        INSERT INTO user_behavior (user_id, behavior_type, target_type, target_id, extra_data)
        VALUES (#{userId}, #{behaviorType}, #{targetType}, #{targetId}, #{extraData, typeHandler=com.wwt.pixel.infrastructure.persistence.handler.JsonTypeHandler})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserBehavior behavior);

    @Select("""
        SELECT * FROM user_behavior
        WHERE user_id = #{userId}
        ORDER BY created_at DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<UserBehavior> findByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
        SELECT * FROM user_behavior
        WHERE user_id = #{userId} AND behavior_type = #{behaviorType}
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<UserBehavior> findByUserIdAndType(@Param("userId") Long userId,
                                           @Param("behaviorType") String behaviorType,
                                           @Param("limit") int limit);

    @Select("""
        SELECT target_id, COUNT(*) as cnt
        FROM user_behavior
        WHERE user_id = #{userId} AND behavior_type = #{behaviorType} AND target_type = #{targetType}
        GROUP BY target_id
        ORDER BY cnt DESC
        LIMIT #{limit}
        """)
    List<java.util.Map<String, Object>> findTopTargets(@Param("userId") Long userId,
                                                       @Param("behaviorType") String behaviorType,
                                                       @Param("targetType") String targetType,
                                                       @Param("limit") int limit);
}