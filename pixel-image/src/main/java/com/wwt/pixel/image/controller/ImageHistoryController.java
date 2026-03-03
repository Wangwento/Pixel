package com.wwt.pixel.image.controller;

import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.image.domain.GenerationRecord;
import com.wwt.pixel.image.mapper.GenerationRecordMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 图片历史记录控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/image")
@RequiredArgsConstructor
public class ImageHistoryController {

    private final GenerationRecordMapper generationRecordMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取用户生成历史（分页）
     */
    @GetMapping("/history")
    public Result<Map<String, Object>> getHistory(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int pageSize,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        log.info("获取历史记录: userId={}, page={}, pageSize={}, startDate={}, endDate={}",
                userId, page, pageSize, startDate, endDate);

        int offset = (page - 1) * pageSize;
        List<GenerationRecord> records;
        int total;

        if (startDate != null && endDate != null && !startDate.isEmpty() && !endDate.isEmpty()) {
            // 带日期筛选
            records = generationRecordMapper.findByUserIdAndDateRange(
                    userId, startDate, endDate, offset, pageSize);
            total = generationRecordMapper.countByUserIdAndDateRange(userId, startDate, endDate);
        } else {
            // 不带日期筛选
            records = generationRecordMapper.findByUserIdWithPaging(userId, offset, pageSize);
            total = generationRecordMapper.countByUserId(userId);
        }

        // 转换为前端需要的格式
        List<HistoryItem> list = records.stream()
                .map(this::convertToHistoryItem)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);

        return Result.success(data);
    }

    private HistoryItem convertToHistoryItem(GenerationRecord record) {
        return HistoryItem.builder()
                .id(String.valueOf(record.getId()))
                .imageUrl(record.getResultImageUrl())
                .prompt(record.getPrompt())
                .style(record.getStyle())
                .createdAt(record.getCreatedAt() != null
                        ? record.getCreatedAt().format(DATE_FORMATTER)
                        : null)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryItem {
        private String id;
        private String imageUrl;
        private String prompt;
        private String style;
        private String createdAt;
    }
}