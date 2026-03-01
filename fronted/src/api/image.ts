import api from './index';

export interface GenerateImageParams {
  prompt: string;
  style?: string;
  size?: string;
}

export interface GenerateImageResponse {
  code: number;
  message: string;
  data: {
    imageUrl: string;
    taskId: string;
  };
}

export interface ImageToImageParams {
  prompt: string;
  imageFile: File;
  style?: string;
}

// 文生图接口
export const generateImage = (params: GenerateImageParams): Promise<GenerateImageResponse> => {
  return api.post('/image/generate', params);
};

// 图生图接口
export const imageToImage = (params: ImageToImageParams): Promise<GenerateImageResponse> => {
  const formData = new FormData();
  formData.append('prompt', params.prompt);
  formData.append('image', params.imageFile);
  if (params.style) {
    formData.append('style', params.style);
  }
  return api.post('/image/transform', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
};

// 获取生成历史
export const getImageHistory = (page: number = 1, pageSize: number = 10) => {
  return api.get('/image/history', { params: { page, pageSize } });
};