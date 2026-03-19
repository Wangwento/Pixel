import api from './index';

export type GrowthTaskStatus = 'ACTION_REQUIRED' | 'CLAIMABLE' | 'CLAIMED' | 'REJECTED';
export type GrowthTaskAction =
  | 'COMPLETE_PROFILE'
  | 'BIND_EMAIL'
  | 'BIND_PHONE'
  | 'VERIFY_REAL_NAME'
  | 'CLAIM'
  | 'CLAIM_HOT'
  | 'NONE';

export type GrowthTask = {
  recordId?: number;
  activityCode: string;
  title: string;
  description: string;
  triggerType: string;
  status: GrowthTaskStatus;
  actionType: GrowthTaskAction;
  rewardSummary?: string;
  createdAt?: string;
  claimedAt?: string;
};

export const getGrowthTasks = () => {
  return api.get('/user/growth/tasks');
};

export const claimGrowthReward = (recordId: number) => {
  return api.post(`/user/growth/claim/${recordId}`);
};

export const completeProfileTask = (params: { nickname: string; avatar: string }) => {
  return api.post('/user/profile/complete', params);
};

export const bindEmailTask = (params: { email: string; code: string }) => {
  return api.post('/user/profile/bind-email', params);
};

export const bindPhoneTask = (params: { phone: string; code: string }) => {
  return api.post('/user/profile/bind-phone', params);
};

export const verifyRealNameTask = (params: { realName: string; idCard: string }) => {
  return api.post('/user/profile/verify-real-name', params);
};
