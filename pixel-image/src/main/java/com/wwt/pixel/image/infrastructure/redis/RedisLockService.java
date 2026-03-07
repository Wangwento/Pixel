package com.wwt.pixel.image.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * Redis分布式锁服务（使用Lua脚本保证原子性）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Lua脚本：原子性释放锁
     * 只有当锁的value匹配时才删除，保证不会误删其他线程的锁
     */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 尝试获取锁
     *
     * @param lockKey 锁的key
     * @param expireTime 锁的过期时间
     * @return 锁的唯一标识，获取失败返回null
     */
    public String tryLock(String lockKey, Duration expireTime) {
        String lockValue = UUID.randomUUID().toString();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, expireTime);

        if (Boolean.TRUE.equals(success)) {
            log.debug("获取锁成功: key={}, value={}", lockKey, lockValue);
            return lockValue;
        }

        log.debug("获取锁失败: key={}", lockKey);
        return null;
    }

    /**
     * 释放锁（使用Lua脚本保证原子性）
     *
     * @param lockKey 锁的key
     * @param lockValue 锁的唯一标识
     * @return 是否释放成功
     */
    public boolean unlock(String lockKey, String lockValue) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(UNLOCK_SCRIPT);
            script.setResultType(Long.class);

            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(lockKey),
                    lockValue
            );

            boolean success = Long.valueOf(1).equals(result);
            if (success) {
                log.debug("释放锁成功: key={}", lockKey);
            } else {
                log.warn("释放锁失败，锁已被其他线程持有或已过期: key={}", lockKey);
            }
            return success;

        } catch (Exception e) {
            log.error("释放锁异常: key={}", lockKey, e);
            return false;
        }
    }
}
