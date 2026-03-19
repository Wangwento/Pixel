CREATE DATABASE IF NOT EXISTS `pixel_admin`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `pixel_admin`;
-- AI服务提供商和模型配置管理（重新设计）

-- 服务提供商表（只包含基础连接信息）
CREATE TABLE ai_provider (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code VARCHAR(50) UNIQUE NOT NULL COMMENT '提供商代码',
    provider_name VARCHAR(100) NOT NULL COMMENT '提供商名称',
    base_url VARCHAR(500) NOT NULL COMMENT 'API基础URL',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    weight INT DEFAULT 100 COMMENT '权重，越大优先级越高',
    description TEXT COMMENT '描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 模型表（包含模型特定的所有配置）
CREATE TABLE ai_model (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id BIGINT NOT NULL COMMENT '提供商ID',
    model_code VARCHAR(100) NOT NULL COMMENT '模型代码',
    model_name VARCHAR(200) NOT NULL COMMENT '模型名称',
    model_type VARCHAR(50) NOT NULL COMMENT '模型类型: text2img/img2img/text2video/img2video',
    api_key VARCHAR(500) COMMENT '模型专属API密钥',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    min_vip_level INT DEFAULT 0 COMMENT '最低VIP等级',
    cost_per_unit DECIMAL(10,4) COMMENT '单位成本',
    timeout_ms INT DEFAULT 60000 COMMENT '超时时间（毫秒）',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    description TEXT COMMENT '描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_provider_model (provider_id, model_code),
    FOREIGN KEY (provider_id) REFERENCES ai_provider(id) ON DELETE CASCADE
);

-- 模型参数定义表（动态参数配置）
CREATE TABLE ai_model_param_def (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_id BIGINT NOT NULL COMMENT '模型ID',
    param_key VARCHAR(100) NOT NULL COMMENT '参数键名',
    param_name VARCHAR(200) NOT NULL COMMENT '参数显示名称',
    param_type VARCHAR(50) NOT NULL COMMENT '参数类型: string/number/boolean/select/multiSelect',
    required BOOLEAN DEFAULT FALSE COMMENT '是否必填',
    default_value TEXT COMMENT '默认值',
    options JSON COMMENT '可选项（select类型使用）',
    validation_rule TEXT COMMENT '验证规则',
    description TEXT COMMENT '参数说明',
    display_order INT DEFAULT 0 COMMENT '显示顺序',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_param (model_id, param_key),
    FOREIGN KEY (model_id) REFERENCES ai_model(id) ON DELETE CASCADE
);