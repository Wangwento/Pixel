import api from './index';
import { memoryCache } from '../utils/cache';

// 风格模板类型
export type StyleTemplate = {
  id: number;
  name: string;
  nameEn: string;
  description: string;
  promptTemplate: string;
  coverImage: string;
  category: string;
  sortOrder: number;
};

// 获取所有风格模板（带缓存）
export const getStyleList = async () => {
  const cacheKey = 'style:list';

  // 先检查缓存
  const cached = memoryCache.get<StyleTemplate[]>(cacheKey);
  if (cached) {
    console.log('[Cache] 使用缓存的风格列表');
    return { code: 200, data: cached };
  }

  // 缓存未命中，请求API
  console.log('[API] 请求风格列表');
  const result = await api.get<StyleTemplate[]>('/style/list');

  // 保存到缓存（5分钟）
  if (result && (result as any).code === 200) {
    memoryCache.set(cacheKey, (result as any).data, 5 * 60 * 1000);
  }

  return result;
};

// 根据ID获取模板
export const getStyleById = (id: number) => {
  return api.get<StyleTemplate>(`/style/${id}`);
};

// 根据英文名获取模板
export const getStyleByName = (nameEn: string) => {
  return api.get<StyleTemplate>(`/style/name/${nameEn}`);
};

// 按分类获取模板
export const getStyleByCategory = (category: string) => {
  return api.get<StyleTemplate[]>(`/style/category/${category}`);
};
