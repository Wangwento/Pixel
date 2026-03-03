package com.wwt.pixel.infrastructure.persistence.mapper;

import com.wwt.pixel.domain.model.QuotaPackage;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 额度包Mapper
 */
@Mapper
public interface QuotaPackageMapper {

    @Insert("""
        INSERT INTO quota_package (user_id, quota_total, quota_used, quota_remaining,
            source, source_desc, expire_time, status)
        VALUES (#{userId}, #{quotaTotal}, #{quotaUsed}, #{quotaRemaining},
            #{source}, #{sourceDesc}, #{expireTime}, #{status})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(QuotaPackage quotaPackage);

    @Update("""
        UPDATE quota_package SET
            quota_used = #{quotaUsed}, quota_remaining = #{quotaRemaining}, status = #{status}
        WHERE id = #{id}
        """)
    int update(QuotaPackage quotaPackage);

    /**
     * 获取用户有效的额度包(按过期时间升序，优先消耗快过期的)
     */
    @Select("""
        SELECT * FROM quota_package
        WHERE user_id = #{userId} AND status = 1 AND expire_time > NOW()
        ORDER BY expire_time ASC
        """)
    List<QuotaPackage> findValidByUserId(Long userId);

    /**
     * 获取用户所有额度包
     */
    @Select("""
        SELECT * FROM quota_package
        WHERE user_id = #{userId}
        ORDER BY created_at DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<QuotaPackage> findByUserId(@Param("userId") Long userId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    /**
     * 统计用户有效额度总数
     */
    @Select("""
        SELECT COALESCE(SUM(quota_remaining), 0) FROM quota_package
        WHERE user_id = #{userId} AND status = 1 AND expire_time > NOW()
        """)
    int sumValidQuota(Long userId);

    /**
     * 检查用户是否已领取某来源的额度
     */
    @Select("SELECT COUNT(*) FROM quota_package WHERE user_id = #{userId} AND source = #{source}")
    int countByUserIdAndSource(@Param("userId") Long userId, @Param("source") Integer source);

    /**
     * 过期额度包(定时任务调用)
     */
    @Update("UPDATE quota_package SET status = 0 WHERE status = 1 AND expire_time <= NOW()")
    int expireQuotaPackages();
}
