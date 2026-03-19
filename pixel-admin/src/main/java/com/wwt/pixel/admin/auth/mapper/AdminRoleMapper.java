package com.wwt.pixel.admin.auth.mapper;

import com.wwt.pixel.admin.domain.AdminRole;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AdminRoleMapper {

    @Select("SELECT * FROM role ORDER BY id")
    List<AdminRole> findAll();

    @Select("SELECT r.* FROM role r " +
            "INNER JOIN user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<AdminRole> findByUserId(Long userId);

    @Select("SELECT r.role_code FROM role r " +
            "INNER JOIN user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> findRoleCodesByUserId(Long userId);

    @Delete("DELETE FROM user_role WHERE user_id = #{userId}")
    void deleteUserRoles(Long userId);

    @Insert("<script>" +
            "INSERT INTO user_role (user_id, role_id) VALUES " +
            "<foreach collection='roleIds' item='roleId' separator=','>" +
            "(#{userId}, #{roleId})" +
            "</foreach>" +
            "</script>")
    void insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);
}
