package com.wwt.pixel.user.mapper;

import com.wwt.pixel.user.domain.PointsRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * 积分流水 Mapper
 */
@Mapper
public interface PointsRecordMapper {

    @Insert("""
        INSERT INTO points_record (user_id, points, balance, type, source, description, created_at)
        VALUES (#{userId}, #{points}, #{balance}, #{type}, #{source}, #{description}, NOW())
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PointsRecord record);
}
