package com.wwt.pixel.user.mapper;

import com.wwt.pixel.user.domain.QuotaRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * 额度流水 Mapper
 */
@Mapper
public interface QuotaRecordMapper {

    @Insert("""
        INSERT INTO quota_record (user_id, quota, balance, type, source, description, created_at)
        VALUES (#{userId}, #{quota}, #{balance}, #{type}, #{source}, #{description}, NOW())
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(QuotaRecord record);
}
