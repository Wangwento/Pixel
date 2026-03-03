package com.wwt.pixel.image.mapper;

import com.wwt.pixel.image.domain.GenerationRecord;
import org.apache.ibatis.annotations.*;

/**
 * 生成记录Mapper
 */
@Mapper
public interface GenerationRecordMapper {

    @Insert("INSERT INTO generation_record (user_id, prompt, style, status, created_at) " +
            "VALUES (#{userId}, #{prompt}, #{style}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GenerationRecord record);

    @Update("UPDATE generation_record SET status = #{status}, result_image_url = #{resultImageUrl}, " +
            "vendor = #{vendor}, model = #{model}, cost = #{cost}, error_message = #{errorMessage} " +
            "WHERE id = #{id}")
    int updateStatus(GenerationRecord record);

    @Select("SELECT * FROM generation_record WHERE id = #{id}")
    GenerationRecord findById(Long id);
}