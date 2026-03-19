package com.wwt.pixel.video.mapper;

import com.wwt.pixel.video.domain.AssetFolder;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AssetFolderMapper {

    @Select("SELECT * FROM asset_folder WHERE id = #{folderId}")
    AssetFolder findById(Long folderId);
}