-- ============================================
-- RBAC 权限表结构 (在 pixel_user 库中)
-- 复用 user 表，不再单独建 admin_user
-- ============================================

USE pixel_user;

-- 角色表
CREATE TABLE IF NOT EXISTS `role` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    description VARCHAR(200) DEFAULT '' COMMENT '描述',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 权限表
CREATE TABLE IF NOT EXISTS `permission` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码',
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    module VARCHAR(50) NOT NULL COMMENT '所属模块',
    description VARCHAR(200) DEFAULT '' COMMENT '描述',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS `role_permission` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 用户-角色关联表 (关联 user.id)
CREATE TABLE IF NOT EXISTS `user_role` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '关联 user 表的 id',
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS `admin_operation_log` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL COMMENT '操作人ID (user.id)',
    admin_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '操作人账号',
    module VARCHAR(50) NOT NULL DEFAULT '' COMMENT '模块',
    action VARCHAR(50) NOT NULL DEFAULT '' COMMENT '操作类型',
    target_type VARCHAR(50) DEFAULT '' COMMENT '目标类型',
    target_id VARCHAR(50) DEFAULT '' COMMENT '目标ID',
    detail TEXT COMMENT '详情',
    ip VARCHAR(50) DEFAULT '' COMMENT 'IP地址',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_admin_id (admin_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作日志表';

-- ============================================
-- 初始角色数据
-- ============================================

INSERT INTO `role` (role_code, role_name, description) VALUES
('super_admin',  '超级管理员', '拥有所有权限，不受权限校验限制'),
('admin',        '管理员',     '普通管理员，按分配的权限操作'),
('user',         '普通用户',   '普通注册用户'),
('vip_monthly',  '月度会员',   '月卡会员'),
('vip_yearly',   '年度会员',   '年卡会员')
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    description = VALUES(description);

-- ============================================
-- 权限数据 (15个模块)
-- ============================================

INSERT INTO `permission` (permission_code, permission_name, module, sort_order) VALUES
-- 管理员管理
('admin:user:list',   '管理员列表', '管理员管理', 100),
('admin:user:create', '创建管理员', '管理员管理', 101),
('admin:user:edit',   '编辑管理员', '管理员管理', 102),
-- 用户管理
('user:list',   '用户列表', '用户管理', 200),
('user:detail', '用户详情', '用户管理', 201),
('user:edit',   '编辑用户', '用户管理', 202),
('user:adjust', '调整用户', '用户管理', 203),
-- 积分额度
('points:list',       '积分列表', '积分额度', 300),
('points:compensate', '积分补偿', '积分额度', 301),
-- 成长任务
('growth:list',    '任务列表', '成长任务', 400),
('growth:edit',    '编辑任务', '成长任务', 401),
('growth:regrant', '重新发放', '成长任务', 402),
-- 邀请广告
('invite:list',       '邀请列表', '邀请广告', 500),
('invite:anti-cheat', '反作弊',   '邀请广告', 501),
-- 图片生成
('generation:list',     '生成记录', '图片生成', 600),
('generation:analysis', '生成分析', '图片生成', 601),
-- 资产管理
('asset:list', '资产列表', '资产管理', 700),
-- 风格模板
('style:list',   '模板列表', '风格模板', 800),
('style:edit',   '编辑模板', '风格模板', 801),
('style:delete', '删除模板', '风格模板', 802),
-- 会员中心
('membership:list', '会员列表', '会员中心', 900),
('membership:edit', '编辑会员', '会员中心', 901),
-- 营销活动
('marketing:list', '活动列表', '营销活动', 1000),
('marketing:edit', '编辑活动', '营销活动', 1001),
-- 订单中心
('order:list', '订单列表', '订单中心', 1100),
-- 支付中心
('payment:list',   '支付列表', '支付中心', 1200),
('payment:notify', '支付通知', '支付中心', 1201),
-- 退款中心
('refund:list',    '退款列表', '退款中心', 1300),
('refund:process', '处理退款', '退款中心', 1301),
-- 消息运维
('system:message', '系统消息', '消息运维', 1400),
('system:undo',    '撤回消息', '消息运维', 1401),
-- 报表中心
('report:finance',        '财务报表', '报表中心', 1500),
('report:reconciliation', '对账报表', '报表中心', 1501)
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    module = VALUES(module),
    sort_order = VALUES(sort_order);

-- 超级管理员拥有所有权限
INSERT IGNORE INTO `role_permission` (role_id, permission_id)
SELECT r.id, p.id
FROM `role` r
CROSS JOIN `permission` p
WHERE r.role_code = 'super_admin';

-- ============================================
-- 默认管理员账号 (插入 user 表)
-- 账号: admin  密码: admin123
-- BCrypt hash of 'admin123'
-- ============================================

INSERT INTO `user` (username, password, nickname, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '超级管理员', 1)
ON DUPLICATE KEY UPDATE
    nickname = VALUES(nickname),
    status = VALUES(status);

-- 给 admin 账号关联超级管理员角色 (role_id=1)
INSERT IGNORE INTO `user_role` (user_id, role_id)
SELECT u.id, r.id
FROM `user` u
INNER JOIN `role` r ON r.role_code = 'super_admin'
WHERE u.username = 'admin';
