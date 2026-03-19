package com.wwt.pixel.image.feign;

import com.wwt.pixel.common.dto.Result;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

/**
 * 用户服务Feign客户端
 */
@FeignClient(name = "pixel-user", path = "/api/user")
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

    /**
     * 获取用户VIP等级
     */
    @GetMapping("/internal/vip-level")
    Result<Integer> getVipLevel(@RequestHeader("X-User-Id") Long userId);

    /**
     * 批量获取用户基础信息
     */
    @PostMapping("/internal/basic-info/batch")
    Result<List<UserBasicInfoDTO>> getUserBasicInfoBatch(@RequestBody Map<String, List<Long>> body);

    /**
     * 内部加积分 (热门图片奖励等)
     */
    @PostMapping("/internal/add-points")
    Result<Void> addPoints(@RequestHeader("X-User-Id") Long userId,
                           @RequestBody Map<String, Object> body);

    @Data
    class UserBasicInfoDTO {
        private Long id;
        private String username;
        private String nickname;
        private String avatar;
    }
}
