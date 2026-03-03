import React, { useState } from 'react';
import { Modal, Input, Button, Form, Checkbox, message, Progress } from 'antd';
import {
  UserOutlined,
  LockOutlined,
  MailOutlined,
  SafetyCertificateOutlined,
  RocketOutlined,
  GiftOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { register } from '../../api/auth';
import type { UserInfo } from '../../api/auth';
import { useAuth } from '../../contexts/AuthContext';
import './index.css';

interface RegisterModalProps {
  open: boolean;
  onClose: () => void;
  onRegisterSuccess?: (user: UserInfo) => void;
  onBackToLogin?: () => void;
}

const RegisterModal: React.FC<RegisterModalProps> = ({
  open,
  onClose,
  onRegisterSuccess,
  onBackToLogin,
}) => {
  const [loading, setLoading] = useState(false);
  const [passwordStrength, setPasswordStrength] = useState(0);
  const [form] = Form.useForm();
  const { setCurrentUser } = useAuth();

  // 计算密码强度
  const calculatePasswordStrength = (password: string) => {
    let strength = 0;
    if (password.length >= 6) strength += 25;
    if (password.length >= 10) strength += 15;
    if (/[a-z]/.test(password) && /[A-Z]/.test(password)) strength += 20;
    if (/\d/.test(password)) strength += 20;
    if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) strength += 20;
    return Math.min(strength, 100);
  };

  const getStrengthColor = () => {
    if (passwordStrength < 40) return '#ff4d4f';
    if (passwordStrength < 70) return '#faad14';
    return '#52c41a';
  };

  const getStrengthText = () => {
    if (passwordStrength < 40) return '弱';
    if (passwordStrength < 70) return '中';
    return '强';
  };

  // 注册提交
  const handleSubmit = async (values: {
    username: string;
    password: string;
    confirmPassword: string;
    email?: string;
    agreement: boolean;
  }) => {
    if (!values.agreement) {
      message.error('请先同意用户协议和隐私政策');
      return;
    }

    if (values.password !== values.confirmPassword) {
      message.error('两次输入的密码不一致');
      return;
    }

    setLoading(true);
    try {
      const res = await register({
        username: values.username,
        password: values.password,
        email: values.email,
      }) as unknown as { code: number; data: { token: string; user: UserInfo }; message?: string };

      if (res.code === 200) {
        localStorage.setItem('token', res.data.token);
        // 更新全局用户状态
        setCurrentUser(res.data.user);
        onRegisterSuccess?.(res.data.user);
        onClose();
      } else {
        message.error(res.message || '注册失败');
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '注册失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      width={920}
      centered
      closable={false}
      rootClassName="register-modal"
      wrapClassName="register-modal-wrap"
      maskClosable={false}
      keyboard={false}
      classNames={{
        wrapper: 'register-modal-wrapper',
        content: 'register-modal-content',
        body: 'register-modal-body',
      }}
      styles={{
        mask: {
          backdropFilter: 'blur(4px)',
        },
        content: {
          background: 'transparent',
          boxShadow: 'none',
          padding: 0,
        },
        body: {
          padding: 0,
          background: 'transparent',
        },
      }}
    >
      <div className="register-modal-container">
        {/* 关闭按钮 */}
        <button className="close-btn" onClick={onClose}>
          <span className="close-icon">×</span>
        </button>

        {/* 装饰性元素 */}
        <div className="decor-circle decor-circle-1"></div>
        <div className="decor-circle decor-circle-2"></div>
        <div className="decor-circle decor-circle-3"></div>
        <div className="decor-line decor-line-1"></div>
        <div className="decor-line decor-line-2"></div>

        {/* 左侧权益展示区域 */}
        <div className="benefits-section">
          <div className="benefits-header">
            <RocketOutlined className="rocket-icon" />
            <h3>加入 Pixel</h3>
          </div>
          <p className="benefits-subtitle">开启你的 AI 创作之旅</p>

          <div className="benefits-list">
            <div className="benefit-item">
              <div className="benefit-icon">
                <GiftOutlined />
              </div>
              <div className="benefit-content">
                <h4>新用户专享</h4>
                <p>注册即送 50 次免费生成额度</p>
              </div>
            </div>

            <div className="benefit-item">
              <div className="benefit-icon">
                <ThunderboltOutlined />
              </div>
              <div className="benefit-content">
                <h4>极速生成</h4>
                <p>多模型并行，秒级出图</p>
              </div>
            </div>

            <div className="benefit-item">
              <div className="benefit-icon">
                <SafetyCertificateOutlined />
              </div>
              <div className="benefit-content">
                <h4>隐私保护</h4>
                <p>端到端加密，数据安全无忧</p>
              </div>
            </div>
          </div>

          <div className="stats-row">
            <div className="stat-item">
              <span className="stat-value">100K+</span>
              <span className="stat-label">创作者</span>
            </div>
            <div className="stat-divider"></div>
            <div className="stat-item">
              <span className="stat-value">1M+</span>
              <span className="stat-label">作品生成</span>
            </div>
          </div>
        </div>

        {/* 分割线 */}
        <div className="divider">
          <div className="divider-line"></div>
        </div>

        {/* 右侧注册表单区域 */}
        <div className="form-section">
          <h2 className="form-title">
            <span className="title-text">创建账户</span>
            <span className="title-decoration"></span>
          </h2>
          <p className="form-subtitle">只需一步，开启无限可能</p>

          <Form form={form} onFinish={handleSubmit} size="large" className="register-form" autoComplete="off">
            <Form.Item
              name="username"
              rules={[
                { required: true, message: '请输入用户名' },
                { min: 3, max: 20, message: '用户名长度 3-20 个字符' },
                { pattern: /^[a-zA-Z0-9_]+$/, message: '只能包含字母、数字和下划线' },
              ]}
            >
              <Input
                prefix={<UserOutlined className="input-icon" />}
                placeholder="用户名（3-20个字符）"
                className="register-input"
                autoComplete="off"
              />
            </Form.Item>

            <Form.Item
              name="email"
              rules={[
                { type: 'email', message: '请输入有效的邮箱地址' },
              ]}
            >
              <Input
                prefix={<MailOutlined className="input-icon" />}
                placeholder="邮箱（选填，用于找回密码）"
                className="register-input"
                autoComplete="off"
              />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, max: 32, message: '密码长度 6-32 个字符' },
              ]}
            >
              <Input.Password
                prefix={<LockOutlined className="input-icon" />}
                placeholder="设置密码（6-32个字符）"
                className="register-input"
                autoComplete="new-password"
                onChange={(e) => setPasswordStrength(calculatePasswordStrength(e.target.value))}
              />
            </Form.Item>

            {/* 密码强度指示器 */}
            <div className="password-strength">
              <Progress
                percent={passwordStrength}
                showInfo={false}
                strokeColor={getStrengthColor()}
                trailColor="rgba(255,255,255,0.1)"
                size="small"
              />
              <span className="strength-text" style={{ color: getStrengthColor() }}>
                密码强度: {getStrengthText()}
              </span>
            </div>

            <Form.Item
              name="confirmPassword"
              rules={[
                { required: true, message: '请确认密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('两次输入的密码不一致'));
                  },
                }),
              ]}
            >
              <Input.Password
                prefix={<LockOutlined className="input-icon" />}
                placeholder="确认密码"
                className="register-input"
                autoComplete="new-password"
              />
            </Form.Item>

            <Form.Item name="agreement" valuePropName="checked" initialValue={false}>
              <Checkbox className="agreement-checkbox">
                我已阅读并同意
                <a href="/terms" target="_blank" className="link">《用户协议》</a>
                和
                <a href="/privacy" target="_blank" className="link">《隐私政策》</a>
              </Checkbox>
            </Form.Item>

            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                className="register-btn"
                block
              >
                <span className="btn-text">立即注册</span>
                <span className="btn-icon">→</span>
              </Button>
            </Form.Item>
          </Form>

          <div className="login-section">
            <span className="login-text">已有账号?</span>
            <a className="login-link" onClick={onBackToLogin}>
              返回登录
              <span className="login-arrow">←</span>
            </a>
          </div>
        </div>
      </div>
    </Modal>
  );
};

export default RegisterModal;
