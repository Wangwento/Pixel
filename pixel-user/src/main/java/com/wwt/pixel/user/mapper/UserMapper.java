package com.wwt.pixel.user.mapper;

import com.wwt.pixel.user.domain.User;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户Mapper (User服务)
 */
@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE id = #{id}")
    User findById(Long id);

    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(String username);

    @Select("SELECT * FROM user WHERE invite_code = #{inviteCode}")
    User findByInviteCode(String inviteCode);

    @Update("""
        UPDATE user SET
            nickname = #{nickname}, avatar = #{avatar}, email = #{email}, phone = #{phone},
            phone_verified = #{phoneVerified}, real_name = #{realName}, id_card = #{idCard},
            real_name_verified = #{realNameVerified}, points = #{points}, total_points = #{totalPoints},
            free_quota = #{freeQuota}, free_quota_total = #{freeQuotaTotal},
            daily_limit = #{dailyLimit}, daily_used = #{dailyUsed}, daily_limit_date = #{dailyLimitDate},
            monthly_quota = #{monthlyQuota}, monthly_quota_used = #{monthlyQuotaUsed}, monthly_quota_date = #{monthlyQuotaDate},
            user_type = #{userType}, vip_level = #{vipLevel}, vip_expire_time = #{vipExpireTime},
            level = #{level}, exp = #{exp}, status = #{status},
            last_sign_date = #{lastSignDate}, continuous_sign_days = #{continuousSignDays},
            profile_completed = #{profileCompleted}
        WHERE id = #{id}
        """)
    int update(User user);

    @Update("UPDATE user SET points = #{points}, total_points = #{totalPoints} WHERE id = #{id}")
    int updatePoints(@Param("id") Long id, @Param("points") Integer points, @Param("totalPoints") Integer totalPoints);

    @Update("UPDATE user SET free_quota = #{freeQuota}, free_quota_total = #{freeQuotaTotal} WHERE id = #{id}")
    int updateFreeQuota(@Param("id") Long id, @Param("freeQuota") Integer freeQuota, @Param("freeQuotaTotal") Integer freeQuotaTotal);

    @Update("""
        UPDATE user SET daily_used = #{dailyUsed}, daily_limit_date = #{dailyLimitDate}
        WHERE id = #{id}
        """)
    int updateDailyLimit(@Param("id") Long id, @Param("dailyUsed") Integer dailyUsed,
                         @Param("dailyLimitDate") LocalDate dailyLimitDate);

    @Update("""
        UPDATE user SET monthly_quota_used = #{monthlyQuotaUsed}, monthly_quota_date = #{monthlyQuotaDate}
        WHERE id = #{id}
        """)
    int updateMonthlyQuota(@Param("id") Long id, @Param("monthlyQuotaUsed") Integer monthlyQuotaUsed,
                           @Param("monthlyQuotaDate") LocalDate monthlyQuotaDate);

    @Update("""
        UPDATE user SET vip_level = #{vipLevel}, vip_expire_time = #{vipExpireTime},
            user_type = #{userType}, monthly_quota = #{monthlyQuota}
        WHERE id = #{id}
        """)
    int updateVip(@Param("id") Long id, @Param("vipLevel") Integer vipLevel,
                  @Param("vipExpireTime") LocalDateTime vipExpireTime,
                  @Param("userType") Integer userType, @Param("monthlyQuota") Integer monthlyQuota);

    @Update("UPDATE user SET last_sign_date = #{lastSignDate}, continuous_sign_days = #{continuousSignDays} WHERE id = #{id}")
    int updateSignIn(@Param("id") Long id, @Param("lastSignDate") LocalDate lastSignDate,
                     @Param("continuousSignDays") Integer continuousSignDays);

    @Update("UPDATE user SET nickname = #{nickname}, avatar = #{avatar}, profile_completed = #{profileCompleted} WHERE id = #{id}")
    int updateProfile(@Param("id") Long id, @Param("nickname") String nickname,
                      @Param("avatar") String avatar, @Param("profileCompleted") Integer profileCompleted);
}
