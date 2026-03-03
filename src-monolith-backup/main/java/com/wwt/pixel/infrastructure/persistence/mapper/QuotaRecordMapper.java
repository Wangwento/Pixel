package com.wwt.pixel.infrastructure.persistence.mapper;

import com.wwt.pixel.domain.model.QuotaRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 额度记录Mapper
 */
@Mapper
public interface QuotaRecordMapper {

    @Insert("""
        INSERT INTO quota_record (user_id, quota, balance, type, source, description)
        VALUES (#{userId}, #{quota}, #{balance}, #{type}, #{source}, #{description})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(QuotaRecord record);

    @Select("SELECT * FROM quota_record WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<QuotaRecord> findByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM quota_record WHERE user_id = #{userId}")
    int countByUserId(Long userId);
}