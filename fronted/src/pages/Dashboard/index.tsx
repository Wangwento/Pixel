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
import { useAuth } from '../../contexts/AuthContext';
import { claimGrowthReward, completeProfileTask, getGrowthTasks } from '../../api/user';
import type { GrowthTask } from '../../api/user';
import logoSvg from '../../assets/ip.svg';
import './index.css';

const { Sider, Content } = Layout;

type GrowthTaskListPayload = {
  pendingCount?: number;
  tasks?: GrowthTask[];
};

const Dashboard: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const [userPopoverOpen, setUserPopoverOpen] = useState(false);
  const [taskDrawerOpen, setTaskDrawerOpen] = useState(false);
  const [profileModalOpen, setProfileModalOpen] = useState(false);
  const [taskLoading, setTaskLoading] = useState(false);
  const [profileSubmitting, setProfileSubmitting] = useState(false);
  const [pendingCount, setPendingCount] = useState(0);
  const [growthTasks, setGrowthTasks] = useState<GrowthTask[]>([]);
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);
  const [theme, setTheme] = useState<'dark' | 'light'>(() => {
    return (localStorage.getItem('dashboard-theme') as 'dark' | 'light') || 'dark';
  });
  const [profileForm] = Form.useForm<{ nickname: string; avatar: string }>();
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

  const loadGrowthTasks = useCallback(async (silent: boolean = true) => {
    if (!localStorage.getItem('token')) {
      setPendingCount(0);
      setGrowthTasks([]);
      return;
    }

    setTaskLoading(true);
    try {
      const res = await getGrowthTasks() as unknown as {
        code: number;
        message?: string;
        data?: GrowthTaskListPayload;
      };

      if (res.code === 200 && res.data) {
        setPendingCount(res.data.pendingCount || 0);
        setGrowthTasks(res.data.tasks || []);
      } else if (!silent) {
        message.error(res.message || '获取通知失败');
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
    setTaskDrawerOpen(true);
    loadGrowthTasks(false);
  };

  const openProfileRewardModal = () => {
    profileForm.setFieldsValue({
      nickname: currentUser?.nickname || currentUser?.username || '',
      avatar: currentUser?.avatar || '',
    });
    setProfileModalOpen(true);
  };

  const handleTaskAction = async (task: GrowthTask) => {
    if (task.actionType === 'COMPLETE_PROFILE') {
      openProfileRewardModal();
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
      const res = await completeProfileTask(values) as unknown as {
        code: number;
        message?: string;
        data?: {
          message?: string;
        };
      };

      if (res.code !== 200) {
        message.error(res.message || '保存资料失败');
        return;
      }

      message.success(res.data?.message || res.message || '资料已完善，请在通知中领取奖励');
      setProfileModalOpen(false);
      await Promise.all([loadGrowthTasks(), refreshUser()]);
    } catch (error: unknown) {
      const formError = error as { errorFields?: Array<unknown>; response?: { data?: { message?: string } } };
      if (formError.errorFields) {
        return;
      }
      message.error(formError.response?.data?.message || '保存资料失败，请稍后重试');
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
    return '已领取';
  };

  const getTaskTimeText = (task: GrowthTask) => {
    const time = task.claimedAt || task.createdAt;
    if (!time) {
      return '';
    }
    return dayjs(time).format('YYYY-MM-DD HH:mm');
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
            <Badge dot={pendingCount > 0} offset={[-2, 4]}>
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
            <span className="notification-summary-label">待处理任务</span>
            <span className="notification-summary-value">{pendingCount}</span>
          </div>
          <div className="notification-summary-tip">
            注册礼包、完善资料等奖励会先进入这里，需要你手动领取。
          </div>
        </div>

        {taskLoading ? (
          <div className="notification-loading">
            <Spin size="large" />
          </div>
        ) : growthTasks.length > 0 ? (
          <div className="notification-task-list">
            {growthTasks.map((task) => (
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
                        : '先完善资料，完成后会变成可领取状态。'}
                  </span>
                  <Button
                    type={task.status === 'CLAIMABLE' ? 'primary' : 'default'}
                    ghost={task.status !== 'CLAIMABLE'}
                    disabled={task.status === 'CLAIMED'}
                    loading={task.recordId ? actionLoadingId === task.recordId : false}
                    onClick={() => handleTaskAction(task)}
                  >
                    {task.status === 'CLAIMED'
                      ? '已领取'
                      : task.actionType === 'COMPLETE_PROFILE'
                        ? '去完善'
                        : '领取奖励'}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="notification-empty">
            <Empty description="暂无待处理通知" />
          </div>
        )}
      </Drawer>

      <Modal
        title="完善个人信息"
        open={profileModalOpen}
        onCancel={() => setProfileModalOpen(false)}
        onOk={handleProfileSubmit}
        okText="保存并生成奖励"
        cancelText="取消"
        confirmLoading={profileSubmitting}
        rootClassName="profile-reward-modal"
        wrapClassName="profile-reward-modal-wrap"
      >
        <Form form={profileForm} layout="vertical">
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
        </Form>
        <div className="profile-reward-tip">
          保存后会在通知中心生成“完善资料奖励”，需要你再手动领取，链路更清晰也更方便扩展活动。
        </div>
      </Modal>
    </Layout>
  );
};

export default Dashboard;
