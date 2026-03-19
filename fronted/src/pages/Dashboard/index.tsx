import React, { useState, useEffect, useCallback } from 'react';
import { Layout, Menu, Avatar, Popover, Button, Badge, Drawer, Modal, Form, Input, Empty, Spin, message } from 'antd';
import {
  FireOutlined,
  AppstoreOutlined,
  FolderOutlined,
  BorderOutlined,
  PictureOutlined,
  VideoCameraOutlined,
  AudioOutlined,
  UserOutlined,
  WalletOutlined,
  CrownOutlined,
  FileTextOutlined,
  CustomerServiceOutlined,
  LogoutOutlined,
  RightOutlined,
  LeftOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  GiftOutlined,
  StarOutlined,
  SunOutlined,
  MoonOutlined,
  BellOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import type { MenuProps } from 'antd';
import dayjs from 'dayjs';
import { sendEmailVerifyCode, sendPhoneVerifyCode } from '../../api/auth';
import { useAuth } from '../../contexts/AuthContext';
import {
  bindEmailTask,
  bindPhoneTask,
  claimGrowthReward,
  completeProfileTask,
  getGrowthTasks,
  verifyRealNameTask,
} from '../../api/user';
import type { GrowthTask, GrowthTaskAction } from '../../api/user';
import { getHotImageNotifications, claimHotImageReward } from '../../api/image';
import type { HotImageNotification } from '../../api/image';
import logoSvg from '../../assets/ip.svg';
import './index.css';

const { Sider, Content } = Layout;

type GrowthTaskListPayload = {
  pendingCount?: number;
  tasks?: GrowthTask[];
};

type TaskViewMode = 'pending' | 'history';

type TaskModalAction = Exclude<GrowthTaskAction, 'CLAIM' | 'NONE'>;
type TaskFormValues = {
  nickname?: string;
  avatar?: string;
  email?: string;
  emailCode?: string;
  phone?: string;
  phoneCode?: string;
  realName?: string;
  idCard?: string;
};

const Dashboard: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const [userPopoverOpen, setUserPopoverOpen] = useState(false);
  const [taskDrawerOpen, setTaskDrawerOpen] = useState(false);
  const [profileModalOpen, setProfileModalOpen] = useState(false);
  const [activeTaskAction, setActiveTaskAction] = useState<TaskModalAction | null>(null);
  const [taskLoading, setTaskLoading] = useState(false);
  const [profileSubmitting, setProfileSubmitting] = useState(false);
  const [taskCodeSending, setTaskCodeSending] = useState<'email' | 'phone' | null>(null);
  const [emailCodeCountdown, setEmailCodeCountdown] = useState(0);
  const [phoneCodeCountdown, setPhoneCodeCountdown] = useState(0);
  const [pendingCount, setPendingCount] = useState(0);
  const [growthTasks, setGrowthTasks] = useState<GrowthTask[]>([]);
  const [taskViewMode, setTaskViewMode] = useState<TaskViewMode>('pending');
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);
  const [theme, setTheme] = useState<'dark' | 'light'>(() => {
    return (localStorage.getItem('dashboard-theme') as 'dark' | 'light') || 'dark';
  });
  const [profileForm] = Form.useForm<TaskFormValues>();
  const { currentUser, logout: authLogout, refreshUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const toggleTheme = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    setTheme(next);
    localStorage.setItem('dashboard-theme', next);
  };

  // 同步主题到 body，让 Portal 组件（Popover 等）也能响应
  useEffect(() => {
    document.body.setAttribute('data-theme', theme);
    return () => {
      document.body.removeAttribute('data-theme');
    };
  }, [theme]);

  useEffect(() => {
    if (emailCodeCountdown <= 0) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setEmailCodeCountdown((prev) => prev - 1);
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [emailCodeCountdown]);

  useEffect(() => {
    if (phoneCodeCountdown <= 0) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setPhoneCodeCountdown((prev) => prev - 1);
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [phoneCodeCountdown]);

  const loadGrowthTasks = useCallback(async (silent: boolean = true) => {
    if (!localStorage.getItem('token')) {
      setPendingCount(0);
      setGrowthTasks([]);
      return;
    }

    setTaskLoading(true);
    try {
      const [growthRes, hotRes] = await Promise.all([
        getGrowthTasks() as unknown as {
          code: number;
          message?: string;
          data?: GrowthTaskListPayload;
        },
        getHotImageNotifications().then(r => r as unknown as {
          code: number;
          data?: HotImageNotification[];
        }).catch(() => ({ code: 0, data: [] as HotImageNotification[] })),
      ]);

      let tasks: GrowthTask[] = [];
      let pending = 0;

      if (growthRes.code === 200 && growthRes.data) {
        pending = growthRes.data.pendingCount || 0;
        tasks = growthRes.data.tasks || [];
      }

      // 将热门图片通知转为 GrowthTask 兼容格式合并
      if (hotRes.code === 200 && hotRes.data && hotRes.data.length > 0) {
        const hotTasks: GrowthTask[] = hotRes.data.map((n: HotImageNotification) => ({
          recordId: n.hotImageId,
          activityCode: `hot_image_${n.hotImageId}`,
          title: n.title,
          description: n.description,
          triggerType: 'hot_image',
          status: n.status === 'CLAIMABLE' ? 'CLAIMABLE' : ('REJECTED' as GrowthTask['status']),
          actionType: (n.actionType === 'CLAIM_HOT' ? 'CLAIM_HOT' : 'NONE') as GrowthTask['actionType'],
          rewardSummary: n.rewardSummary,
          createdAt: n.createdAt,
        }));
        const hotPending = hotTasks.filter(t => t.status === 'CLAIMABLE').length;
        pending += hotPending;
        tasks = [...tasks, ...hotTasks];
      }

      setPendingCount(pending);
      setGrowthTasks(tasks);

      if (growthRes.code !== 200 && !silent) {
        message.error(growthRes.message || '获取通知失败');
      }
    } catch (error: unknown) {
      if (!silent) {
        const err = error as { response?: { data?: { message?: string } } };
        message.error(err.response?.data?.message || '获取通知失败');
      }
    } finally {
      setTaskLoading(false);
    }
  }, []);

  useEffect(() => {
    if (currentUser?.id) {
      loadGrowthTasks();
      return;
    }
    setPendingCount(0);
    setGrowthTasks([]);
  }, [currentUser?.id, loadGrowthTasks]);

  // 第一部分菜单：热门、模版、资产
  const menuItemsPart1: MenuProps['items'] = [
    {
      key: '/dashboard/hot',
      icon: <FireOutlined />,
      label: '热门',
    },
    {
      key: '/dashboard/templates',
      icon: <AppstoreOutlined />,
      label: '模版',
    },
    {
      key: '/dashboard/assets',
      icon: <FolderOutlined />,
      label: '资产',
    },
  ];

  // 第二部分菜单：AI创作
  const menuItemsPart2: MenuProps['items'] = [
    {
      key: '/dashboard/canvas',
      icon: <BorderOutlined />,
      label: '画布',
    },
    {
      key: '/dashboard',
      icon: <PictureOutlined />,
      label: '图片生成',
    },
    {
      key: '/dashboard/video',
      icon: <VideoCameraOutlined />,
      label: '视频生成',
    },
    {
      key: '/dashboard/audio',
      icon: <AudioOutlined />,
      label: '音频生成',
    },
  ];

  // 第三部分菜单：社区
  const menuItemsPart3: MenuProps['items'] = [
    {
      key: '/dashboard/community',
      icon: <TeamOutlined />,
      label: '分享社区',
    },
  ];

  const handleMenuClick: MenuProps['onClick'] = (e) => {
    navigate(e.key);
  };

  const handleLogout = () => {
    authLogout();
    navigate('/');
  };

  const openTaskDrawer = () => {
    setTaskViewMode('pending');
    setTaskDrawerOpen(true);
    loadGrowthTasks(false);
  };
  const pendingTasks = growthTasks.filter((task) => task.status !== 'CLAIMED');
  const historyTasks = growthTasks.filter((task) => task.status === 'CLAIMED');
  const visibleGrowthTasks = taskViewMode === 'history' ? historyTasks : pendingTasks;

  const openTaskModal = (actionType: TaskModalAction) => {
    setActiveTaskAction(actionType);
    profileForm.setFieldsValue({
      nickname: currentUser?.nickname || currentUser?.username || '',
      avatar: currentUser?.avatar || '',
      email: currentUser?.email || '',
      emailCode: '',
      phone: currentUser?.phone || '',
      phoneCode: '',
      realName: currentUser?.realName || '',
      idCard: '',
    });
    setEmailCodeCountdown(0);
    setPhoneCodeCountdown(0);
    setProfileModalOpen(true);
  };

  const handleTaskAction = async (task: GrowthTask) => {
    if (
      task.actionType === 'COMPLETE_PROFILE'
      || task.actionType === 'BIND_EMAIL'
      || task.actionType === 'BIND_PHONE'
      || task.actionType === 'VERIFY_REAL_NAME'
    ) {
      openTaskModal(task.actionType as TaskModalAction);
      return;
    }

    // 热门图片领取奖励
    if (task.actionType === 'CLAIM_HOT' && task.recordId) {
      setActionLoadingId(task.recordId);
      try {
        const res = await claimHotImageReward(task.recordId) as unknown as {
          code: number;
          message?: string;
          data?: { pointsAdded?: number };
        };
        if (res.code !== 200) {
          message.error(res.message || '领取失败');
          return;
        }
        const pts = res.data?.pointsAdded;
        message.success(pts ? `领取成功 +${pts}积分` : '奖励领取成功');
        await Promise.all([loadGrowthTasks(), refreshUser()]);
      } catch (error: unknown) {
        const err = error as { response?: { data?: { message?: string } } };
        message.error(err.response?.data?.message || '领取失败，请稍后重试');
      } finally {
        setActionLoadingId(null);
      }
      return;
    }

    if (task.actionType !== 'CLAIM' || !task.recordId) {
      return;
    }

    setActionLoadingId(task.recordId);
    try {
      const res = await claimGrowthReward(task.recordId) as unknown as {
        code: number;
        message?: string;
        data?: {
          pointsAdded?: number;
          quotaAdded?: number;
        };
      };

      if (res.code !== 200) {
        message.error(res.message || '领取失败');
        return;
      }

      const rewards: string[] = [];
      if (res.data?.pointsAdded) {
        rewards.push(`+${res.data.pointsAdded}积分`);
      }
      if (res.data?.quotaAdded) {
        rewards.push(`+${res.data.quotaAdded}次免费额度`);
      }

      message.success(rewards.length > 0 ? `领取成功 ${rewards.join('，')}` : '奖励领取成功');
      await Promise.all([loadGrowthTasks(), refreshUser()]);
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '领取失败，请稍后重试');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleProfileSubmit = async () => {
    try {
      const values = await profileForm.validateFields();
      setProfileSubmitting(true);
      let request: Promise<unknown>;
      if (activeTaskAction === 'COMPLETE_PROFILE') {
        request = completeProfileTask({
          nickname: values.nickname || '',
          avatar: values.avatar || '',
        });
      } else if (activeTaskAction === 'BIND_EMAIL') {
        request = bindEmailTask({
          email: values.email || '',
          code: values.emailCode || '',
        });
      } else if (activeTaskAction === 'BIND_PHONE') {
        request = bindPhoneTask({
          phone: values.phone || '',
          code: values.phoneCode || '',
        });
      } else if (activeTaskAction === 'VERIFY_REAL_NAME') {
        request = verifyRealNameTask({
          realName: values.realName || '',
          idCard: values.idCard || '',
        });
      } else {
        message.error('未知的任务类型');
        return;
      }

      const res = await request as unknown as {
        code: number;
        message?: string;
        data?: {
          message?: string;
        };
      };

      if (res.code !== 200) {
        message.error(res.message || '任务提交失败');
        return;
      }

      message.success(res.data?.message || res.message || '任务已完成，请在通知中领取奖励');
      setProfileModalOpen(false);
      setActiveTaskAction(null);
      await Promise.all([loadGrowthTasks(), refreshUser()]);
    } catch (error: unknown) {
      const formError = error as { errorFields?: Array<unknown>; response?: { data?: { message?: string } } };
      if (formError.errorFields) {
        return;
      }
      message.error(formError.response?.data?.message || '任务提交失败，请稍后重试');
    } finally {
      setProfileSubmitting(false);
    }
  };

  const getTaskStatusText = (task: GrowthTask) => {
    if (task.status === 'CLAIMABLE') {
      return '待领取';
    }
    if (task.status === 'ACTION_REQUIRED') {
      return '待完成';
    }
    if ((task.status as string) === 'REJECTED') {
      return '未通过';
    }
    return '已领取';
  };

  const getTaskTimeText = (task: GrowthTask) => {
    const time = task.claimedAt || task.createdAt;
    if (!time) {
      return '';
    }
    return dayjs(time).format('YYYY-MM-DD HH:mm');
  };

  const getTaskActionRequiredTip = (task: GrowthTask) => {
    if (task.actionType === 'BIND_EMAIL') {
      return '绑定邮箱后可用于找回密码，完成后即可领取奖励。';
    }
    if (task.actionType === 'BIND_PHONE') {
      return '绑定手机号后可增强账号安全，完成后即可领取奖励。';
    }
    if (task.actionType === 'VERIFY_REAL_NAME') {
      return '完成实名认证后可提升合规与风控能力，完成后即可领取奖励。';
    }
    return '先完善基础资料，完成后会变成可领取状态。';
  };

  const getTaskActionButtonText = (task: GrowthTask) => {
    if (task.status === 'CLAIMED') {
      return '已领取';
    }
    if ((task.status as string) === 'REJECTED') {
      return '未通过';
    }
    if (task.status === 'CLAIMABLE') {
      return task.actionType === 'CLAIM_HOT' ? '领取积分' : '领取奖励';
    }
    if (task.actionType === 'BIND_EMAIL' || task.actionType === 'BIND_PHONE') {
      return '去绑定';
    }
    if (task.actionType === 'VERIFY_REAL_NAME') {
      return '去认证';
    }
    return '去完善';
  };

  const getTaskModalTitle = () => {
    if (activeTaskAction === 'BIND_EMAIL') {
      return '绑定邮箱';
    }
    if (activeTaskAction === 'BIND_PHONE') {
      return '绑定手机号';
    }
    if (activeTaskAction === 'VERIFY_REAL_NAME') {
      return '实名认证';
    }
    return '完善基础资料';
  };

  const getTaskModalTip = () => {
    if (activeTaskAction === 'BIND_EMAIL') {
      return '邮箱将用于密码找回与安全通知，请先获取验证码，保存后会在通知中心生成待领取奖励。';
    }
    if (activeTaskAction === 'BIND_PHONE') {
      return '手机号将用于账号找回与风险校验，请先获取验证码，保存后会在通知中心生成待领取奖励。';
    }
    if (activeTaskAction === 'VERIFY_REAL_NAME') {
      return '实名认证用于平台合规和非法内容风控，完成后会在通知中心生成待领取奖励。';
    }
    return '保存后会在通知中心生成“完善资料奖励”，需要你再手动领取。';
  };

  const getTaskModalOkText = () => {
    if (activeTaskAction === 'VERIFY_REAL_NAME') {
      return '提交认证并生成奖励';
    }
    return '保存并生成奖励';
  };

  const handleSendTaskCode = async (targetType: 'email' | 'phone') => {
    try {
      if (targetType === 'email') {
        const email = await profileForm.validateFields(['email']).then(() => profileForm.getFieldValue('email') as string);
        setTaskCodeSending('email');
        await sendEmailVerifyCode(email, 'bind_email');
        message.success('邮箱验证码已发送，请查收邮箱');
        setEmailCodeCountdown(60);
        return;
      }

      const phone = await profileForm.validateFields(['phone']).then(() => profileForm.getFieldValue('phone') as string);
      setTaskCodeSending('phone');
      await sendPhoneVerifyCode(phone, 'bind_phone');
      message.success('手机验证码已发送，请查看后端终端输出');
      setPhoneCodeCountdown(60);
    } catch (error: unknown) {
      const formError = error as { errorFields?: Array<unknown>; response?: { data?: { message?: string } } };
      if (formError.errorFields) {
        return;
      }
      message.error(formError.response?.data?.message || '发送失败，请稍后重试');
    } finally {
      setTaskCodeSending(null);
    }
  };

  const renderTaskFormItems = () => {
    if (activeTaskAction === 'BIND_EMAIL') {
      return (
        <>
          <Form.Item
            name="email"
            label="邮箱地址"
            rules={[
              { required: true, message: '请输入邮箱地址' },
              { type: 'email', message: '请输入有效的邮箱地址' },
            ]}
          >
            <Input placeholder="请输入常用邮箱" />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
            <Form.Item
              name="emailCode"
              label="邮箱验证码"
              rules={[
                { required: true, message: '请输入邮箱验证码' },
                { pattern: /^\d{6}$/, message: '请输入6位邮箱验证码' },
              ]}
              style={{ flex: 1, marginBottom: 0 }}
            >
              <Input placeholder="请输入邮箱验证码" maxLength={6} />
            </Form.Item>
            <Button
              htmlType="button"
              style={{ marginTop: 30, minWidth: 132 }}
              disabled={emailCodeCountdown > 0}
              loading={taskCodeSending === 'email'}
              onClick={() => handleSendTaskCode('email')}
            >
              {emailCodeCountdown > 0 ? `${emailCodeCountdown}s 后重发` : '发送验证码'}
            </Button>
          </div>
        </>
      );
    }

    if (activeTaskAction === 'BIND_PHONE') {
      return (
        <>
          <Form.Item
            name="phone"
            label="手机号"
            rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1\d{10}$/, message: '请输入正确的手机号' },
            ]}
          >
            <Input placeholder="请输入常用手机号" maxLength={11} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
            <Form.Item
              name="phoneCode"
              label="手机验证码"
              rules={[
                { required: true, message: '请输入手机验证码' },
                { pattern: /^\d{6}$/, message: '请输入6位手机验证码' },
              ]}
              style={{ flex: 1, marginBottom: 0 }}
            >
              <Input placeholder="请输入手机验证码" maxLength={6} />
            </Form.Item>
            <Button
              htmlType="button"
              style={{ marginTop: 30, minWidth: 132 }}
              disabled={phoneCodeCountdown > 0}
              loading={taskCodeSending === 'phone'}
              onClick={() => handleSendTaskCode('phone')}
            >
              {phoneCodeCountdown > 0 ? `${phoneCodeCountdown}s 后重发` : '发送验证码'}
            </Button>
          </div>
        </>
      );
    }

    if (activeTaskAction === 'VERIFY_REAL_NAME') {
      return (
        <>
          <Form.Item
            name="realName"
            label="真实姓名"
            rules={[
              { required: true, message: '请输入真实姓名' },
              { max: 30, message: '真实姓名最多 30 个字符' },
            ]}
          >
            <Input placeholder="请输入真实姓名" maxLength={30} />
          </Form.Item>
          <Form.Item
            name="idCard"
            label="身份证号"
            rules={[
              { required: true, message: '请输入身份证号' },
              { pattern: /^(\d{15}|\d{17}[\dXx])$/, message: '请输入正确的身份证号' },
            ]}
          >
            <Input placeholder="请输入身份证号" maxLength={18} />
          </Form.Item>
        </>
      );
    }

    return (
      <>
        <Form.Item
          name="nickname"
          label="昵称"
          rules={[
            { required: true, message: '请输入昵称' },
            { max: 20, message: '昵称最多 20 个字符' },
          ]}
        >
          <Input placeholder="请输入新的昵称" maxLength={20} />
        </Form.Item>
        <Form.Item
          name="avatar"
          label="头像地址"
          rules={[
            { required: true, message: '请输入头像地址' },
            { type: 'url', message: '请输入有效的图片 URL' },
          ]}
        >
          <Input placeholder="请输入头像图片 URL" />
        </Form.Item>
      </>
    );
  };

  // 用户弹出面板内容
  const userPopoverContent = (
    <div className="user-popover-content">
      {/* 用户信息头部 */}
      <div className="user-popover-header">
        <Avatar
          src={currentUser?.avatar}
          icon={<UserOutlined />}
          size={48}
          className="popover-avatar"
        />
        <div className="popover-user-info">
          <div className="popover-nickname">
            {currentUser?.nickname || currentUser?.username}
            {currentUser?.isVip && <CrownOutlined className="popover-vip-badge" />}
          </div>
          <div className="popover-user-id">ID: {currentUser?.id}</div>
        </div>
      </div>

      {/* 余额信息 */}
      <div className="balance-card">
        {/* 总额度 */}
        <div className="quota-section">
          <div className="quota-main">
            <div className="quota-icon">
              <ThunderboltOutlined />
            </div>
            <div className="quota-info">
              <span className="quota-label">总额度</span>
              <span className="quota-value">
                {(() => {
                  const freeQuota = currentUser?.freeQuota || 0;
                  const monthlyQuota = currentUser?.monthlyQuota || 0;
                  const monthlyQuotaUsed = currentUser?.monthlyQuotaUsed || 0;
                  return freeQuota + monthlyQuota - monthlyQuotaUsed;
                })()}
              </span>
            </div>
          </div>
          <div className="quota-sub">
            <GiftOutlined />
            <span>免费额度: {currentUser?.freeQuota || 0}</span>
          </div>
        </div>

        {/* 统计数据 */}
        <div className="stats-grid">
          <div className="stat-item">
            <div className="stat-icon today">
              <PictureOutlined />
            </div>
            <div className="stat-content">
              <span className="stat-label">今日已生成</span>
              <span className="stat-value">
                {currentUser?.dailyUsed || 0}
                <span className="stat-limit">
                  / {currentUser?.isVip ? '∞' : (currentUser?.dailyLimit || 10)}
                </span>
              </span>
            </div>
          </div>
          <div className="stat-item">
            <div className="stat-icon points">
              <StarOutlined />
            </div>
            <div className="stat-content">
              <span className="stat-label">我的积分</span>
              <span className="stat-value">{currentUser?.points || 0}</span>
            </div>
          </div>
        </div>

        <Button type="primary" className="recharge-btn" onClick={() => navigate('/dashboard/recharge')}>
          <WalletOutlined />
          去充值
        </Button>
      </div>

      {/* 菜单列表 */}
      <div className="popover-menu">
        <div className="popover-menu-item" onClick={() => { setUserPopoverOpen(false); navigate('/dashboard/account'); }}>
          <UserOutlined />
          <span>账户管理</span>
        </div>
        <div className="popover-menu-item" onClick={() => { setUserPopoverOpen(false); navigate('/dashboard/wallet'); }}>
          <WalletOutlined />
          <span>钱包</span>
        </div>
        <div className="popover-menu-item" onClick={() => { setUserPopoverOpen(false); navigate('/dashboard/vip'); }}>
          <CrownOutlined />
          <span>会员充值</span>
        </div>
        <div className="popover-menu-item" onClick={() => { setUserPopoverOpen(false); navigate('/dashboard/orders'); }}>
          <FileTextOutlined />
          <span>订单管理</span>
        </div>
        <div className="popover-menu-item" onClick={() => { setUserPopoverOpen(false); navigate('/dashboard/support'); }}>
          <CustomerServiceOutlined />
          <span>工单</span>
        </div>
        <div className="popover-menu-divider"></div>
        <div className="popover-menu-item logout" onClick={handleLogout}>
          <LogoutOutlined />
          <span>退出登录</span>
        </div>
      </div>

      {/* 底部链接 */}
      <div className="popover-footer">
        <a href="/help" target="_blank">帮助文档</a>
        <a href="/" target="_blank">官网</a>
      </div>
    </div>
  );

  return (
    <Layout className={`dashboard-layout ${theme === 'light' ? 'theme-light' : ''}`}>
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={220}
        collapsedWidth={72}
        className="dashboard-sider"
      >
        <div className="sider-logo">
          <div className="logo-left" onClick={() => navigate('/')}>
            <img src={logoSvg} alt="Pixel" className="logo-img" />
            {!collapsed && <span className="logo-text">Pixel</span>}
          </div>
          <div className="logo-actions">
            <Badge
              dot={pendingCount > 0}
              offset={collapsed ? [2, 6] : [-2, 4]}
              className={`notification-badge ${collapsed ? 'collapsed' : ''}`}
            >
              <div className="notification-trigger" onClick={openTaskDrawer}>
                <BellOutlined />
              </div>
            </Badge>
            {!collapsed && (
              <div className="theme-toggle" onClick={toggleTheme}>
                {theme === 'dark' ? <SunOutlined /> : <MoonOutlined />}
              </div>
            )}
          </div>
        </div>

        {/* 第一部分菜单：热门、模版、资产 */}
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItemsPart1}
          onClick={handleMenuClick}
          className="sider-menu sider-menu-part1"
        />

        {/* 第二部分菜单：AI创作 */}
        <div className="menu-section-header">
          {!collapsed && <span>AI 创作</span>}
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItemsPart2}
          onClick={handleMenuClick}
          className="sider-menu sider-menu-part2"
        />

        {/* 第三部分菜单：社区 */}
        <div className="menu-section-header">
          {!collapsed && <span>社区</span>}
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItemsPart3}
          onClick={handleMenuClick}
          className="sider-menu sider-menu-part3"
        />

        {/* 余额和积分 */}
        <div className="balance-section">
          {!collapsed ? (
            <>
              <div className="balance-row">
                <span className="balance-label">总额度</span>
                <span className="balance-value">
                  {(() => {
                    const freeQuota = currentUser?.freeQuota || 0;
                    const monthlyQuota = currentUser?.monthlyQuota || 0;
                    const monthlyQuotaUsed = currentUser?.monthlyQuotaUsed || 0;
                    const total = freeQuota + monthlyQuota - monthlyQuotaUsed;
                    return total;
                  })()}
                  <span className="free-quota-hint"> ({currentUser?.freeQuota || 0})</span>
                </span>
              </div>
              <div className="balance-row">
                <span className="balance-label">积分</span>
                <span className="balance-value">{currentUser?.points || 0}</span>
              </div>
            </>
          ) : (
            <div className="balance-collapsed">
              <WalletOutlined />
            </div>
          )}
        </div>

        {/* 底部用户信息 */}
        <div className="sider-footer">
          <Popover
            content={userPopoverContent}
            trigger="click"
            placement="rightBottom"
            open={userPopoverOpen}
            onOpenChange={setUserPopoverOpen}
            overlayClassName="user-popover"
            arrow={false}
          >
            <div className="user-trigger">
              <Avatar
                src={currentUser?.avatar}
                icon={<UserOutlined />}
                size={collapsed ? 36 : 40}
                className="user-avatar"
              />
              {!collapsed && (
                <>
                  <div className="user-details">
                    <span className="user-name">
                      {currentUser?.nickname || currentUser?.username || '用户'}
                    </span>
                    {currentUser?.isVip && <CrownOutlined className="vip-icon" />}
                  </div>
                  <RightOutlined className="expand-icon" />
                </>
              )}
            </div>
          </Popover>
        </div>
      </Sider>

      {/* 侧边栏外部收缩按钮 */}
      <div
        className={`collapse-toggle ${collapsed ? 'collapsed' : ''}`}
        onClick={() => setCollapsed(!collapsed)}
      >
        {collapsed ? <RightOutlined /> : <LeftOutlined />}
      </div>

      <Layout className="dashboard-main">
        <Content className="dashboard-content">
          <Outlet />
        </Content>
      </Layout>

      <Drawer
        title="通知中心"
        placement="right"
        width={420}
        open={taskDrawerOpen}
        onClose={() => setTaskDrawerOpen(false)}
        rootClassName="notification-drawer"
      >
        <div className="notification-summary">
          <div className="notification-summary-main">
            <span className="notification-summary-label">
              {taskViewMode === 'history' ? '历史任务' : '待处理任务'}
            </span>
            <span className="notification-summary-value">
              {taskViewMode === 'history' ? historyTasks.length : pendingCount}
            </span>
          </div>
          <div className="notification-summary-tip">
            {taskViewMode === 'history'
              ? '这里会展示你已经完成或已领取过的成长任务记录。'
              : '注册礼包、账号完善与合规任务奖励会先进入这里，需要你手动领取。'}
          </div>
        </div>

        <div className="notification-view-switch">
          <button
            type="button"
            className={`notification-view-btn ${taskViewMode === 'pending' ? 'active' : ''}`}
            onClick={() => setTaskViewMode('pending')}
          >
            待处理
          </button>
          <button
            type="button"
            className={`notification-view-btn ${taskViewMode === 'history' ? 'active' : ''}`}
            onClick={() => setTaskViewMode('history')}
          >
            历史任务
          </button>
        </div>

        {taskLoading ? (
          <div className="notification-loading">
            <Spin size="large" />
          </div>
        ) : visibleGrowthTasks.length > 0 ? (
          <div className="notification-task-list">
            {visibleGrowthTasks.map((task) => (
              <div className="notification-task-card" key={`${task.activityCode}-${task.recordId || 'action'}`}>
                <div className="notification-task-header">
                  <div>
                    <div className="notification-task-title">{task.title}</div>
                    <div className="notification-task-time">{getTaskTimeText(task)}</div>
                  </div>
                  <span className={`notification-task-badge ${task.status.toLowerCase()}`}>
                    {getTaskStatusText(task)}
                  </span>
                </div>

                <div className="notification-task-desc">{task.description}</div>

                {task.rewardSummary && (
                  <div className="notification-task-reward">{task.rewardSummary}</div>
                )}

                <div className="notification-task-footer">
                  <span className="notification-task-tip">
                    {task.status === 'CLAIMED'
                      ? '奖励已到账，可继续完成其他任务。'
                      : task.status === 'CLAIMABLE'
                        ? '点击右侧按钮后立即到账。'
                        : getTaskActionRequiredTip(task)}
                  </span>
                  <Button
                    type={task.status === 'CLAIMABLE' ? 'primary' : 'default'}
                    ghost={task.status !== 'CLAIMABLE'}
                    className={`notification-task-action ${task.status.toLowerCase()}`}
                    disabled={task.status === 'CLAIMED' || (task.status as string) === 'REJECTED'}
                    loading={task.recordId ? actionLoadingId === task.recordId : false}
                    onClick={() => handleTaskAction(task)}
                  >
                    {getTaskActionButtonText(task)}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="notification-empty">
            <Empty description={taskViewMode === 'history' ? '暂无历史任务' : '暂无待处理通知'} />
          </div>
        )}
      </Drawer>

      <Modal
        title={getTaskModalTitle()}
        open={profileModalOpen}
        onCancel={() => {
          setProfileModalOpen(false);
          setActiveTaskAction(null);
          setTaskCodeSending(null);
          setEmailCodeCountdown(0);
          setPhoneCodeCountdown(0);
        }}
        onOk={handleProfileSubmit}
        okText={getTaskModalOkText()}
        cancelText="取消"
        confirmLoading={profileSubmitting}
        rootClassName="profile-reward-modal"
        wrapClassName="profile-reward-modal-wrap"
      >
        <Form form={profileForm} layout="vertical">
          {renderTaskFormItems()}
        </Form>
        <div className="profile-reward-tip">
          {getTaskModalTip()}
        </div>
      </Modal>
    </Layout>
  );
};

export default Dashboard;
