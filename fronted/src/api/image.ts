import api from './index';

// 文生图请求参数（与后端Text2ImgRequest对应）
export type Text2ImgRequest = {
  prompt: string;
  style?: string;
  modelId?: string;
  aspectRatio?: '1:1' | '2:3' | '3:2' | '3:4' | '4:3' | '4:5' | '5:4' | '9:16' | '16:9' | '21:9';
  imageSize?: '1K' | '2K' | '4K';
  params?: Record<string, unknown>;  // 动态模型参数
};

// 图生图请求参数（预留）
export type Img2ImgRequest = {
  prompt: string;
  style?: string;
  modelId?: string;
  responseFormat?: 'url' | 'b64_json';
  aspectRatio?: '1:1' | '2:3' | '3:2' | '3:4' | '4:3' | '4:5' | '5:4' | '9:16' | '16:9' | '21:9';
  imageSize?: '1K' | '2K' | '4K';
  images?: File[];
  imageUrls?: string[];
  params?: Record<string, unknown>;  // 动态模型参数
};

// 通用生成请求参数（兼容旧代码）
export type GenerationRequest = {
  prompt: string;
  negativePrompt?: string;
  style?: string;
  sourceImageUrl?: string;
  sourceImageBase64?: string;
  size?: string;
  quality?: string;
  vendor?: string;
};

// 生成结果
export type GenerationResult = {
  taskId?: string;
  taskStatus?: string;
  imageUrl?: string;
  imageBase64?: string;
  imageUrls?: string[];
  imageBase64List?: string[];
  ossUrl?: string;
  ossUrls?: string[];
  revisedPrompt?: string;
  vendor?: string;
  model?: string;
  generationTimeMs?: number;
  fromCache?: boolean;
};

// 图片生成响应（新增，对应后端GenerationResponse）
export type GenerationResponse = {
  taskId?: string;
  taskStatus: 'RUNNING' | 'SUCCESS' | 'FAILED';
  message?: string;
  result?: GenerationResult;
};

// 文生图
export const text2img = (params: Text2ImgRequest) => {
  return api.post<GenerationResponse>('/avatar/text2img', params);
};

// 图生图
export const img2img = (params: Img2ImgRequest) => {
  const formData = new FormData();
  formData.append('prompt', params.prompt);
  if (params.style) {
    formData.append('style', params.style);
  }
  if (params.modelId) {
    formData.append('modelId', params.modelId);
  }
  if (params.responseFormat) {
    formData.append('responseFormat', params.responseFormat);
  }
  if (params.aspectRatio) {
    formData.append('aspectRatio', params.aspectRatio);
  }
  if (params.imageSize) {
    formData.append('imageSize', params.imageSize);
  }
  if (params.params) {
    formData.append('params', JSON.stringify(params.params));
  }
  params.images?.forEach((image) => {
    formData.append('image', image);
  });
  params.imageUrls?.forEach((imageUrl) => {
    formData.append('imageUrl', imageUrl);
  });

  return api.post<GenerationResponse>('/avatar/img2img', formData);
};

// 通用生成（兼容旧代码）
export const generate = (params: GenerationRequest) => {
  return api.post<GenerationResult>('/avatar/generate', params);
};

// 获取生成历史
export const getImageHistory = (
  page: number = 1,
  pageSize: number = 10,
  filters?: {
    startDate?: string;
    endDate?: string;
  }
) => {
  return api.get('/image/history', {
    params: {
      page,
      pageSize,
      ...filters,
    },
  });
};

// 模型参数定义
export type ModelParam = {
  paramKey: string;
  paramName: string;
  paramType: 'string' | 'number' | 'boolean' | 'select' | 'multiSelect' | 'array';
  required: boolean;
  visible: boolean;
  defaultValue?: string;
  options?: string;  // JSON 格式的选项数组
  validationRule?: string;
  description?: string;
  displayOrder: number;
};

// 模型信息
export type ModelInfo = {
  modelId: string;
  displayName: string;
  minVipLevel: number;
  available: boolean;
  supportsImageInput: boolean;  // 是否支持图片输入（图生图）
  params?: ModelParam[];  // 模型参数定义
};

export type AssetFolder = {
  id: number;
  userId: number;
  parentId: number;
  folderName: string;
  sortOrder: number;
  createdAt?: string;
  updatedAt?: string;
};

export type ImageAsset = {
  id: number;
  userId: number;
  generationRecordId?: number;
  folderId: number;
  title: string;
  imageUrl: string;
  prompt?: string;
  style?: string;
  sourceType?: string;
  createdAt: string;
  updatedAt?: string;
};

export const getAssetFolders = () => {
  return api.get<AssetFolder[]>('/image/folders');
};

export const createAssetFolder = (params: { folderName: string; parentId?: number }) => {
  return api.post<AssetFolder>('/image/folders', params);
};

export const renameAssetFolder = (folderId: number, params: { folderName: string }) => {
  return api.put(`/image/folders/${folderId}`, params);
};

export const deleteAssetFolder = (folderId: number) => {
  return api.delete(`/image/folders/${folderId}`);
};

export const getAssets = (params: {
  page?: number;
  pageSize?: number;
  folderId?: number;
  keyword?: string;
  startDate?: string;
  endDate?: string;
}) => {
  return api.get('/image/assets', { params });
};

export const updateAssetTitle = (assetId: number, params: { title: string }) => {
  return api.put<ImageAsset>(`/image/assets/${assetId}/title`, params);
};

export const moveAssets = (params: { assetIds: number[]; folderId?: number }) => {
  return api.post('/image/assets/move', params);
};

export const deleteAssets = (params: { assetIds: number[] }) => {
  return api.post('/image/assets/delete', params);
};

// 获取可用模型列表
export const getModels = () => {
  return api.get<ModelInfo[]>('/avatar/models');
};

// ==================== 视频生成 ====================

// 文生视频请求参数
export type Text2VideoRequest = {
  prompt: string;
  model: 'sora-2' | 'sora-2-pro';
  aspect_ratio?: '16:9' | '9:16';
  hd?: boolean;
  duration?: '10' | '15' | '25';
  watermark?: boolean;
  private?: boolean;
};

// 图生视频请求参数
export type Img2VideoRequest = {
  prompt: string;
  model: 'sora-2' | 'sora-2-pro';
  images: string[];
  aspect_ratio?: '16:9' | '9:16';
  hd?: boolean;
  duration?: '4' | '8' | '10' | '12' | '15' | '25';
  watermark?: boolean;
  private?: boolean;
};

// 视频生成响应
export type VideoGenerationResponse = {
  taskId?: string;
  taskStatus: 'RUNNING' | 'SUCCESS' | 'FAILED';
  message?: string;
  videoUrl?: string;
  coverUrl?: string;
};

// 文生视频
export const text2video = (params: Text2VideoRequest) => {
  return api.post<VideoGenerationResponse>('/video/text2video', params);
};

// 图生视频
export const img2video = (params: Img2VideoRequest) => {
  return api.post<VideoGenerationResponse>('/video/img2video', params);
};

// ==================== 热门图片 ====================

export type HotImage = {
  id: number;
  userId: number;
  nickname?: string;
  imageAssetId: number;
  imageUrl: string;
  mediaType?: 'image' | 'video';  // 默认 image
  coverUrl?: string;               // 视频封面
  title?: string;
  description?: string;
  status: number;
  rejectReason?: string;
  rewardClaimed: number;
  rewardPoints: number;
  likeCount?: number;
  collectCount?: number;
  commentCount?: number;
  reviewedAt?: string;
  claimedAt?: string;
  createdAt: string;
};

export type HotImageNotification = {
  hotImageId: number;
  title: string;
  description: string;
  imageUrl?: string;
  status: 'CLAIMABLE' | 'REJECTED';
  actionType: 'CLAIM_HOT' | 'NONE';
  rewardSummary?: string;
  createdAt?: string;
};

// 提交到热门
export const submitToHot = (data: { imageAssetId: number; description?: string }) => {
  return api.post('/image/hot/submit', data);
};

// 我的热门提交
export const getMyHotSubmissions = (params: { page: number; pageSize: number }) => {
  return api.get('/image/hot/my', { params });
};

// 热门图片列表（公共）
export const getHotImages = (params: { page: number; pageSize: number }) => {
  return api.get('/image/hot/list', { params });
};

// 热门通知数据
export const getHotImageNotifications = () => {
  return api.get<HotImageNotification[]>('/image/hot/notifications');
};

// 领取热门奖励
export const claimHotImageReward = (id: number) => {
  return api.post(`/image/hot/claim/${id}`);
};
