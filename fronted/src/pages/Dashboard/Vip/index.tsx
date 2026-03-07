import React, { useState } from 'react';
import {
  CrownOutlined,
  ThunderboltOutlined,
  PictureOutlined,
  StarOutlined,
  SafetyCertificateOutlined,
  CustomerServiceOutlined,
  RocketOutlined,
  GiftOutlined,
  DownloadOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../../contexts/AuthContext';
import './index.css';

const Vip: React.FC = () => {
  const [billingCycle, setBillingCycle] = useState<'monthly' | 'yearly'>('monthly');
  const { currentUser } = useAuth();
  const navigate = useNavigate();

  const plans = [
    {
      key: 'free',
      name: '免费用户',
      price: 0,
      yearlyPrice: 0,
      subtitle: '体验基础AI生成能力',
      isCurrent: !currentUser?.isVip,
      features: [
        { icon: <PictureOutlined />, text: '每日3次生成额度' },
        { icon: <ThunderboltOutlined />, text: '标准分辨率输出' },
        { icon: <StarOutlined />, text: '基础风格模板' },
        { icon: <GiftOutlined />, text: '社区作品浏览' },
      ],
    },
    {
      key: 'monthly',
      name: '月度会员',
      price: 19.9,
      yearlyPrice: 15.9,
      subtitle: '解锁全部创作能力',
      recommended: true,
      isCurrent: currentUser?.isVip && currentUser?.vipLevel === 1,
      features: [
        { icon: <PictureOutlined />, text: '每日50次生成额度' },
        { icon: <RocketOutlined />, text: '高清无水印输出' },
        { icon: <StarOutlined />, text: '全部风格模板' },
        { icon: <ThunderboltOutlined />, text: '优先生成队列' },
        { icon: <DownloadOutlined />, text: '批量下载与管理' },
        { icon: <CustomerServiceOutlined />, text: '专属客服支持' },
      ],
    },
    {
      key: 'yearly',
      name: '年度会员',
      price: 199,
      yearlyPrice: 199,
      subtitle: '尊享全部权益与专属服务',
      isCurrent: currentUser?.isVip && currentUser?.vipLevel === 2,
      features: [
        { icon: <PictureOutlined />, text: '无限生成额度' },
        { icon: <RocketOutlined />, text: '4K超高清输出' },
        { icon: <StarOutlined />, text: '全部风格 + 抢先体验' },
        { icon: <ThunderboltOutlined />, text: '最高优先级队列' },
        { icon: <DownloadOutlined />, text: '批量下载与管理' },
        { icon: <SafetyCertificateOutlined />, text: '数据隐私保护' },
        { icon: <CustomerServiceOutlined />, text: '1对1专属客服' },
        { icon: <CrownOutlined />, text: '专属身份标识' },
      ],
    },
  ];

  return (
    <div className="vip-page">
      <div className="vip-back" onClick={() => navigate('/dashboard')}>
        <ArrowLeftOutlined />
        <span>返回工作台</span>
      </div>
      <div className="vip-header">
        <h1 className="vip-title">
          <CrownOutlined className="vip-title-icon" />
          会员充值
        </h1>
        <p className="vip-subtitle">选择适合你的方案，解锁更强大的AI创作能力</p>
        <div className="billing-toggle">
          <button
            className={`toggle-btn ${billingCycle === 'monthly' ? 'active' : ''}`}
            onClick={() => setBillingCycle('monthly')}
          >
            按月付费
          </button>
          <button
            className={`toggle-btn ${billingCycle === 'yearly' ? 'active' : ''}`}
            onClick={() => setBillingCycle('yearly')}
          >
            按年付费
            <span className="save-badge">省20%</span>
          </button>
        </div>
      </div>

      <div className="vip-plans">
        {plans.map((plan) => (
          <PlanCard key={plan.key} plan={plan} billingCycle={billingCycle} />
        ))}
      </div>
    </div>
  );
};

interface PlanInfo {
  key: string;
  name: string;
  price: number;
  yearlyPrice: number;
  subtitle: string;
  recommended?: boolean;
  isCurrent?: boolean;
  features: { icon: React.ReactNode; text: string }[];
}

const PlanCard: React.FC<{ plan: PlanInfo; billingCycle: 'monthly' | 'yearly' }> = ({
  plan,
  billingCycle,
}) => {
  const displayPrice = billingCycle === 'yearly' ? plan.yearlyPrice : plan.price;
  const isFree = plan.key === 'free';

  return (
    <div className={`plan-card ${plan.recommended ? 'recommended' : ''} ${plan.isCurrent ? 'current' : ''}`}>
      {plan.recommended && <div className="recommended-badge">推荐</div>}
      <div className="plan-name">{plan.name}</div>
      <div className="plan-price-section">
        {isFree ? (
          <span className="plan-price">免费</span>
        ) : (
          <>
            <span className="plan-currency">¥</span>
            <span className="plan-price">{displayPrice}</span>
            <span className="plan-period">
              / {plan.key === 'yearly' ? '年' : '月'}
            </span>
          </>
        )}
      </div>
      <p className="plan-subtitle">{plan.subtitle}</p>
      <button
        className={`plan-btn ${plan.isCurrent ? 'current-btn' : ''} ${plan.recommended ? 'recommended-btn' : ''}`}
        disabled={plan.isCurrent}
      >
        {plan.isCurrent ? '当前方案' : isFree ? '当前方案' : '立即开通'}
      </button>
      <div className="plan-features">
        {plan.features.map((feature, index) => (
          <div className="feature-item" key={index}>
            <span className="feature-icon">{feature.icon}</span>
            <span className="feature-text">{feature.text}</span>
          </div>
        ))}
      </div>
    </div>
  );
};

export default Vip;
