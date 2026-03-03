package com.wwt.pixel.infrastructure.persistence.mapper;

import com.wwt.pixel.domain.model.InviteRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邀请记录Mapper
 */
@Mapper
public interface InviteRecordMapper {

    @Select("SELECT * FROM invite_record WHERE id = #{id}")
    InviteRecord findById(Long id);

    @Select("SELECT * FROM invite_record WHERE invitee_id = #{inviteeId}")
    InviteRecord findByInviteeId(Long inviteeId);

    @Select("SELECT * FROM invite_record WHERE inviter_id = #{inviterId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<InviteRecord> findByInviterId(@Param("inviterId") Long inviterId,
                                        @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM invite_record WHERE inviter_id = #{inviterId}")
    int countByInviterId(Long inviterId);

    @Select("SELECT COUNT(*) FROM invite_record WHERE inviter_id = #{inviterId} AND status = #{status}")
    int countByInviterIdAndStatus(@Param("inviterId") Long inviterId, @Param("status") Integer status);

    @Insert("""
        INSERT INTO invite_record (inviter_id, invitee_id, invite_code, status,
            inviter_reward, invitee_reward, created_at)
        VALUES (#{inviterId}, #{inviteeId}, #{inviteCode}, #{status},
            #{inviterReward}, #{inviteeReward}, #{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InviteRecord record);

    @Update("""
        UPDATE invite_record SET status = #{status}, inviter_reward = #{inviterReward},
            invitee_reward = #{inviteeReward}, completed_at = #{completedAt}
        WHERE id = #{id}
        """)
    int update(InviteRecord record);

    @Update("""
        UPDATE invite_record SET status = 1, inviter_reward = #{reward},
            invitee_reward = #{reward}, completed_at = #{completedAt}
        WHERE invitee_id = #{inviteeId} AND status = 0
        """)
    int completeInvite(@Param("inviteeId") Long inviteeId,
                       @Param("reward") Integer reward,
                       @Param("completedAt") LocalDateTime completedAt);
}