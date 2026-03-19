package com.wwt.pixel.admin.mapper;

import com.wwt.pixel.admin.domain.AiModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiModelMapper {

    @Select("SELECT * FROM ai_model WHERE provider_id = #{providerId}")
    List<AiModel> findByProviderId(Long providerId);

    @Select("SELECT * FROM ai_model WHERE id = #{id}")
    AiModel findById(Long id);

    @Insert("""
        INSERT INTO ai_model (provider_id, model_code, model_name, model_type, category, supports_image_input, api_key,
        enabled, min_vip_level, cost_per_unit, timeout_ms, retry_count, description)
        VALUES (#{providerId}, #{modelCode}, #{modelName}, #{modelType}, #{category}, #{supportsImageInput}, #{apiKey},
        #{enabled}, #{minVipLevel}, #{costPerUnit}, #{timeoutMs}, #{retryCount}, #{description})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiModel model);

    @Update("""
        UPDATE ai_model SET model_name = #{modelName}, category = #{category}, supports_image_input = #{supportsImageInput},
        api_key = #{apiKey}, enabled = #{enabled}, min_vip_level = #{minVipLevel}, cost_per_unit = #{costPerUnit},
        timeout_ms = #{timeoutMs}, retry_count = #{retryCount}, description = #{description}
        WHERE id = #{id}
        """)
    int update(AiModel model);

    @Delete("DELETE FROM ai_model WHERE id = #{id}")
    int delete(Long id);
}