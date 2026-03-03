package com.wwt.pixel.domain.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户行为日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehavior {
    private Long id;
    private Long userId;
    private String behaviorType;  // generate/download/like/share/view
    private String targetType;    // image/style/template
    private String targetId;
    private Map<String, Object> extraData;  // JSON扩展数据
    private LocalDateTime createdAt;

    /**
     * 行为类型
     */
    public static class BehaviorType {
        public static final String GENERATE = "generate";
        public static final String DOWNLOAD = "download";
        public static final String LIKE = "like";
        public static final String SHARE = "share";
        public static final String VIEW = "view";
    }

    /**
     * 目标类型
     */
    public static class TargetType {
        public static final String IMAGE = "image";
        public static final String STYLE = "style";
        public static final String TEMPLATE = "template";
    }
}