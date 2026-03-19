import React, { useState, useEffect, useRef } from 'react';
import { Input, Button, message, Dropdown, Avatar, Spin, Empty } from 'antd';
import type { MenuProps } from 'antd';
import {
  UserOutlined,
  HistoryOutlined,
  SettingOutlined,
  LogoutOutlined,
  CrownOutlined,
  FireOutlined,
  HeartOutlined,
  StarOutlined,
  MessageOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons';
import { getHotImages } from '../../api/image';
import type { HotImage } from '../../api/image';
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

  // 热门图片
  const [hotImages, setHotImages] = useState<HotImage[]>([]);
  const [hotLoading, setHotLoading] = useState(false);
  const [hotPage, setHotPage] = useState(0);
  const [hotHasMore, setHotHasMore] = useState(true);
  const hotSentinelRef = useRef<HTMLDivElement>(null);
  const hotFetchingRef = useRef(false);
  const [hotPreview, setHotPreview] = useState<HotImage | null>(null);
  const hotSectionRef = useRef<HTMLDivElement>(null);

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

  // 加载热门图片
  const fetchHotImages = async (pageNum: number) => {
    if (hotFetchingRef.current) return;
    hotFetchingRef.current = true;
    setHotLoading(true);
    try {
      const raw = await getHotImages({ page: pageNum, pageSize: 20 });
      const res = raw as any;
      const list: HotImage[] = res?.data?.list ?? [];
      if (pageNum === 1) {
        setHotImages(list);
      } else {
        setHotImages(prev => [...prev, ...list]);
      }
      setHotHasMore(list.length >= 20);
      setHotPage(pageNum);
    } catch {
      // 静默
    } finally {
      hotFetchingRef.current = false;
      setHotLoading(false);
    }
  };

  useEffect(() => {
    fetchHotImages(1);
  }, []);

  // 热门图片无限滚动
  useEffect(() => {
    const el = hotSentinelRef.current;
    if (!el || !hotHasMore) return;
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && !hotFetchingRef.current && hotPage > 0) {
        fetchHotImages(hotPage + 1);
      }
    }, { rootMargin: '300px' });
    observer.observe(el);
    return () => observer.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hotPage, hotHasMore]);

  const scrollToHot = () => {
    hotSectionRef.current?.scrollIntoView({ behavior: 'smooth' });
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
    <div className="home-wrapper">
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

      {/* 底部提示 - 点击滚动到热门 */}
      <div className="scroll-hint" onClick={scrollToHot} style={{ cursor: 'pointer' }}>
        <span>热门内容</span>
        <div className="scroll-arrow" />
      </div>
      </div>

      {/* ====== 第二屏：热门图片 ====== */}
      <div className="home-hot-section" ref={hotSectionRef}>
        <div className="home-hot-header">
          <FireOutlined className="home-hot-icon" />
          <h2>热门作品</h2>
          <p>精选社区优秀 AI 生成作品</p>
        </div>

        {hotImages.length === 0 && hotLoading && (
          <div className="home-hot-loading"><Spin size="large" /></div>
        )}

        {hotImages.length === 0 && !hotLoading && (
          <div className="home-hot-empty">
            <Empty description="暂无热门作品" />
          </div>
        )}

        {hotImages.length > 0 && (
          <div className="home-hot-masonry">
            {hotImages.map((item) => {
              const isVideo = item.mediaType === 'video';
              return (
                <div key={item.id} className="home-hot-card" onClick={() => setHotPreview(item)}>
                  <div className="home-hot-card-media">
                    {isVideo ? (
                      <>
                        <img
                          src={item.coverUrl || item.imageUrl}
                          alt={item.title || '热门作品'}
                          loading="lazy" decoding="async"
                        />
                        <div className="home-hot-card-play"><PlayCircleOutlined /></div>
                      </>
                    ) : (
                      <img
                        src={item.imageUrl}
                        alt={item.title || '热门作品'}
                        loading="lazy" decoding="async"
                      />
                    )}
                  </div>
                  <div className="home-hot-card-info">
                    {item.title && <p className="home-hot-card-title">{item.title}</p>}
                    <div className="home-hot-card-bottom">
                      <div className="home-hot-card-meta">
                        <UserOutlined />
                        <span>{item.nickname || `用户 ${item.userId}`}</span>
                      </div>
                      <div className="home-hot-card-actions" onClick={(e) => e.stopPropagation()}>
                        <span className="home-hot-action" title="点赞">
                          <HeartOutlined />{item.likeCount || 0}
                        </span>
                        <span className="home-hot-action" title="收藏">
                          <StarOutlined />{item.collectCount || 0}
                        </span>
                        <span className="home-hot-action" title="评论">
                          <MessageOutlined />{item.commentCount || 0}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {hotLoading && hotImages.length > 0 && (
          <div className="home-hot-load-more"><Spin /><span>加载更多...</span></div>
        )}

        {!hotHasMore && hotImages.length > 0 && (
          <div className="home-hot-end">已经到底啦</div>
        )}

        {hotHasMore && !hotLoading && <div ref={hotSentinelRef} style={{ height: 1 }} />}
      </div>

      {/* 热门大图预览 */}
      {hotPreview && (
        <div className="home-hot-preview-overlay" onClick={() => setHotPreview(null)}>
          <div className="home-hot-preview-body" onClick={(e) => e.stopPropagation()}>
            {hotPreview.mediaType === 'video' ? (
              <video src={hotPreview.imageUrl} controls autoPlay className="home-hot-preview-video" />
            ) : (
              <img src={hotPreview.imageUrl} alt={hotPreview.title || '预览'} />
            )}
            {hotPreview.title && <h3>{hotPreview.title}</h3>}
            {hotPreview.description && <p className="home-hot-preview-desc">{hotPreview.description}</p>}
            <button className="home-hot-preview-close" onClick={() => setHotPreview(null)}>✕</button>
          </div>
        </div>
      )}

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
