import React, { useState, useEffect } from 'react';
import { Modal, Input, Button, Tabs, Form, Checkbox, message } from 'antd';
import {
  UserOutlined,
  LockOutlined,
  MobileOutlined,
  SafetyCertificateOutlined,
  QrcodeOutlined,
  ScanOutlined,
} from '@ant-design/icons';
import { login, sendVerifyCode, loginByPhone } from '../../api/auth';
import type { UserInfo } from '../../api/auth';
import { useAuth } from '../../contexts/AuthContext';
import './index.css';

interface LoginModalProps {
  open: boolean;
  onClose: () => void;
  onLoginSuccess?: (user: UserInfo) => void;
  onRegister?: () => void;
  onForgotPassword?: () => void;
}

type LoginType = 'account' | 'phone';

const LoginModal: React.FC<LoginModalProps> = ({
  open,
  onClose,
  onLoginSuccess,
  onRegister,
  onForgotPassword,
}) => {
  const [loginType, setLoginType] = useState<LoginType>('account');
  const [loading, setLoading] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [form] = Form.useForm();
  const { setCurrentUser } = useAuth();

  // 弹窗打开时恢复记住的账号密码
  useEffect(() => {
    if (open) {
      const remembered = localStorage.getItem('rememberMe');
      if (remembered) {
        try {
          const data = JSON.parse(remembered);
          form.setFieldsValue({
            username: data.username,
            password: data.password,
            remember: true,
          });
        } catch {
          localStorage.removeItem('rememberMe');
        }
      }
    }
  }, [open, form]);

  // 弹窗关闭时根据是否勾选记住我决定是否清空
  useEffect(() => {
    if (!open) {
      const remember = form.getFieldValue('remember');
      if (!remember) {
        form.resetFields();
      }
    }
  }, [open, form]);

  // 发送验证码
  const handleSendCode = async () => {
    try {
      const phone = form.getFieldValue('phone');
      if (!phone || !/^1[3-9]\d{9}$/.test(phone)) {
        message.error('请输入正确的手机号');
        return;
      }
      await sendVerifyCode(phone);
      message.success('验证码已发送');
      setCountdown(60);
      const timer = setInterval(() => {
        setCountdown((prev) => {
          if (prev <= 1) {
            clearInterval(timer);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } catch {
      message.error('发送失败，请重试');
    }
  };

  // 登录提交
  const handleSubmit = async (values: Record<string, unknown>) => {
    console.log('handleSubmit triggered:', values);
    setLoading(true);
    try {
      let res: unknown;

      if (loginType === 'account') {
        console.log('Calling login API...');
        res = await login({
          username: values.username as string,
          password: values.password as string,
        });
        console.log('Login response:', res);
      } else {
        res = await loginByPhone(values.phone as string, values.code as string);
      }

      const response = res as { code: number; data: { token: string; user: UserInfo }; message?: string };

      if (response.code === 200) {
        localStorage.setItem('token', response.data.token);

        // 更新全局用户状态
        setCurrentUser(response.data.user);

        // 处理记住我
        if (loginType === 'account' && values.remember) {
          localStorage.setItem('rememberMe', JSON.stringify({
            username: values.username,
            password: values.password,
          }));
        } else {
          localStorage.removeItem('rememberMe');
        }

        onLoginSuccess?.(response.data.user);
        onClose();
      } else {
        message.error(response.message || '登录失败');
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '登录失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  // 账号密码登录表单
  const AccountLoginForm = (
    <Form form={form} onFinish={handleSubmit} size="large" autoComplete="off">
      {/* 隐藏输入框欺骗浏览器自动填充 */}
      <input type="text" style={{ display: 'none' }} />
      <input type="password" style={{ display: 'none' }} />

      <Form.Item
        name="username"
        rules={[{ required: true, message: '请输入用户名/邮箱' }]}
      >
        <Input
          prefix={<UserOutlined className="input-icon" />}
          placeholder="用户名 / 邮箱"
          className="login-input"
          autoComplete="off"
        />
      </Form.Item>
      <Form.Item
        name="password"
        rules={[{ required: true, message: '请输入密码' }]}
      >
        <Input.Password
          prefix={<LockOutlined className="input-icon" />}
          placeholder="密码"
          className="login-input"
          autoComplete="off"
        />
      </Form.Item>
      <Form.Item>
        <div className="form-extra">
          <Form.Item name="remember" valuePropName="checked" noStyle>
            <Checkbox className="remember-checkbox">记住我</Checkbox>
          </Form.Item>
          <a className="forgot-link" onClick={onForgotPassword}>
            忘记密码?
          </a>
        </div>
      </Form.Item>
      <Form.Item>
        <Button
          type="primary"
          htmlType="submit"
          loading={loading}
          className="login-btn"
          block
        >
          登 录
        </Button>
      </Form.Item>
    </Form>
  );

  // 手机验证码登录表单
  const PhoneLoginForm = (
    <Form form={form} onFinish={handleSubmit} size="large" autoComplete="off">
      <Form.Item
        name="phone"
        rules={[
          { required: true, message: '请输入手机号' },
          { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' },
        ]}
      >
        <Input
          prefix={<MobileOutlined className="input-icon" />}
          placeholder="手机号"
          className="login-input"
          maxLength={11}
          autoComplete="off"
        />
      </Form.Item>
      <Form.Item
        name="code"
        rules={[{ required: true, message: '请输入验证码' }]}
      >
        <div className="code-input-wrapper">
          <Input
            prefix={<SafetyCertificateOutlined className="input-icon" />}
            placeholder="验证码"
            className="login-input code-input"
            maxLength={6}
          />
          <Button
            className="send-code-btn"
            disabled={countdown > 0}
            onClick={handleSendCode}
          >
            {countdown > 0 ? `${countdown}s` : '获取验证码'}
          </Button>
        </div>
      </Form.Item>
      <Form.Item>
        <Button
          type="primary"
          htmlType="submit"
          loading={loading}
          className="login-btn"
          block
        >
          登 录
        </Button>
      </Form.Item>
    </Form>
  );

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      width={920}
      centered
      closable={false}
      rootClassName="login-modal"
      wrapClassName="login-modal-wrap"
      maskClosable={false}
      keyboard={false}
      classNames={{
        wrapper: 'login-modal-wrapper',
        container: 'login-modal-content',
        body: 'login-modal-body',
      }}
      styles={{
        mask: {
          backdropFilter: 'blur(4px)',
        },
        container: {
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
      <div className="login-modal-container">
        {/* 关闭按钮 */}
        <button className="close-btn" onClick={onClose}>
          <span className="close-icon">×</span>
        </button>

        {/* 装饰性元素 */}
        <div className="decor-circle decor-circle-1"></div>
        <div className="decor-circle decor-circle-2"></div>
        <div className="decor-line decor-line-1"></div>
        <div className="decor-line decor-line-2"></div>

        {/* 左侧二维码区域 */}
        <div className="qrcode-section">
          <div className="qrcode-header">
            <ScanOutlined className="scan-icon" />
            <span>扫码登录</span>
          </div>
          <div className="qrcode-wrapper">
            <div className="qrcode-box">
              <QrcodeOutlined className="qrcode-placeholder" />
              <div className="qrcode-corner corner-tl"></div>
              <div className="qrcode-corner corner-tr"></div>
              <div className="qrcode-corner corner-bl"></div>
              <div className="qrcode-corner corner-br"></div>
            </div>
            <div className="scan-line"></div>
          </div>
          <p className="qrcode-tip">
            打开 <span className="highlight">Pixel APP</span>
            <br />
            扫一扫登录
          </p>
          <div className="qrcode-features">
            <div className="feature-item">
              <div className="feature-dot"></div>
              <span>安全便捷</span>
            </div>
            <div className="feature-item">
              <div className="feature-dot"></div>
              <span>免密登录</span>
            </div>
          </div>
        </div>

        {/* 分割线 */}
        <div className="divider">
          <div className="divider-line"></div>
          <span className="divider-text">OR</span>
          <div className="divider-line"></div>
        </div>

        {/* 右侧登录表单区域 */}
        <div className="form-section">
          <h2 className="form-title">
            <span className="title-text">欢迎回来</span>
            <span className="title-decoration"></span>
          </h2>
          <p className="form-subtitle">登录您的 Pixel 账户</p>

          <Tabs
            activeKey={loginType}
            onChange={(key) => {
              setLoginType(key as LoginType);
              form.resetFields();
            }}
            className="login-tabs"
            items={[
              {
                key: 'account',
                label: '账号登录',
                children: AccountLoginForm,
              },
              {
                key: 'phone',
                label: '手机登录',
                children: PhoneLoginForm,
              },
            ]}
          />

          <div className="register-section">
            <a className="forgot-password-link" onClick={onForgotPassword}>
              忘记密码?
            </a>
            <div className="register-wrapper">
              <span className="register-text">还没有账号?</span>
              <a className="register-link" onClick={onRegister}>
                立即注册
                <span className="register-arrow">→</span>
              </a>
            </div>
          </div>
        </div>
      </div>
    </Modal>
  );
};

export default LoginModal;
