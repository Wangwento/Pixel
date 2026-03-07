import React, { useState, useEffect, useRef } from 'react';
import { Input, Button, message, Dropdown, Avatar } from 'antd';
import type { MenuProps } from 'antd';
import {
  UserOutlined,
  HistoryOutlined,
  SettingOutlined,
  LogoutOutlined,
  CrownOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import LoginModal from '../../components/LoginModal';
import RegisterModal from '../../components/RegisterModal';
import { useAuth } from '../../contexts/AuthContext';
import './index.css';

import video1 from '../../assets/封面1.mp4';
import video2 from '../../assets/封面2.mp4';
import video3 from '../../assets/封面3.mp4';
import video4 from '../../assets/封面4.mp4';
import video5 from '../../assets/封面5.mp4';
import logoSvg from '../../assets/ip.svg';

const videos = [video1, video2, video3, video4, video5];
const VIDEO_DURATION = 8000;
const TRANSITION_DURATION = 1200;

const Home: React.FC = () => {
  const [activeLayer, setActiveLayer] = useState<0 | 1>(0);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [prompt, setPrompt] = useState('');
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const [registerModalOpen, setRegisterModalOpen] = useState(false);
  const { currentUser, setCurrentUser, logout: authLogout } = useAuth();
  const videoRefs = [useRef<HTMLVideoElement>(null), useRef<HTMLVideoElement>(null)];
  const navigate = useNavigate();

  // 用 ref 存储可变状态，避免 useCallback/useEffect 依赖问题
  const stateRef = useRef({
    activeLayer: 0 as 0 | 1,
    videoIndices: [0, 1],
    isTransitioning: false,
  });
  const timersRef = useRef<number[]>([]);

  const clearAllTimers = () => {
    timersRef.current.forEach(clearTimeout);
    timersRef.current = [];
  };

  const addTimer = (fn: () => void, delay: number) => {
    const id = window.setTimeout(fn, delay);
    timersRef.current.push(id);
    return id;
  };

  // 执行切换（不依赖 React state，用 ref 读取最新值）
  const doSwitch = (targetVideoIndex: number) => {
    const state = stateRef.current;
    if (state.isTransitioning) return;

    const nextLayer = state.activeLayer === 0 ? 1 : 0;
    state.videoIndices[nextLayer] = targetVideoIndex;
    state.isTransitioning = true;
    setIsTransitioning(true);

    const nextRef = videoRefs[nextLayer].current;
    if (!nextRef) return;

    // 只在视频源不同时才重新加载
    const targetSrc = videos[targetVideoIndex];
    if (nextRef.src !== targetSrc) {
      nextRef.src = targetSrc;
      nextRef.load();
    }

    const startTransition = () => {
      nextRef.currentTime = 0;
      nextRef.play().catch(() => {});

      addTimer(() => {
        state.activeLayer = nextLayer as 0 | 1;
        setActiveLayer(nextLayer as 0 | 1);
      }, 50);

      addTimer(() => {
        state.isTransitioning = false;
        setIsTransitioning(false);
      }, TRANSITION_DURATION);
    };

    // 如果视频已经可以播放，直接开始
    if (nextRef.readyState >= 3) {
      startTransition();
      return;
    }

    let handled = false;
    const handleCanPlay = () => {
      if (handled) return;
      handled = true;
      nextRef.removeEventListener('canplaythrough', handleCanPlay);
      startTransition();
    };

    nextRef.addEventListener('canplaythrough', handleCanPlay);

    // 超时兜底：3秒后强制切换
    addTimer(() => {
      if (handled) return;
      handled = true;
      nextRef.removeEventListener('canplaythrough', handleCanPlay);
      startTransition();
    }, 3000);
  };

  // 初始化：只加载一次
  useEffect(() => {
    const initRef = videoRefs[0].current;
    if (initRef) {
      initRef.src = videos[0];
      initRef.load();
      initRef.play().catch(() => {});
    }
    const preloadRef = videoRefs[1].current;
    if (preloadRef) {
      preloadRef.src = videos[1];
      preloadRef.load();
    }
    return () => clearAllTimers();
  }, []);

  // 定时切换：用 ref 读状态，不依赖 state
  useEffect(() => {
    const timer = setInterval(() => {
      const state = stateRef.current;
      if (state.isTransitioning) return;
      const nextIdx = (state.videoIndices[state.activeLayer] + 1) % videos.length;
      doSwitch(nextIdx);
    }, VIDEO_DURATION);
    return () => clearInterval(timer);
  }, []);

  const handleStart = () => {
    const token = localStorage.getItem('token');
    if (!token) {
      setLoginModalOpen(true);
      return;
    }
    if (prompt.trim()) {
      navigate(`/dashboard?prompt=${encodeURIComponent(prompt)}`);
    } else {
      navigate('/dashboard');
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleStart();
    }
  };

  // 退出登录
  const handleLogout = () => {
    authLogout();
    message.success('已退出登录');
  };

  // 用户下拉菜单
  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人中心',
      onClick: () => navigate('/profile'),
    },
    {
      key: 'history',
      icon: <HistoryOutlined />,
      label: '生成历史',
      onClick: () => navigate('/history'),
    },
    {
      key: 'vip',
      icon: <CrownOutlined />,
      label: '会员中心',
      onClick: () => navigate('/vip'),
    },
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: '设置',
      onClick: () => navigate('/settings'),
    },
    {
      type: 'divider',
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ];

  return (
    <div className="home-fullscreen">
      {/* 左上角 Logo */}
      <div className="home-header">
        <img src={logoSvg} alt="Pixel" className="header-logo" />
        <span className="header-title">像素</span>
      </div>

      {/* 右上角登录/注册 或 用户信息 */}
      <div className="home-auth">
        {currentUser ? (
          <Dropdown
            menu={{ items: userMenuItems }}
            placement="bottomRight"
            trigger={['click']}
            overlayClassName="user-dropdown"
          >
            <div className="user-info">
              <Avatar
                src={currentUser.avatar}
                icon={!currentUser.avatar ? <UserOutlined /> : undefined}
                size={36}
                className="user-avatar"
              />
              <span className="user-nickname">
                {currentUser.nickname || currentUser.username}
              </span>
              {currentUser.isVip && (
                <CrownOutlined className="vip-badge" />
              )}
            </div>
          </Dropdown>
        ) : (
          <Button
            type="text"
            className="auth-button"
            onClick={() => setLoginModalOpen(true)}
          >
            登录 / 注册
          </Button>
        )}
      </div>

      {/* 模糊海报过渡层 */}
      <div className="blur-poster" />

      {/* 双层视频容器 */}
      <div className="video-container transition-crossfade">
        <video
          ref={videoRefs[0]}
          muted
          playsInline
          className={`bg-video ${activeLayer === 0 ? 'active' : ''} ${isTransitioning ? 'transition-crossfade' : ''}`}
        />
        <video
          ref={videoRefs[1]}
          muted
          playsInline
          className={`bg-video ${activeLayer === 1 ? 'active' : ''} ${isTransitioning ? 'transition-crossfade' : ''}`}
        />
        <div className="video-overlay" />
      </div>

      {/* 主要内容 */}
      <div className="home-content">
        <div className="hero-text">
          <p className="slogan">从灵光一现，到惊艳世界</p>
        </div>

        <div className="input-section">
          <div className="input-wrapper">
            <Input
              placeholder="描述你想要的头像风格，例如：赛博朋克风格的酷炫少年..."
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              onKeyPress={handleKeyPress}
              className="prompt-input"
              size="large"
            />
            <Button
              type="primary"
              onClick={handleStart}
              className="start-button"
              size="large"
            >
              开始创作
            </Button>
          </div>
        </div>
      </div>

      {/* 底部提示 */}
      <div className="scroll-hint">
        <span>热门头像</span>
        <div className="scroll-arrow" />
      </div>

      {/* 登录弹窗 */}
      <LoginModal
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        onLoginSuccess={(user) => {
          setCurrentUser(user);
          setLoginModalOpen(false);
          message.success('登录成功');
        }}
        onRegister={() => {
          setLoginModalOpen(false);
          setRegisterModalOpen(true);
        }}
        onForgotPassword={() => {
          setLoginModalOpen(false);
          navigate('/forgot-password');
        }}
      />

      {/* 注册弹窗 */}
      <RegisterModal
        open={registerModalOpen}
        onClose={() => setRegisterModalOpen(false)}
        onRegisterSuccess={(user) => {
          setCurrentUser(user);
          setRegisterModalOpen(false);
          message.success('注册成功，欢迎加入 Pixel！');
        }}
        onBackToLogin={() => {
          setRegisterModalOpen(false);
          setLoginModalOpen(true);
        }}
      />
    </div>
  );
};

export default Home;
