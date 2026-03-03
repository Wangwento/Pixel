package com.wwt.pixel.infrastructure.persistence.mapper;

import com.wwt.pixel.domain.model.Tag;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 标签Mapper
 */
@Mapper
public interface TagMapper {

    @Select("SELECT * FROM tag WHERE id = #{id}")
    Tag findById(Long id);

    @Select("SELECT * FROM tag WHERE name_en = #{nameEn}")
    Tag findByNameEn(String nameEn);

    @Select("SELECT * FROM tag WHERE status = 1 ORDER BY sort_order")
    List<Tag> findAll();

    @Select("SELECT * FROM tag WHERE category = #{category} AND status = 1 ORDER BY sort_order")
    List<Tag> findByCategory(String category);

    @Select("SELECT * FROM tag WHERE id IN (#{ids}) AND status = 1")
    List<Tag> findByIds(@Param("ids") List<Long> ids);

    @Insert("""
        INSERT INTO tag (name, name_en, category, parent_id, icon, color, sort_order, status)
        VALUES (#{name}, #{nameEn}, #{category}, #{parentId}, #{icon}, #{color}, #{sortOrder}, #{status})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Tag tag);

    @Update("""
        UPDATE tag SET name = #{name}, name_en = #{nameEn}, category = #{category},
            parent_id = #{parentId}, icon = #{icon}, color = #{color}, sort_order = #{sortOrder}, status = #{status}
        WHERE id = #{id}
        """)
    int update(Tag tag);
}