import React, { useState } from 'react';
import { Layout, Menu, Avatar, Popover, Button } from 'antd';
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
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import type { MenuProps } from 'antd';
import { useAuth } from '../../contexts/AuthContext';
import logoSvg from '../../assets/ip.svg';
import './index.css';

const { Sider, Content } = Layout;

const Dashboard: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const [userPopoverOpen, setUserPopoverOpen] = useState(false);
  const { currentUser, logout: authLogout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

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
        <div className="balance-main">
          <span className="balance-label">总额度</span>
          <span className="balance-value">
            {(() => {
              const freeQuota = currentUser?.freeQuota || 0;
              const monthlyQuota = currentUser?.monthlyQuota || 0;
              const monthlyQuotaUsed = currentUser?.monthlyQuotaUsed || 0;
              const total = freeQuota + monthlyQuota - monthlyQuotaUsed;
              console.log('[Dashboard] 额度计算:', { freeQuota, monthlyQuota, monthlyQuotaUsed, total });
              return total;
            })()}
            <span className="free-quota-hint"> ({currentUser?.freeQuota || 0})</span>
          </span>
        </div>
        <div className="balance-details">
          <div className="balance-item">
            <span>今日剩余</span>
            <span>{currentUser?.dailyRemaining || 0}</span>
          </div>
          <div className="balance-item">
            <span>今日已生成</span>
            <span>{currentUser?.dailyUsed || 0}/{currentUser?.dailyLimit || 10}</span>
          </div>
          <div className="balance-item">
            <span>积分</span>
            <span>{currentUser?.points || 0}</span>
          </div>
        </div>
        <Button type="primary" className="recharge-btn" onClick={() => navigate('/dashboard/recharge')}>
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
          <span>会员中心</span>
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
    <Layout className="dashboard-layout">
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={220}
        collapsedWidth={72}
        className="dashboard-sider"
      >
        {/* Logo */}
        <div className="sider-logo" onClick={() => navigate('/')}>
          <img src={logoSvg} alt="Pixel" className="logo-img" />
          {!collapsed && <span className="logo-text">Pixel</span>}
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
    </Layout>
  );
};

export default Dashboard;
