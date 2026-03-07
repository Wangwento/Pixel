import api from './index';

export type GrowthTaskStatus = 'ACTION_REQUIRED' | 'CLAIMABLE' | 'CLAIMED';
export type GrowthTaskAction = 'COMPLETE_PROFILE' | 'CLAIM' | 'NONE';

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
