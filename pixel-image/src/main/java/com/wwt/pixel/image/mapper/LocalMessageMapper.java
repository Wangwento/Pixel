package com.wwt.pixel.image.mapper;

import com.wwt.pixel.image.domain.LocalMessage;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LocalMessageMapper {

    @Insert("INSERT INTO local_message (message_id, topic, tag, content, status, retry_count, max_retry, next_retry_time) " +
            "VALUES (#{messageId}, #{topic}, #{tag}, #{content}, 0, 0, #{maxRetry}, #{nextRetryTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LocalMessage message);

    @Update("UPDATE local_message SET status = #{status}, update_time = NOW() WHERE message_id = #{messageId}")
    int updateStatus(@Param("messageId") String messageId, @Param("status") int status);

    @Update("UPDATE local_message SET status = 2, retry_count = retry_count + 1, " +
            "next_retry_time = #{nextRetryTime}, error_message = #{errorMessage}, update_time = NOW() " +
            "WHERE message_id = #{messageId}")
    int updateFailed(@Param("messageId") String messageId,
                     @Param("nextRetryTime") LocalDateTime nextRetryTime,
                     @Param("errorMessage") String errorMessage);

    // 查询待重试的消息：状态为失败 且 重试次数未超限 且 到了重试时间
    @Select("SELECT * FROM local_message WHERE status = 2 AND retry_count < max_retry " +
            "AND next_retry_time <= NOW() ORDER BY next_retry_time LIMIT 100")
    List<LocalMessage> findPendingRetry();
}