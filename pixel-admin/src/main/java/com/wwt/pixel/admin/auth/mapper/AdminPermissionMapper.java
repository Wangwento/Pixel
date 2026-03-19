package com.wwt.pixel.admin.auth.mapper;

import com.wwt.pixel.admin.domain.AdminPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

@Mapper
public interface AdminPermissionMapper {

    @Select("SELECT * FROM permission ORDER BY sort_order")
    List<AdminPermission> findAll();

    @Select("SELECT DISTINCT p.permission_code FROM permission p " +
            "INNER JOIN role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    Set<String> findPermissionCodesByUserId(Long userId);
}
