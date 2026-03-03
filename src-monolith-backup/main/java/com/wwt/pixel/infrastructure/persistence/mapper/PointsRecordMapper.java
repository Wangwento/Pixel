package com.wwt.pixel.infrastructure.persistence.mapper;

import com.wwt.pixel.domain.model.PointsRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 积分记录Mapper
 */
@Mapper
public interface PointsRecordMapper {

    @Insert("""
        INSERT INTO points_record (user_id, points, balance, type, source, description)
        VALUES (#{userId}, #{points}, #{balance}, #{type}, #{source}, #{description})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PointsRecord record);

    @Select("SELECT * FROM points_record WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<PointsRecord> findByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM points_record WHERE user_id = #{userId}")
    int countByUserId(Long userId);

    @Select("SELECT * FROM points_record WHERE user_id = #{userId} AND type = #{type} ORDER BY created_at DESC LIMIT #{limit}")
    List<PointsRecord> findByUserIdAndType(@Param("userId") Long userId, @Param("type") Integer type, @Param("limit") int limit);
}