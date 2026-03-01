import React from 'react';
import { Layout, Menu, Button, Space, Avatar, Dropdown } from 'antd';
import { UserOutlined, PictureOutlined, HistoryOutlined, LogoutOutlined } from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import type { MenuProps } from 'antd';
import './index.css';

const { Header, Content, Footer } = Layout;

const AppLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const token = localStorage.getItem('token');

  const menuItems: MenuProps['items'] = [
    {
      key: '/generate',
      icon: <PictureOutlined />,
      label: 'AI头像生成',
    },
    {
      key: '/history',
      icon: <HistoryOutlined />,
      label: '生成历史',
    },
  ];

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人中心',
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: () => {
        localStorage.removeItem('token');
        navigate('/login');
      },
    },
  ];

  const handleMenuClick: MenuProps['onClick'] = (e) => {
    navigate(e.key);
  };

  return (
    <Layout className="app-layout">
      <Header className="app-header">
        <div className="logo" onClick={() => navigate('/')}>
          <PictureOutlined style={{ fontSize: 24, marginRight: 8 }} />
          <span>Pixel AI</span>
        </div>
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={handleMenuClick}
          className="nav-menu"
        />
        <div className="header-right">
          {token ? (
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <Avatar icon={<UserOutlined />} style={{ cursor: 'pointer' }} />
            </Dropdown>
          ) : (
            <Space>
              <Button type="link" onClick={() => navigate('/login')} style={{ color: '#fff' }}>
                登录
              </Button>
              <Button type="primary" onClick={() => navigate('/register')}>
                注册
              </Button>
            </Space>
          )}
        </div>
      </Header>
      <Content className="app-content">
        <Outlet />
      </Content>
      <Footer className="app-footer">
        Pixel AI ©{new Date().getFullYear()} - AI头像生成平台
      </Footer>
    </Layout>
  );
};

export default AppLayout;