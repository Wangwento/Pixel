package com.wwt.pixel.admin.mapper;

import com.wwt.pixel.admin.domain.AiProvider;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiProviderMapper {

    @Select("SELECT * FROM ai_provider ORDER BY weight DESC, id DESC")
    List<AiProvider> findAll();

    @Select("SELECT * FROM ai_provider WHERE id = #{id}")
    AiProvider findById(Long id);

    @Select("SELECT * FROM ai_provider WHERE enabled = true ORDER BY weight DESC")
    List<AiProvider> findByEnabled(boolean enabled);

    @Insert("""
        INSERT INTO ai_provider (provider_code, provider_name, base_url, enabled, weight, description)
        VALUES (#{providerCode}, #{providerName}, #{baseUrl}, #{enabled}, #{weight}, #{description})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiProvider provider);

    @Update("""
        UPDATE ai_provider SET provider_name = #{providerName}, base_url = #{baseUrl},
        enabled = #{enabled}, weight = #{weight}, description = #{description}
        WHERE id = #{id}
        """)
    int update(AiProvider provider);

    @Delete("DELETE FROM ai_provider WHERE id = #{id}")
    int delete(Long id);
}