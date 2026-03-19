package com.wwt.pixel.image.mapper;

import com.wwt.pixel.image.domain.AssetFolder;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 资产文件夹 Mapper
 */
@Mapper
public interface AssetFolderMapper {

    @Insert("""
        INSERT INTO asset_folder (user_id, parent_id, folder_name, sort_order, created_at, updated_at)
        VALUES (#{userId}, #{parentId}, #{folderName}, #{sortOrder}, NOW(), NOW())
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AssetFolder folder);

    @Select("""
        SELECT * FROM asset_folder
        WHERE user_id = #{userId}
        ORDER BY sort_order ASC, created_at ASC, id ASC
        """)
    List<AssetFolder> findByUserId(Long userId);

    @Select("""
        SELECT * FROM asset_folder
        WHERE id = #{id}
          AND user_id = #{userId}
        LIMIT 1
        """)
    AssetFolder findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Update("""
        UPDATE asset_folder
        SET folder_name = #{folderName}, updated_at = NOW()
        WHERE id = #{id}
          AND user_id = #{userId}
        """)
    int updateName(@Param("id") Long id, @Param("userId") Long userId, @Param("folderName") String folderName);

    @Delete("""
        DELETE FROM asset_folder
        WHERE id = #{id}
          AND user_id = #{userId}
        """)
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
        SELECT COUNT(*) FROM asset_folder
        WHERE user_id = #{userId}
          AND parent_id = #{parentId}
        """)
    int countByParentIdAndUserId(@Param("userId") Long userId, @Param("parentId") Long parentId);
}
