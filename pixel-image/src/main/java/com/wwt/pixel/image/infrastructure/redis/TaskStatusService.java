package com.wwt.pixel.image.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.image.domain.GenerationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 任务状态管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStatusService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String TASK_STATUS_PREFIX = "task:status:";
    private static final Duration TASK_EXPIRE_TIME = Duration.ofMinutes(10);

    /**
     * 保存任务状态
     */
    public void saveTaskStatus(String taskId, String status, GenerationResult result) {
        try {
            TaskStatus taskStatus = new TaskStatus();
            taskStatus.setTaskId(taskId);
            taskStatus.setStatus(status);
            taskStatus.setResult(result);
            taskStatus.setTimestamp(System.currentTimeMillis());

            String json = objectMapper.writeValueAsString(taskStatus);
            redisTemplate.opsForValue().set(
                    TASK_STATUS_PREFIX + taskId,
                    json,
                    TASK_EXPIRE_TIME
            );
            log.debug("保存任务状态: taskId={}, status={}", taskId, status);
        } catch (JsonProcessingException e) {
            log.error("保存任务状态失败", e);
        }
    }

    /**
     * 获取任务状态
     */
    public TaskStatus getTaskStatus(String taskId) {
        try {
            String json = redisTemplate.opsForValue().get(TASK_STATUS_PREFIX + taskId);
            if (json != null) {
                return objectMapper.readValue(json, TaskStatus.class);
            }
        } catch (JsonProcessingException e) {
            log.error("获取任务状态失败", e);
        }
        return null;
    }

    /**
     * 删除任务状态
     */
    public void deleteTaskStatus(String taskId) {
        redisTemplate.delete(TASK_STATUS_PREFIX + taskId);
        log.debug("删除任务状态: taskId={}", taskId);
    }

    /**
     * 任务状态数据结构
     */
    public static class TaskStatus {
        private String taskId;
        private String status; // RUNNING, SUCCESS, FAILED
        private GenerationResult result;
        private Long timestamp;

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public GenerationResult getResult() {
            return result;
        }

        public void setResult(GenerationResult result) {
            this.result = result;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
