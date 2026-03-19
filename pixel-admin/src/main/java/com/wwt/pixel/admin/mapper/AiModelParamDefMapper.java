package com.wwt.pixel.admin.mapper;

import com.wwt.pixel.admin.domain.AiModelParamDef;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiModelParamDefMapper {

    @Select("SELECT * FROM ai_model_param_def WHERE model_id = #{modelId} ORDER BY display_order")
    List<AiModelParamDef> findByModelId(Long modelId);

    @Insert("""
        INSERT INTO ai_model_param_def (model_id, param_key, param_name, param_type, required, visible,
        default_value, options, validation_rule, description, display_order)
        VALUES (#{modelId}, #{paramKey}, #{paramName}, #{paramType}, #{required}, #{visible},
        #{defaultValue}, #{options}, #{validationRule}, #{description}, #{displayOrder})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiModelParamDef paramDef);

    @Update("""
        UPDATE ai_model_param_def SET param_key = #{paramKey}, param_name = #{paramName}, param_type = #{paramType},
        required = #{required}, visible = #{visible}, default_value = #{defaultValue}, options = #{options},
        validation_rule = #{validationRule}, description = #{description}, display_order = #{displayOrder}
        WHERE id = #{id}
        """)
    int update(AiModelParamDef paramDef);

    @Delete("DELETE FROM ai_model_param_def WHERE id = #{id}")
    int delete(Long id);
}