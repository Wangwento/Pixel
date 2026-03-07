import api from './index';

// 文生图请求参数（与后端Text2ImgRequest对应）
export type Text2ImgRequest = {
  prompt: string;
  style?: string;
  modelId?: string;
  aspectRatio?: '1:1' | '2:3' | '3:2' | '3:4' | '4:3' | '4:5' | '5:4' | '9:16' | '16:9' | '21:9';
  imageSize?: '1K' | '2K' | '4K';
};

// 图生图请求参数（预留）
export type Img2ImgRequest = {
  prompt: string;
  style?: string;
  modelId?: string;
  responseFormat?: 'url' | 'b64_json';
  aspectRatio?: '1:1' | '2:3' | '3:2' | '3:4' | '4:3' | '4:5' | '5:4' | '9:16' | '16:9' | '21:9';
  imageSize?: '1K' | '2K' | '4K';
  images: File[];
  imageUrls?: string[];
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
  ossUrl?: string;
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
  params.images.forEach((image) => {
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
export const getImageHistory = (page: number = 1, pageSize: number = 10) => {
  return api.get('/image/history', { params: { page, pageSize } });
};

// 模型信息
export type ModelInfo = {
  modelId: string;
  displayName: string;
  minVipLevel: number;
  available: boolean;
  imageToImageSupported: boolean;
};

// 获取可用模型列表
export const getModels = () => {
  return api.get<ModelInfo[]>('/avatar/models');
};
