package com.wwt.pixel.image.mapper;

import com.wwt.pixel.image.domain.StyleTemplate;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 风格模板Mapper
 */
@Mapper
public interface StyleTemplateMapper {

    @Select("SELECT * FROM style_template WHERE status = 1 ORDER BY sort_order")
    List<StyleTemplate> findAll();

    @Select("SELECT * FROM style_template WHERE id = #{id}")
    StyleTemplate findById(Long id);

    @Select("SELECT * FROM style_template WHERE name_en = #{nameEn} AND status = 1")
    StyleTemplate findByNameEn(String nameEn);

    @Select("SELECT * FROM style_template WHERE category = #{category} AND status = 1 ORDER BY sort_order")
    List<StyleTemplate> findByCategory(String category);
}