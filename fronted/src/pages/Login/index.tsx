import React, { useEffect, useState } from 'react';
import { Card, Form, Input, Button, message, Tabs } from 'antd';
import { UserOutlined, LockOutlined, MailOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { checkEmailAvailable, checkUsernameAvailable, login, register, sendEmailVerifyCode } from '../../api/auth';
import './index.css';

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [activeTab, setActiveTab] = useState('login');
  const navigate = useNavigate();
  const [loginForm] = Form.useForm();
  const [registerForm] = Form.useForm();

  useEffect(() => {
    if (countdown <= 0) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setCountdown((prev) => prev - 1);
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [countdown]);

  const handleLogin = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const response: any = await login(values);
      if (response.code === 200) {
        localStorage.setItem('token', response.data.token);
        message.success('登录成功！');
        navigate('/');
      } else {
        message.error(response.message || '登录失败');
      }
    } catch (error: any) {
      message.error(error.response?.data?.message || '登录失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (values: {
    username: string;
    email: string;
    password: string;
    emailCode: string;
  }) => {
    setLoading(true);
    try {
      const response: any = await register({
        username: values.username,
        email: values.email,
        password: values.password,
        emailCode: values.emailCode,
      });
      if (response.code === 200) {
        message.success('注册成功，请登录！');
        setActiveTab('login');
        registerForm.resetFields();
      } else {
        message.error(response.message || '注册失败');
      }
    } catch (error: any) {
      message.error(error.response?.data?.message || '注册失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const handleSendEmailCode = async () => {
    try {
      const email = (registerForm.getFieldValue('email') as string)?.trim();
      if (!email) {
        message.error('请输入邮箱');
        return;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        message.error('请输入有效的邮箱地址');
        return;
      }
      setSendingCode(true);
      await sendEmailVerifyCode(email, 'register');
      message.success('验证码已发送，请查收邮箱');
      setCountdown(60);
    } catch (error: any) {
      if (error?.errorFields) {
        return;
      }
      message.error(error.response?.data?.message || '发送失败，请稍后重试');
    } finally {
      setSendingCode(false);
    }
  };

  const validateEmailAvailable = async (_: unknown, value?: string) => {
    if (!value) {
      return Promise.resolve();
    }
    const response = await checkEmailAvailable(value);
    if (response.code !== 200) {
      return Promise.reject(new Error(response.message || '邮箱校验失败，请稍后重试'));
    }
    if (!response.data?.available) {
      return Promise.reject(new Error('邮箱已被注册'));
    }
    return Promise.resolve();
  };

  const validateUsernameAvailable = async (_: unknown, value?: string) => {
    if (!value) {
      return Promise.resolve();
    }
    const response = await checkUsernameAvailable(value);
    if (response.code !== 200) {
      return Promise.reject(new Error(response.message || '用户名校验失败，请稍后重试'));
    }
    if (!response.data?.available) {
      return Promise.reject(new Error('用户名已存在'));
    }
    return Promise.resolve();
  };

  const tabItems = [
    {
      key: 'login',
      label: '登录',
      children: (
        <Form
          form={loginForm}
          onFinish={handleLogin}
          size="large"
          className="auth-form"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              登录
            </Button>
          </Form.Item>
        </Form>
      ),
    },
    {
      key: 'register',
      label: '注册',
      children: (
        <Form
          form={registerForm}
          onFinish={handleRegister}
          size="large"
          className="auth-form"
        >
          <Form.Item
            name="username"
            validateTrigger="onBlur"
            rules={[
              { required: true, message: '请输入用户名' },
              { min: 3, max: 20, message: '用户名长度3-20个字符' },
              { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线' },
              { validator: validateUsernameAvailable },
            ]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" />
          </Form.Item>
          <Form.Item
            name="email"
            validateTrigger="onBlur"
            rules={[
              { required: true, message: '请输入邮箱' },
              { type: 'email', message: '请输入有效的邮箱地址' },
              { validator: validateEmailAvailable },
            ]}
          >
            <Input prefix={<MailOutlined />} placeholder="邮箱" />
          </Form.Item>
          <div className="auth-code-row">
            <Form.Item
              name="emailCode"
              rules={[
                { required: true, message: '请输入邮箱验证码' },
                { pattern: /^\d{6}$/, message: '请输入6位邮箱验证码' },
              ]}
              className="auth-code-item"
            >
              <Input prefix={<SafetyCertificateOutlined />} placeholder="邮箱验证码" maxLength={6} />
            </Form.Item>
            <Button
              htmlType="button"
              className="auth-send-code-btn"
              disabled={countdown > 0}
              loading={sendingCode}
              onClick={handleSendEmailCode}
            >
              {countdown > 0 ? `${countdown}s 后重发` : '发送验证码'}
            </Button>
          </div>
          <Form.Item
            name="password"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, message: '密码至少6个字符' },
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            dependencies={['password']}
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
            <Input.Password prefix={<LockOutlined />} placeholder="确认密码" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              注册
            </Button>
          </Form.Item>
        </Form>
      ),
    },
  ];

  return (
    <div className="login-container">
      <Card className="login-card">
        <div className="login-header">
          <h1>Pixel AI</h1>
          <p>AI头像生成平台</p>
        </div>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={tabItems}
          centered
        />
      </Card>
    </div>
  );
};

export default Login;
