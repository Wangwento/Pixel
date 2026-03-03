package com.wwt.pixel.auth.mapper;

import com.wwt.pixel.auth.domain.User;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;

/**
 * 用户Mapper (Auth服务)
 */
@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE id = #{id}")
    User findById(Long id);

    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(String username);

    @Select("SELECT * FROM user WHERE email = #{email}")
    User findByEmail(String email);

    @Select("SELECT * FROM user WHERE invite_code = #{inviteCode}")
    User findByInviteCode(String inviteCode);

    @Insert("""
        INSERT INTO user (username, password, nickname, avatar, email, phone_verified,
            real_name_verified, points, total_points, free_quota, free_quota_total,
            daily_limit, daily_used, daily_limit_date,
            monthly_quota, monthly_quota_used, user_type, vip_level, level, exp, status,
            continuous_sign_days, invite_code, invited_by, profile_completed)
        VALUES (#{username}, #{password}, #{nickname}, #{avatar}, #{email}, #{phoneVerified},
            #{realNameVerified}, #{points}, #{totalPoints}, #{freeQuota}, #{freeQuotaTotal},
            #{dailyLimit}, #{dailyUsed}, #{dailyLimitDate},
            #{monthlyQuota}, #{monthlyQuotaUsed}, #{userType}, #{vipLevel}, #{level}, #{exp}, #{status},
            #{continuousSignDays}, #{inviteCode}, #{invitedBy}, #{profileCompleted})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}