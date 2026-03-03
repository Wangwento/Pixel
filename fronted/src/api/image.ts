import api from './index';

// 文生图请求参数（与后端Text2ImgRequest对应）
export type Text2ImgRequest = {
  prompt: string;
  style?: string;
};

// 图生图请求参数（预留）
export type Img2ImgRequest = {
  prompt: string;
  style?: string;
  sourceImageUrl?: string;
  sourceImageBase64?: string;
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
  imageUrl?: string;
  imageBase64?: string;
  ossUrl?: string;
  revisedPrompt?: string;
  vendor?: string;
  model?: string;
  generationTimeMs?: number;
  fromCache?: boolean;
};

// 文生图
export const text2img = (params: Text2ImgRequest) => {
  return api.post<GenerationResult>('/avatar/text2img', params);
};

// 图生图
export const img2img = (params: Img2ImgRequest) => {
  return api.post<GenerationResult>('/avatar/img2img', params);
};

// 通用生成（兼容旧代码）
export const generate = (params: GenerationRequest) => {
  return api.post<GenerationResult>('/avatar/generate', params);
};

// 获取生成历史
export const getImageHistory = (page: number = 1, pageSize: number = 10) => {
  return api.get('/image/history', { params: { page, pageSize } });
};