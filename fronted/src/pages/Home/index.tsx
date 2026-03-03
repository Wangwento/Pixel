import React, { useState, useEffect, useRef, useCallback } from 'react';
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
const VIDEO_DURATION = 8000; // 每个视频播放8秒
const TRANSITION_DURATION = 1200; // 过渡时间1.2秒

// 过渡效果类型
type TransitionType = 'crossfade';
const transitions: TransitionType[] = ['crossfade'];

const Home: React.FC = () => {
  const [activeLayer, setActiveLayer] = useState<0 | 1>(0);
  const [videoIndices, setVideoIndices] = useState([0, 1]);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [transitionType, setTransitionType] = useState<TransitionType>('crossfade');
  const [showBlurPoster, setShowBlurPoster] = useState(false);
  const [prompt, setPrompt] = useState('');
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const [registerModalOpen, setRegisterModalOpen] = useState(false);
  const { currentUser, setCurrentUser, logout: authLogout } = useAuth();
  const videoRefs = [useRef<HTMLVideoElement>(null), useRef<HTMLVideoElement>(null)];
  const transitionCountRef = useRef(0);
  const navigate = useNavigate();

  // 获取下一个过渡效果（轮流使用）
  const getNextTransition = useCallback(() => {
    const type = transitions[transitionCountRef.current % transitions.length];
    transitionCountRef.current++;
    return type;
  }, []);

  // 切换到下一个视频
  const switchToNext = useCallback(() => {
    if (isTransitioning) return;

    const nextLayer = activeLayer === 0 ? 1 : 0;
    const nextVideoIndex = (videoIndices[activeLayer] + 1) % videos.length;
    const nextType = getNextTransition();

    // 更新下一层的视频
    const newIndices = [...videoIndices];
    newIndices[nextLayer] = nextVideoIndex;
    setVideoIndices(newIndices);

    // 预加载并准备播放
    const nextRef = videoRefs[nextLayer].current;
    if (nextRef) {
      nextRef.src = videos[nextVideoIndex];
      nextRef.load();

      const handleCanPlay = () => {
        nextRef.removeEventListener('canplaythrough', handleCanPlay);
        nextRef.currentTime = 0;
        nextRef.play().catch(() => {});

        // 开始过渡
        setTransitionType(nextType);
        setIsTransitioning(true);

        // 如果是模糊效果，先显示模糊海报
        if (nextType === 'blur') {
          setShowBlurPoster(true);
          setTimeout(() => {
            setActiveLayer(nextLayer as 0 | 1);
            setTimeout(() => {
              setShowBlurPoster(false);
              setIsTransitioning(false);
            }, TRANSITION_DURATION / 2);
          }, TRANSITION_DURATION / 2);
        } else {
          // crossfade 或 blink
          setTimeout(() => {
            setActiveLayer(nextLayer as 0 | 1);
          }, 50);

          setTimeout(() => {
            setIsTransitioning(false);
          }, TRANSITION_DURATION);
        }
      };

      nextRef.addEventListener('canplaythrough', handleCanPlay);

      // 超时保护
      setTimeout(() => {
        nextRef.removeEventListener('canplaythrough', handleCanPlay);
      }, 3000);
    }
  }, [activeLayer, videoIndices, isTransitioning, getNextTransition]);

  // 初始化
  useEffect(() => {
    const initRef = videoRefs[0].current;
    if (initRef) {
      initRef.src = videos[0];
      initRef.load();
      initRef.play().catch(() => {});
    }
    // 预加载第二个视频
    const preloadRef = videoRefs[1].current;
    if (preloadRef) {
      preloadRef.src = videos[1];
      preloadRef.load();
    }
  }, []);

  // 定时切换
  useEffect(() => {
    const timer = setInterval(switchToNext, VIDEO_DURATION);
    return () => clearInterval(timer);
  }, [switchToNext]);

  const handleIndicatorClick = (targetIndex: number) => {
    const currentVideoIndex = videoIndices[activeLayer];
    if (targetIndex === currentVideoIndex || isTransitioning) return;

    const nextLayer = activeLayer === 0 ? 1 : 0;
    const nextType = getNextTransition();

    const newIndices = [...videoIndices];
    newIndices[nextLayer] = targetIndex;
    setVideoIndices(newIndices);

    const nextRef = videoRefs[nextLayer].current;
    if (nextRef) {
      nextRef.src = videos[targetIndex];
      nextRef.load();

      const handleCanPlay = () => {
        nextRef.removeEventListener('canplaythrough', handleCanPlay);
        nextRef.currentTime = 0;
        nextRef.play().catch(() => {});

        setTransitionType(nextType);
        setIsTransitioning(true);

        if (nextType === 'blur') {
          setShowBlurPoster(true);
          setTimeout(() => {
            setActiveLayer(nextLayer as 0 | 1);
            setTimeout(() => {
              setShowBlurPoster(false);
              setIsTransitioning(false);
            }, TRANSITION_DURATION / 2);
          }, TRANSITION_DURATION / 2);
        } else {
          setTimeout(() => {
            setActiveLayer(nextLayer as 0 | 1);
          }, 50);
          setTimeout(() => {
            setIsTransitioning(false);
          }, TRANSITION_DURATION);
        }
      };

      nextRef.addEventListener('canplaythrough', handleCanPlay);
    }
  };

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

  const currentVideoIndex = videoIndices[activeLayer];

  // 获取视频的CSS类
  const getVideoClass = (layerIndex: number) => {
    const isActive = layerIndex === activeLayer;
    const classes = ['bg-video'];

    if (isActive) {
      classes.push('active');
    }

    if (isTransitioning) {
      classes.push(`transition-${transitionType}`);
      if (isActive) {
        classes.push('entering');
      } else if (layerIndex === (activeLayer === 0 ? 1 : 0)) {
        // 即将变成 active 的层
      }
    }

    return classes.join(' ');
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
                icon={!<UserOutlined />}
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
      <div className={`blur-poster ${showBlurPoster ? 'visible' : ''}`} />

      {/* 双层视频容器 */}
      <div className={`video-container transition-${transitionType}`}>
        <video
          ref={videoRefs[0]}
          muted
          loop
          playsInline
          className={`bg-video ${activeLayer === 0 ? 'active' : ''} ${isTransitioning ? `transition-${transitionType}` : ''}`}
        />
        <video
          ref={videoRefs[1]}
          muted
          loop
          playsInline
          className={`bg-video ${activeLayer === 1 ? 'active' : ''} ${isTransitioning ? `transition-${transitionType}` : ''}`}
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