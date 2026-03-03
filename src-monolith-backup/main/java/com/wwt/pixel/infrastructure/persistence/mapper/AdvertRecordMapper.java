package com.wwt.pixel.infrastructure.persistence.mapper;

import com.wwt.pixel.domain.model.AdvertRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 广告观看记录Mapper
 */
@Mapper
public interface AdvertRecordMapper {

    @Insert("""
        INSERT INTO advert_record (user_id, ad_type, ad_id, points_earned, duration, created_at)
        VALUES (#{userId}, #{adType}, #{adId}, #{pointsEarned}, #{duration}, #{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdvertRecord record);

    @Select("""
        SELECT COUNT(*) FROM advert_record
        WHERE user_id = #{userId} AND created_at >= #{startTime}
        """)
    int countTodayAds(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    @Select("""
        SELECT COALESCE(SUM(points_earned), 0) FROM advert_record
        WHERE user_id = #{userId} AND created_at >= #{startTime}
        """)
    int sumTodayPoints(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    @Select("""
        SELECT * FROM advert_record WHERE user_id = #{userId}
        ORDER BY created_at DESC LIMIT #{offset}, #{limit}
        """)
    List<AdvertRecord> findByUserId(@Param("userId") Long userId,
                                     @Param("offset") int offset, @Param("limit") int limit);
}