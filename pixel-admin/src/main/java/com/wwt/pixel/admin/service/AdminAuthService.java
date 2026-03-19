package com.wwt.pixel.admin.service;

import com.wwt.pixel.admin.auth.mapper.AdminPermissionMapper;
import com.wwt.pixel.admin.auth.mapper.AdminRoleMapper;
import com.wwt.pixel.admin.auth.mapper.UserMapper;
import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class AdminAuthService {

    private final UserMapper userMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminPermissionMapper adminPermissionMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Value("${pixel.admin.jwt.secret}")
    private String jwtSecret;

    private static final long TOKEN_EXPIRE_HOURS = 12;

    public AdminAuthService(UserMapper userMapper,
                            AdminRoleMapper adminRoleMapper,
                            AdminPermissionMapper adminPermissionMapper,
                            BCryptPasswordEncoder passwordEncoder,
                            StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.adminPermissionMapper = adminPermissionMapper;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 管理员登录 (复用 user 表，校验是否拥有管理员角色)
     */
    public Map<String, Object> login(String username, String password) {
        Map<String, Object> user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException("账号或密码错误");
        }

        Integer status = (Integer) user.get("status");
        if (status == null || status != 1) {
            throw new BusinessException("账号已被禁用");
        }

        String encodedPassword = (String) user.get("password");
        if (!passwordEncoder.matches(password, encodedPassword)) {
            throw new BusinessException("账号或密码错误");
        }

        Long userId = (Long) user.get("id");

        // 校验是否有管理员角色
        List<String> roleCodes = adminRoleMapper.findRoleCodesByUserId(userId);
        if (!roleCodes.contains("super_admin") && !roleCodes.contains("admin")) {
            throw new BusinessException("无管理员权限");
        }

        // 生成 JWT (使用 admin 独立 secret)
        String token = JwtUtil.generateToken(userId, (String) user.get("username"), jwtSecret);

        // 查询权限
        Set<String> permissionCodes = adminPermissionMapper.findPermissionCodesByUserId(userId);

        // 缓存到 Redis
        String tokenKey = "admin:token:" + userId;
        redisTemplate.opsForValue().set(tokenKey, token, TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);

        String permKey = "admin:permissions:" + userId;
        redisTemplate.delete(permKey);
        if (!permissionCodes.isEmpty()) {
            redisTemplate.opsForSet().add(permKey, permissionCodes.toArray(new String[0]));
            redisTemplate.expire(permKey, TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
        }

        String roleKey = "admin:roles:" + userId;
        redisTemplate.delete(roleKey);
        if (!roleCodes.isEmpty()) {
            redisTemplate.opsForSet().add(roleKey, roleCodes.toArray(new String[0]));
            redisTemplate.expire(roleKey, TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
        }

        // 构造返回
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", userId);
        userInfo.put("username", user.get("username"));
        userInfo.put("nickname", user.get("nickname"));
        userInfo.put("avatar", user.get("avatar"));
        userInfo.put("roles", roleCodes);
        userInfo.put("permissions", permissionCodes);
        result.put("userInfo", userInfo);

        return result;
    }

    /**
     * 登出
     */
    public void logout(Long adminId) {
        redisTemplate.delete("admin:token:" + adminId);
        redisTemplate.delete("admin:permissions:" + adminId);
        redisTemplate.delete("admin:roles:" + adminId);
    }

    /**
     * 获取当前管理员信息
     */
    public Map<String, Object> getCurrentAdmin(Long adminId) {
        Map<String, Object> user = userMapper.findById(adminId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        List<String> roleCodes = adminRoleMapper.findRoleCodesByUserId(adminId);
        Set<String> permissionCodes = adminPermissionMapper.findPermissionCodesByUserId(adminId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.get("id"));
        result.put("username", user.get("username"));
        result.put("nickname", user.get("nickname"));
        result.put("avatar", user.get("avatar"));
        result.put("email", user.get("email"));
        result.put("phone", user.get("phone"));
        result.put("roles", roleCodes);
        result.put("permissions", permissionCodes);

        return result;
    }

    /**
     * 修改密码
     */
    public void changePassword(Long adminId, String oldPassword, String newPassword) {
        Map<String, Object> user = userMapper.findWithPasswordById(adminId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        String encodedPassword = (String) user.get("password");
        if (!passwordEncoder.matches(oldPassword, encodedPassword)) {
            throw new BusinessException("原密码错误");
        }
        String encoded = passwordEncoder.encode(newPassword);
        userMapper.updatePassword(adminId, encoded);
    }
}
