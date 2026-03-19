package com.wwt.pixel.image.mapper;

import com.wwt.pixel.image.domain.GenerationRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 生成记录Mapper
 */
@Mapper
public interface GenerationRecordMapper {

    @Insert("INSERT INTO generation_record (user_id, prompt, negative_prompt, style, status, created_at) " +
            "VALUES (#{userId}, #{prompt}, #{negativePrompt}, #{style}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GenerationRecord record);

    @Update("UPDATE generation_record SET status = #{status}, result_image_url = #{resultImageUrl}, " +
            "vendor = #{vendor}, model = #{model}, cost = #{cost}, error_message = #{errorMessage} " +
            "WHERE id = #{id}")
    int updateStatus(GenerationRecord record);

    @Select("SELECT * FROM generation_record WHERE id = #{id}")
    GenerationRecord findById(Long id);

    @Select("SELECT * FROM generation_record WHERE user_id = #{userId} AND status = 1 " +
            "ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<GenerationRecord> findByUserIdWithPaging(@Param("userId") Long userId,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM generation_record WHERE user_id = #{userId} AND status = 1")
    int countByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM generation_record WHERE user_id = #{userId} AND status = 1 " +
            "AND created_at BETWEEN #{startDate} AND #{endDate} " +
            "ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<GenerationRecord> findByUserIdAndDateRange(@Param("userId") Long userId,
                                                     @Param("startDate") String startDate,
                                                     @Param("endDate") String endDate,
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM generation_record WHERE user_id = #{userId} AND status = 1 " +
            "AND created_at BETWEEN #{startDate} AND #{endDate}")
    int countByUserIdAndDateRange(@Param("userId") Long userId,
                                   @Param("startDate") String startDate,
                                   @Param("endDate") String endDate);
}
