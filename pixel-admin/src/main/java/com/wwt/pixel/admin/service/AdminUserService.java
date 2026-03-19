package com.wwt.pixel.admin.service;

import com.wwt.pixel.admin.domain.AdminPermission;
import com.wwt.pixel.admin.domain.AdminRole;
import com.wwt.pixel.admin.auth.mapper.AdminPermissionMapper;
import com.wwt.pixel.admin.auth.mapper.AdminRoleMapper;
import com.wwt.pixel.admin.auth.mapper.UserMapper;
import com.wwt.pixel.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AdminUserService {

    private final UserMapper userMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminPermissionMapper adminPermissionMapper;

    public AdminUserService(UserMapper userMapper,
                            AdminRoleMapper adminRoleMapper,
                            AdminPermissionMapper adminPermissionMapper) {
        this.userMapper = userMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.adminPermissionMapper = adminPermissionMapper;
    }

    /**
     * 管理员列表 (只查拥有 super_admin 或 admin 角色的用户)
     */
    public List<Map<String, Object>> listAdmins() {
        List<Map<String, Object>> users = userMapper.findAdminUsers();
        for (Map<String, Object> user : users) {
            Long userId = (Long) user.get("id");
            List<AdminRole> roles = adminRoleMapper.findByUserId(userId);
            user.put("roles", roles);
        }
        return users;
    }

    /**
     * 启用/禁用用户
     */
    public void updateStatus(Long id, Integer status) {
        Map<String, Object> existing = userMapper.findById(id);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        userMapper.updateStatus(id, status);
    }

    /**
     * 分配角色
     */
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        Map<String, Object> existing = userMapper.findById(userId);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        adminRoleMapper.deleteUserRoles(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            adminRoleMapper.insertUserRoles(userId, roleIds);
        }
    }

    /**
     * 角色列表
     */
    public List<AdminRole> listRoles() {
        return adminRoleMapper.findAll();
    }

    /**
     * 权限列表
     */
    public List<AdminPermission> listPermissions() {
        return adminPermissionMapper.findAll();
    }
}
