package com.wwt.pixel.video.config;

import com.wwt.pixel.common.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "pixel-user", path = "/api/user")
public interface UserServiceClient {

    @GetMapping("/internal/vip-level")
    Result<Integer> getVipLevel(@RequestHeader("X-User-Id") Long userId);
}
