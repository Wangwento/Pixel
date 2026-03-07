CREATE DATABASE IF NOT EXISTS `pixel_trade`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `pixel_trade`;

CREATE TABLE IF NOT EXISTS `membership_plan` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `plan_code` VARCHAR(32) NOT NULL COMMENT '套餐编码',
    `plan_name` VARCHAR(50) NOT NULL COMMENT '套餐名称',
    `vip_level` TINYINT NOT NULL COMMENT 'VIP等级',
    `duration_days` INT NOT NULL DEFAULT 0 COMMENT '有效期天数',
    `daily_quota` INT NOT NULL DEFAULT 0 COMMENT '每日额度',
    `monthly_quota` INT NOT NULL DEFAULT 0 COMMENT '月度额度',
    `gift_points` INT NOT NULL DEFAULT 0 COMMENT '赠送积分',
    `points_multiplier` DECIMAL(3,1) NOT NULL DEFAULT 1.0 COMMENT '积分倍率',
    `price` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '价格',
    `features` JSON COMMENT '特权列表',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-停用, 1-启用',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_plan_code` (`plan_code`),
    KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员套餐表';

INSERT INTO `membership_plan`
(`plan_code`, `plan_name`, `vip_level`, `duration_days`, `daily_quota`, `monthly_quota`, `gift_points`, `points_multiplier`, `price`, `features`, `status`, `sort_order`)
VALUES
('vip-monthly', '月卡会员', 1, 30, 0, 100, 0, 2.0, 25.00, '["每月100张专属额度","全部风格模板","4K高清无水印","极速生成队列","签到积分翻倍"]', 1, 10),
('vip-yearly', '年卡会员', 2, 365, 0, 120, 500, 2.0, 248.00, '["每月120张专属额度","购买送500积分","全部风格模板","4K高清无水印","极速生成队列","签到积分翻倍","专属客服"]', 1, 20)
ON DUPLICATE KEY UPDATE
    `plan_name` = VALUES(`plan_name`),
    `price` = VALUES(`price`),
    `monthly_quota` = VALUES(`monthly_quota`),
    `gift_points` = VALUES(`gift_points`),
    `features` = VALUES(`features`),
    `status` = VALUES(`status`),
    `sort_order` = VALUES(`sort_order`);

CREATE TABLE IF NOT EXISTS `membership_subscription` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `subscription_no` VARCHAR(64) NOT NULL COMMENT '订阅号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `plan_code` VARCHAR(32) NOT NULL COMMENT '套餐编码',
    `vip_level` TINYINT NOT NULL COMMENT 'VIP等级',
    `source_type` VARCHAR(20) NOT NULL COMMENT '来源类型: order/manual/activity',
    `source_order_no` VARCHAR(64) COMMENT '来源订单号',
    `renew_mode` VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT '续费方式: manual/auto',
    `agreement_no` VARCHAR(64) COMMENT '自动续费协议号',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME NOT NULL COMMENT '结束时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-未生效, 1-生效中, 2-已过期, 3-已取消',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_subscription_no` (`subscription_no`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_agreement_no` (`agreement_no`),
    KEY `idx_end_time` (`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员履约表';

CREATE TABLE IF NOT EXISTS `membership_change_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `change_type` VARCHAR(20) NOT NULL COMMENT '变更类型: open/renew/upgrade/refund/manual',
    `before_level` TINYINT DEFAULT 0 COMMENT '变更前等级',
    `after_level` TINYINT DEFAULT 0 COMMENT '变更后等级',
    `before_expire_time` DATETIME COMMENT '变更前过期时间',
    `after_expire_time` DATETIME COMMENT '变更后过期时间',
    `source_type` VARCHAR(20) COMMENT '来源类型',
    `source_id` VARCHAR(64) COMMENT '来源ID',
    `remark` VARCHAR(255) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_source` (`source_type`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员变更日志表';

CREATE TABLE IF NOT EXISTS `subscription_agreement` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `agreement_no` VARCHAR(64) NOT NULL COMMENT '协议号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `plan_code` VARCHAR(32) NOT NULL COMMENT '套餐编码',
    `channel` VARCHAR(20) NOT NULL COMMENT '签约渠道: wx/alipay/apple',
    `channel_agreement_no` VARCHAR(64) COMMENT '渠道协议号',
    `contract_status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待确认, 1-生效中, 2-暂停, 3-已解约, 4-已过期',
    `sign_time` DATETIME COMMENT '签约时间',
    `next_renew_time` DATETIME COMMENT '下次续费时间',
    `last_renew_time` DATETIME COMMENT '上次续费时间',
    `cancel_time` DATETIME COMMENT '解约时间',
    `cancel_reason` VARCHAR(255) COMMENT '解约原因',
    `ext_config` JSON COMMENT '扩展配置',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_agreement_no` (`agreement_no`),
    UNIQUE KEY `uk_channel_agreement_no` (`channel_agreement_no`),
    KEY `idx_user_status` (`user_id`, `contract_status`),
    KEY `idx_next_renew_time` (`next_renew_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动续费协议表';

CREATE TABLE IF NOT EXISTS `subscription_renew_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `renew_no` VARCHAR(64) NOT NULL COMMENT '续费流水号',
    `agreement_no` VARCHAR(64) NOT NULL COMMENT '协议号',
    `subscription_no` VARCHAR(64) COMMENT '会员履约号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `cycle_no` INT NOT NULL COMMENT '第几次扣费',
    `renew_period_start` DATETIME NOT NULL COMMENT '续费周期开始时间',
    `renew_period_end` DATETIME NOT NULL COMMENT '续费周期结束时间',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '续费金额',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待处理, 1-成功, 2-失败, 3-跳过, 4-关闭',
    `fail_reason` VARCHAR(255) COMMENT '失败原因',
    `trade_order_no` VARCHAR(64) COMMENT '交易订单号',
    `payment_no` VARCHAR(64) COMMENT '支付单号',
    `triggered_at` DATETIME COMMENT '发起时间',
    `completed_at` DATETIME COMMENT '完成时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_renew_no` (`renew_no`),
    UNIQUE KEY `uk_agreement_cycle` (`agreement_no`, `cycle_no`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_trade_order_no` (`trade_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动续费流水表';

CREATE TABLE IF NOT EXISTS `promotion_activity` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `activity_code` VARCHAR(32) NOT NULL COMMENT '活动编码',
    `activity_name` VARCHAR(100) NOT NULL COMMENT '活动名称',
    `activity_type` VARCHAR(30) NOT NULL COMMENT '活动类型: first_pay/auto_renew/limited_time/manual',
    `apply_scope` VARCHAR(20) NOT NULL DEFAULT 'membership' COMMENT '适用范围: membership/recharge/all',
    `description` VARCHAR(255) COMMENT '活动描述',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME NOT NULL COMMENT '结束时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-未启用, 1-进行中, 2-已结束',
    `priority` INT NOT NULL DEFAULT 0 COMMENT '优先级',
    `mutually_exclusive` TINYINT NOT NULL DEFAULT 1 COMMENT '是否互斥: 0-否, 1-是',
    `stackable` TINYINT NOT NULL DEFAULT 0 COMMENT '是否可叠加: 0-否, 1-是',
    `channels` JSON COMMENT '适用渠道',
    `ext_config` JSON COMMENT '扩展配置',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_activity_code` (`activity_code`),
    KEY `idx_status_time` (`status`, `start_time`, `end_time`),
    KEY `idx_type_scope` (`activity_type`, `apply_scope`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销活动表';

CREATE TABLE IF NOT EXISTS `promotion_rule` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `activity_id` BIGINT NOT NULL COMMENT '活动ID',
    `rule_code` VARCHAR(32) NOT NULL COMMENT '规则编码',
    `rule_type` VARCHAR(30) NOT NULL COMMENT '规则类型: first_paid_user/renew_cycle/product_code/order_amount/pay_channel',
    `operator` VARCHAR(20) NOT NULL COMMENT '比较操作符: eq/ge/le/in/between',
    `rule_value` VARCHAR(500) NOT NULL COMMENT '规则值',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-停用, 1-启用',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_rule_code` (`rule_code`),
    KEY `idx_activity_status` (`activity_id`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销活动规则表';

CREATE TABLE IF NOT EXISTS `promotion_reward` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `activity_id` BIGINT NOT NULL COMMENT '活动ID',
    `reward_type` VARCHAR(30) NOT NULL COMMENT '奖励类型: discount_amount/discount_ratio/gift_points/gift_quota/gift_days',
    `reward_value` DECIMAL(12,2) NOT NULL COMMENT '奖励值',
    `reward_unit` VARCHAR(20) NOT NULL COMMENT '奖励单位: yuan/percent/points/quota/days',
    `max_discount_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '比例优惠封顶金额',
    `extra_config` JSON COMMENT '扩展配置',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-停用, 1-启用',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY `idx_activity_status` (`activity_id`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销活动奖励表';

CREATE TABLE IF NOT EXISTS `user_trade_profile` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `first_paid_at` DATETIME COMMENT '首次真实支付时间',
    `first_paid_order_no` VARCHAR(64) COMMENT '首次真实支付订单号',
    `first_membership_paid_at` DATETIME COMMENT '首次会员支付时间',
    `first_membership_order_no` VARCHAR(64) COMMENT '首次会员支付订单号',
    `last_paid_at` DATETIME COMMENT '最近一次真实支付时间',
    `last_paid_order_no` VARCHAR(64) COMMENT '最近一次真实支付订单号',
    `total_paid_count` INT NOT NULL DEFAULT 0 COMMENT '累计真实支付次数',
    `total_paid_amount` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '累计真实支付金额',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_first_paid_at` (`first_paid_at`),
    KEY `idx_last_paid_at` (`last_paid_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户交易快照表';

CREATE TABLE IF NOT EXISTS `user_activity_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `activity_id` BIGINT NOT NULL COMMENT '活动ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `biz_key` VARCHAR(100) NOT NULL COMMENT '幂等业务键，如订单号/续费流水号',
    `order_no` VARCHAR(64) COMMENT '订单号',
    `payment_no` VARCHAR(64) COMMENT '支付单号',
    `subscription_no` VARCHAR(64) COMMENT '会员履约号',
    `hit_status` TINYINT NOT NULL DEFAULT 1 COMMENT '命中状态: 1-命中, 2-未命中, 3-失效',
    `reward_status` TINYINT NOT NULL DEFAULT 0 COMMENT '奖励状态: 0-待发放, 1-已发放, 2-发放失败, 3-已回滚',
    `reward_snapshot` JSON COMMENT '奖励快照',
    `biz_time` DATETIME COMMENT '业务发生时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_activity_user_biz` (`activity_id`, `user_id`, `biz_key`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户活动参与记录表';

CREATE TABLE IF NOT EXISTS `trade_order` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `biz_type` VARCHAR(20) NOT NULL COMMENT '业务类型: membership/points/quota',
    `product_code` VARCHAR(32) NOT NULL COMMENT '商品编码',
    `product_name` VARCHAR(100) NOT NULL COMMENT '商品名称',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '原价金额',
    `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠金额',
    `pay_amount` DECIMAL(10,2) NOT NULL COMMENT '应付金额',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待支付, 1-已支付, 2-已关闭, 3-已退款',
    `expire_time` DATETIME COMMENT '支付过期时间',
    `paid_at` DATETIME COMMENT '支付时间',
    `closed_at` DATETIME COMMENT '关闭时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_biz_type` (`biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易订单表';

CREATE TABLE IF NOT EXISTS `order_discount_detail` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `activity_id` BIGINT COMMENT '活动ID',
    `activity_code` VARCHAR(32) COMMENT '活动编码',
    `discount_type` VARCHAR(30) NOT NULL COMMENT '优惠类型: direct_reduce/discount/gift_points/gift_quota/gift_days',
    `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠金额',
    `benefit_snapshot` JSON COMMENT '权益快照',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_activity_id` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单优惠明细表';

CREATE TABLE IF NOT EXISTS `payment_order` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `payment_no` VARCHAR(64) NOT NULL COMMENT '支付单号',
    `trade_order_no` VARCHAR(64) NOT NULL COMMENT '业务订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `channel` VARCHAR(20) NOT NULL COMMENT '支付渠道: wx/alipay/apple',
    `channel_trade_no` VARCHAR(64) COMMENT '渠道交易号',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待支付, 1-支付成功, 2-支付失败, 3-已关闭',
    `request_no` VARCHAR(64) COMMENT '请求流水号',
    `paid_at` DATETIME COMMENT '支付完成时间',
    `raw_response` TEXT COMMENT '原始响应',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    UNIQUE KEY `uk_channel_trade_no` (`channel_trade_no`),
    KEY `idx_trade_order_no` (`trade_order_no`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单表';

CREATE TABLE IF NOT EXISTS `payment_notify_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `channel` VARCHAR(20) NOT NULL COMMENT '支付渠道',
    `notify_id` VARCHAR(100) COMMENT '回调唯一ID',
    `trade_order_no` VARCHAR(64) COMMENT '业务订单号',
    `payment_no` VARCHAR(64) COMMENT '支付单号',
    `channel_trade_no` VARCHAR(64) COMMENT '渠道交易号',
    `verify_status` TINYINT NOT NULL DEFAULT 0 COMMENT '验签状态: 0-未验, 1-成功, 2-失败',
    `process_status` TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态: 0-未处理, 1-成功, 2-失败',
    `notify_body` LONGTEXT NOT NULL COMMENT '回调报文',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `processed_at` DATETIME COMMENT '处理时间',
    KEY `idx_trade_order_no` (`trade_order_no`),
    KEY `idx_payment_no` (`payment_no`),
    KEY `idx_channel_notify` (`channel`, `notify_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调日志表';

CREATE TABLE IF NOT EXISTS `refund_order` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `refund_no` VARCHAR(64) NOT NULL COMMENT '退款单号',
    `trade_order_no` VARCHAR(64) NOT NULL COMMENT '业务订单号',
    `payment_no` VARCHAR(64) COMMENT '支付单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `refund_amount` DECIMAL(10,2) NOT NULL COMMENT '退款金额',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待退款, 1-退款成功, 2-退款失败',
    `channel_refund_no` VARCHAR(64) COMMENT '渠道退款号',
    `reason` VARCHAR(255) COMMENT '退款原因',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    KEY `idx_trade_order_no` (`trade_order_no`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单表';

CREATE TABLE IF NOT EXISTS `points_product` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `product_code` VARCHAR(32) NOT NULL COMMENT '商品编码',
    `name` VARCHAR(50) NOT NULL COMMENT '商品名称',
    `description` VARCHAR(200) COMMENT '描述',
    `product_type` TINYINT NOT NULL COMMENT '类型: 1-生成额度, 2-高清下载, 3-去水印, 4-专属模型',
    `points_cost` INT NOT NULL COMMENT '所需积分',
    `value` INT NOT NULL COMMENT '商品数值',
    `icon` VARCHAR(100) COMMENT '图标',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-下架, 1-上架',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_product_code` (`product_code`),
    KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分商品表';

INSERT INTO `points_product` (`product_code`, `name`, `description`, `product_type`, `points_cost`, `value`, `sort_order`, `status`)
VALUES
('quota-1', '1次生成额度', '可生成1张AI图片', 1, 100, 1, 10, 1),
('quota-5', '5次生成额度', '可生成5张AI图片，节省50积分', 1, 450, 5, 20, 1),
('quota-10', '10次生成额度', '可生成10张AI图片，节省150积分', 1, 850, 10, 30, 1),
('download-4k', '单张高清下载', '将1张图片升级为4K高清', 2, 50, 1, 40, 1),
('remove-watermark', '单张去水印', '去除1张图片的水印', 3, 30, 1, 50, 1)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `description` = VALUES(`description`),
    `points_cost` = VALUES(`points_cost`),
    `value` = VALUES(`value`),
    `sort_order` = VALUES(`sort_order`),
    `status` = VALUES(`status`);
