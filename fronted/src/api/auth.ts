import api from './index';

export type RegisterParams = {
  username: string;
  password: string;
  email?: string;
};

export type LoginParams = {
  username: string;
  password: string;
};

export type UserInfo = {
  id: number;
  username: string;
  nickname?: string;
  avatar?: string;
  email?: string;
  points: number;
  freeQuota: number;
  dailyLimit: number;
  dailyUsed: number;
  dailyRemaining: number;
  monthlyQuota: number;
  monthlyQuotaUsed: number;
  monthlyQuotaRemaining: number;
  userType: number;
  vipLevel: number;
  vipExpireTime?: string;
  level: number;
  isVip: boolean;
  inviteCode?: string;
  profileCompleted: boolean | number;
};

export type AuthResponse = {
  token: string;
  user: UserInfo;
};

// 用户注册
export const register = (params: RegisterParams) => {
  return api.post<AuthResponse>('/auth/register', params);
};

// 用户登录
export const login = (params: LoginParams) => {
  return api.post<AuthResponse>('/auth/login', params);
};

// 获取当前用户信息
export const getCurrentUser = () => {
  return api.get<UserInfo>('/auth/me');
};

// 发送验证码
export const sendVerifyCode = (phone: string) => {
  return api.post('/auth/send-code', { phone });
};

// 手机验证码登录
export const loginByPhone = (phone: string, code: string) => {
  return api.post<AuthResponse>('/auth/login-phone', { phone, code });
};
