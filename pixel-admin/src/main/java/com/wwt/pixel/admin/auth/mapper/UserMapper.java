package com.wwt.pixel.admin.auth.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    @Select("SELECT id, username, password, nickname, avatar, email, phone, status, created_at, updated_at " +
            "FROM `user` WHERE username = #{username}")
    Map<String, Object> findByUsername(String username);

    @Select("SELECT id, username, nickname, avatar, email, phone, status, created_at, updated_at " +
            "FROM `user` WHERE id = #{id}")
    Map<String, Object> findById(Long id);

    @Select("SELECT id, username, password FROM `user` WHERE id = #{id}")
    Map<String, Object> findWithPasswordById(Long id);

    @Select("SELECT u.id, u.username, u.nickname, u.avatar, u.email, u.phone, u.status, " +
            "u.user_type, u.vip_level, u.vip_expire_time, u.created_at " +
            "FROM `user` u " +
            "INNER JOIN user_role ur ON u.id = ur.user_id " +
            "INNER JOIN role r ON ur.role_id = r.id " +
            "WHERE r.role_code IN ('super_admin', 'admin') " +
            "GROUP BY u.id ORDER BY u.created_at DESC")
    List<Map<String, Object>> findAdminUsers();

    @Update("UPDATE `user` SET password=#{password}, updated_at=NOW() WHERE id=#{id}")
    void updatePassword(@Param("id") Long id, @Param("password") String password);

    @Update("UPDATE `user` SET status=#{status}, updated_at=NOW() WHERE id=#{id}")
    void updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
