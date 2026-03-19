import api from './index';

export type RegisterParams = {
  username: string;
  password: string;
  email: string;
  emailCode: string;
  inviteCode?: string;
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
  emailVerified?: boolean | number;
  phone?: string;
  phoneVerified?: boolean | number;
  realName?: string;
  realNameVerified?: number;
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

export type VerifyCodeScene = 'register' | 'bind_email' | 'bind_phone' | 'login';

type ApiResult<T = unknown> = {
  code: number;
  message?: string;
  data?: T;
};

const ensureSuccess = async <T>(request: Promise<ApiResult<T>>) => {
  const response = await request;
  if (response.code !== 200) {
    return Promise.reject({
      response: {
        data: response,
      },
    });
  }
  return response;
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

export const checkEmailAvailable = async (email: string) => {
  const response = await api.get<{ available: boolean }>('/auth/email/check', {
    params: { email },
  }) as unknown as ApiResult<{ available: boolean }>;
  return response;
};

export const checkUsernameAvailable = async (username: string) => {
  const response = await api.get<{ available: boolean }>('/auth/username/check', {
    params: { username },
  }) as unknown as ApiResult<{ available: boolean }>;
  return response;
};

// 发送验证码
export const sendVerifyCode = (phone: string) => {
  return ensureSuccess(api.post('/auth/send-code', { phone }) as Promise<ApiResult>);
};

export const sendEmailVerifyCode = (
  email: string,
  scene: Extract<VerifyCodeScene, 'register' | 'bind_email'> = 'register',
) => {
  return ensureSuccess(api.post('/auth/email/send-code', { email, scene }) as Promise<ApiResult>);
};

export const sendPhoneVerifyCode = (
  phone: string,
  scene: Extract<VerifyCodeScene, 'bind_phone' | 'login'> = 'login',
) => {
  return ensureSuccess(api.post('/auth/phone/send-code', { phone, scene }) as Promise<ApiResult>);
};

// 手机验证码登录
export const loginByPhone = (phone: string, code: string) => {
  return api.post<AuthResponse>('/auth/login-phone', { phone, code });
};

export const logout = () => {
  return api.post('/auth/logout');
};
