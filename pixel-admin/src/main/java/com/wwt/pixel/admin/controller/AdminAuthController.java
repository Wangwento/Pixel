package com.wwt.pixel.admin.controller;

import com.wwt.pixel.admin.service.AdminAuthService;
import com.wwt.pixel.common.constant.CommonConstant;
import com.wwt.pixel.common.dto.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        Map<String, Object> data = adminAuthService.login(username, password);
        return Result.success(data);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId) {
        adminAuthService.logout(adminId);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId) {
        Map<String, Object> data = adminAuthService.getCurrentAdmin(adminId);
        return Result.success(data);
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestHeader(CommonConstant.HEADER_ADMIN_ID) Long adminId,
                                       @RequestBody Map<String, String> body) {
        adminAuthService.changePassword(adminId, body.get("oldPassword"), body.get("newPassword"));
        return Result.success();
    }
}
