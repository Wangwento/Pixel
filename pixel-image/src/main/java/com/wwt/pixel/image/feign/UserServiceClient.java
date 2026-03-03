package com.wwt.pixel.image.feign;

import com.wwt.pixel.common.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 用户服务Feign客户端
 */
@FeignClient(name = "pixel-user", url = "${feign.client.pixel-user.url:http://localhost:8082}", path = "/api/user")
public interface UserServiceClient {

    /**
     * 检查用户额度是否可用
     */
    @GetMapping("/internal/check-quota")
    Result<Boolean> checkQuota(@RequestHeader("X-User-Id") Long userId);

    /**
     * 消耗用户额度 (生成图片前调用)
     */
    @PostMapping("/internal/consume-quota")
    Result<String> consumeQuota(@RequestHeader("X-User-Id") Long userId);
}