package com.wwt.pixel.infrastructure.persistence.mapper;

import com.wwt.pixel.domain.model.UserTag;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户标签Mapper
 */
@Mapper
public interface UserTagMapper {

    @Select("""
        SELECT ut.*, t.name, t.name_en, t.category, t.icon, t.color
        FROM user_tag ut
        LEFT JOIN tag t ON ut.tag_id = t.id
        WHERE ut.user_id = #{userId}
        ORDER BY ut.weight DESC
        """)
    @Results({
        @Result(property = "tag.id", column = "tag_id"),
        @Result(property = "tag.name", column = "name"),
        @Result(property = "tag.nameEn", column = "name_en"),
        @Result(property = "tag.category", column = "category"),
        @Result(property = "tag.icon", column = "icon"),
        @Result(property = "tag.color", column = "color")
    })
    List<UserTag> findByUserId(Long userId);

    @Select("""
        SELECT ut.*, t.name, t.name_en, t.category, t.icon, t.color
        FROM user_tag ut
        LEFT JOIN tag t ON ut.tag_id = t.id
        WHERE ut.user_id = #{userId}
        ORDER BY ut.weight DESC
        LIMIT #{limit}
        """)
    @Results({
        @Result(property = "tag.id", column = "tag_id"),
        @Result(property = "tag.name", column = "name"),
        @Result(property = "tag.nameEn", column = "name_en"),
        @Result(property = "tag.category", column = "category"),
        @Result(property = "tag.icon", column = "icon"),
        @Result(property = "tag.color", column = "color")
    })
    List<UserTag> findTopByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT * FROM user_tag WHERE user_id = #{userId} AND tag_id = #{tagId}")
    UserTag findByUserIdAndTagId(@Param("userId") Long userId, @Param("tagId") Long tagId);

    @Insert("""
        INSERT INTO user_tag (user_id, tag_id, weight, source, use_count, last_use_time)
        VALUES (#{userId}, #{tagId}, #{weight}, #{source}, #{useCount}, #{lastUseTime})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserTag userTag);

    @Update("""
        UPDATE user_tag SET weight = #{weight}, use_count = #{useCount}, last_use_time = #{lastUseTime}
        WHERE id = #{id}
        """)
    int update(UserTag userTag);

    @Insert("""
        INSERT INTO user_tag (user_id, tag_id, weight, source, use_count, last_use_time)
        VALUES (#{userId}, #{tagId}, #{weight}, #{source}, #{useCount}, #{lastUseTime})
        ON DUPLICATE KEY UPDATE
            weight = #{weight}, use_count = #{useCount}, last_use_time = #{lastUseTime}
        """)
    int upsert(UserTag userTag);

    @Delete("DELETE FROM user_tag WHERE user_id = #{userId} AND tag_id = #{tagId}")
    int delete(@Param("userId") Long userId, @Param("tagId") Long tagId);

    @Delete("DELETE FROM user_tag WHERE user_id = #{userId} AND source = #{source}")
    int deleteBySource(@Param("userId") Long userId, @Param("source") Integer source);
}