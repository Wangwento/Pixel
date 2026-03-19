package com.wwt.pixel.admin.controller;

import com.wwt.pixel.admin.annotation.RequirePermission;
import com.wwt.pixel.admin.domain.AdminPermission;
import com.wwt.pixel.admin.domain.AdminRole;
import com.wwt.pixel.admin.service.AdminUserService;
import com.wwt.pixel.common.dto.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    @RequirePermission("admin:user:list")
    public Result<List<Map<String, Object>>> listAdmins() {
        return Result.success(adminUserService.listAdmins());
    }

    @PutMapping("/users/{id}/status")
    @RequirePermission("admin:user:edit")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminUserService.updateStatus(id, body.get("status"));
        return Result.success();
    }

    @PutMapping("/users/{id}/roles")
    @RequirePermission("admin:user:edit")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        adminUserService.assignRoles(id, body.get("roleIds"));
        return Result.success();
    }

    @GetMapping("/roles")
    public Result<List<AdminRole>> listRoles() {
        return Result.success(adminUserService.listRoles());
    }

    @GetMapping("/permissions")
    public Result<List<AdminPermission>> listPermissions() {
        return Result.success(adminUserService.listPermissions());
    }
}
