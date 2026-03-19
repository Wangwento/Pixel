USE pixel_admin;

-- 添加visible字段，控制参数是否对用户可见
ALTER TABLE ai_model_param_def
ADD COLUMN visible BOOLEAN DEFAULT TRUE COMMENT '是否对用户可见（false表示后端自动填充）'
AFTER required;

-- 修改param_type支持更多类型
ALTER TABLE ai_model_param_def
MODIFY COLUMN param_type VARCHAR(50) NOT NULL COMMENT '参数类型: string/number/boolean/select/multiSelect/array/object';