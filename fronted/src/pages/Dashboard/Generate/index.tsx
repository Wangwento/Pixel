import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { ConfigProvider, Input, Button, Select, message, Spin, Tooltip, Tag, Modal, Empty, Pagination } from 'antd';
import { LockOutlined, SendOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons';
import {
  PictureOutlined,
  ThunderboltOutlined,
  DownloadOutlined,
  ReloadOutlined,
  ExpandOutlined,
  HeartOutlined,
  CopyOutlined,
  FolderOpenOutlined,
} from '@ant-design/icons';
import { getStyleList } from '../../../api/style';
import type { StyleTemplate } from '../../../api/style';
import { text2img, img2img, getModels, getAssets } from '../../../api/image';
import type { GenerationResult, GenerationResponse, ModelInfo, ModelParam, ImageAsset } from '../../../api/image';
import { useAuth } from '../../../contexts/AuthContext';
import { useThrottle } from '../../../hooks/useDebounce';
import { useTempImages } from '../../../hooks/useTempImages';
import ImageArrayUpload from '../../../components/ImageArrayUpload';
import ModelParamsForm from '../../../components/ModelParamsForm';
import './index.css';

const { TextArea } = Input;

// 宽高比选项（带VIP标识和矩形比例）
const RATIO_OPTIONS = [
  { label: '1:1 (1024×1024)', value: '1:1', vip: false, w: 14, h: 14 },
  { label: '4:3 (1024×768)', value: '4:3', vip: false, w: 16, h: 12 },
  { label: '3:4 (768×1024)', value: '3:4', vip: false, w: 12, h: 16 },
  { label: '16:9 (1024×576)', value: '16:9', vip: true, w: 18, h: 10 },
  { label: '9:16 (576×1024)', value: '9:16', vip: true, w: 10, h: 18 },
];

// 比例矩形图标
const RatioIcon: React.FC<{ w: number; h: number }> = ({ w, h }) => (
  <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} className="ratio-icon">
    <rect x="0.5" y="0.5" width={w - 1} height={h - 1} rx="2"
      fill="none" stroke="currentColor" strokeWidth="1.2" />
  </svg>
);

// 质量选项（带VIP标识）
const QUALITY_OPTIONS = [
  { label: '1K 标准', value: '1k', vip: false },
  { label: '2K 高清', value: '2k', vip: true },
  { label: '4K 超清', value: '4k', vip: true },
];

const BUILT_IN_PARAM_KEYS = new Set([
  'prompt',
  'promp',
  'image',
  'aspect_ratio',
  'image_size',
]);

const normalizeParamValue = (param: ModelParam, rawValue?: unknown): unknown => {
  if (rawValue === undefined || rawValue === null || rawValue === '') {
    return undefined;
  }
  if (Array.isArray(rawValue)) {
    return rawValue;
  }
  const value = String(rawValue);
  switch (param.paramType) {
    case 'number': {
      const parsed = Number(value);
      return Number.isNaN(parsed) ? undefined : parsed;
    }
    case 'boolean':
      return value === 'true';
    case 'multiSelect':
      try {
        const parsed = JSON.parse(value);
        return Array.isArray(parsed) ? parsed : [value];
      } catch {
        return value.split(',').map(item => item.trim()).filter(Boolean);
      }
    default:
      return value;
  }
};

const previewLoadingHints = [
  '正在搭建构图与主体轮廓…',
  '正在补充细节、光影和材质…',
  '马上完成高清出图，请稍候…',
];

const GeneratePage: React.FC = () => {
  const [themeMode, setThemeMode] = useState<'light' | 'dark'>(() => {
    if (typeof document !== 'undefined') {
      return document.body.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
    }
    if (typeof localStorage !== 'undefined') {
      return localStorage.getItem('dashboard-theme') === 'light' ? 'light' : 'dark';
    }
    return 'dark';
  });
  const [activeTab, setActiveTab] = useState<'text2img' | 'img2img'>('text2img');
  const [prompt, setPrompt] = useState('');
  const [model, setModel] = useState('');
  const [modelList, setModelList] = useState<ModelInfo[]>([]);
  const [modelsLoading, setModelsLoading] = useState(true);
  const [ratio, setRatio] = useState('1:1');
  const [quality, setQuality] = useState('1k');
  const [loading, setLoading] = useState(false);
  const [generatedImage, setGeneratedImage] = useState<GenerationResult | null>(null);
  const [activePreviewIndex, setActivePreviewIndex] = useState(0);
  const [imageLoaded, setImageLoaded] = useState(false);
  const [loadingHintIndex, setLoadingHintIndex] = useState(0);
  const [isGenerating, setIsGenerating] = useState(false);
  const [styleTemplates, setStyleTemplates] = useState<StyleTemplate[]>([]);
  const [stylesLoading, setStylesLoading] = useState(true);
  const [selectedStyle, setSelectedStyle] = useState<string | null>(null);
  const [modelParamValues, setModelParamValues] = useState<Record<string, unknown>>({});
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [assetModalOpen, setAssetModalOpen] = useState(false);
  const [assetLibraryLoading, setAssetLibraryLoading] = useState(false);
  const [assetLibraryItems, setAssetLibraryItems] = useState<ImageAsset[]>([]);
  const [assetLibraryPage, setAssetLibraryPage] = useState(1);
  const [assetLibraryTotal, setAssetLibraryTotal] = useState(0);
  const [importingAssetUrl, setImportingAssetUrl] = useState<string | null>(null);
  const { refreshUser, currentUser } = useAuth();
  const { addTempUrl, removeTempUrl, cleanup } = useTempImages();
  const assetLibraryPageSize = 8;
  const themeClass = themeMode === 'light' ? 'theme-light' : 'theme-dark';
  const currentModel = useMemo(
    () => modelList.find((item) => item.modelId === model),
    [model, modelList],
  );
  const dynamicVisibleParams = useMemo(() => {
    return (currentModel?.params || [])
      .filter((param) => param.visible && !BUILT_IN_PARAM_KEYS.has(param.paramKey))
      .sort((left, right) => left.displayOrder - right.displayOrder);
  }, [currentModel]);

  useEffect(() => {
    if (!loading) {
      setLoadingHintIndex(0);
      return;
    }

    const timer = window.setInterval(() => {
      setLoadingHintIndex((prev) => (prev + 1) % previewLoadingHints.length);
    }, 1800);

    return () => window.clearInterval(timer);
  }, [loading]);

  useEffect(() => {
    let isMounted = true;
    const fetchStyles = async () => {
      try {
        const res = await getStyleList() as unknown as { code: number; data: StyleTemplate[] };
        if (isMounted && res.code === 200) {
          setStyleTemplates(res.data);
        }
      } catch (error) {
        console.error('获取风格模板失败:', error);
      } finally {
        if (isMounted) setStylesLoading(false);
      }
    };
    fetchStyles();
    return () => { isMounted = false; };
  }, []);

  // 从后端获取可用模型列表
  useEffect(() => {
    let isMounted = true;
    const fetchModels = async () => {
      try {
        const res = await getModels() as unknown as { code: number; data: ModelInfo[] };
        if (isMounted && res.code === 200 && res.data?.length > 0) {
          setModelList(res.data);
          // 默认选中第一个可用模型
          const firstAvailable = res.data.find(m => m.available);
          if (firstAvailable) {
            setModel(firstAvailable.modelId);
          }
        }
      } catch (error) {
        console.error('获取模型列表失败:', error);
      } finally {
        if (isMounted) setModelsLoading(false);
      }
    };
    fetchModels();
    return () => { isMounted = false; };
  }, []);

  useEffect(() => {
    if (typeof document === 'undefined') {
      return undefined;
    }

    const syncTheme = () => {
      setThemeMode(document.body.getAttribute('data-theme') === 'light' ? 'light' : 'dark');
    };

    syncTheme();
    const observer = new MutationObserver(syncTheme);
    observer.observe(document.body, { attributes: true, attributeFilter: ['data-theme'] });
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (activeTab !== 'img2img' || modelList.length === 0) {
      return;
    }
    if (currentModel?.available && currentModel.supportsImageInput) {
      return;
    }
    const firstSupported = modelList.find((item) => item.available && item.supportsImageInput);
    if (firstSupported) {
      setModel(firstSupported.modelId);
    }
  }, [activeTab, currentModel, modelList]);

  useEffect(() => {
    const defaults = dynamicVisibleParams.reduce<Record<string, unknown>>((accumulator, param) => {
      const normalized = normalizeParamValue(param, param.defaultValue);
      if (normalized !== undefined) {
        accumulator[param.paramKey] = normalized;
      }
      return accumulator;
    }, {});
    setModelParamValues(defaults);
  }, [dynamicVisibleParams]);

  const fetchAssetLibrary = useCallback(async (page: number) => {
    setAssetLibraryLoading(true);
    try {
      const res = await getAssets({ page, pageSize: assetLibraryPageSize }) as unknown as {
        code: number;
        data?: { list?: ImageAsset[]; total?: number };
        message?: string;
      };
      if (res.code === 200 && res.data) {
        setAssetLibraryItems(res.data.list || []);
        setAssetLibraryTotal(res.data.total || 0);
      } else {
        message.error(res.message || '获取资源库失败');
      }
    } catch {
      message.error('获取资源库失败');
    } finally {
      setAssetLibraryLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!assetModalOpen) {
      return;
    }
    fetchAssetLibrary(assetLibraryPage);
  }, [assetModalOpen, assetLibraryPage, fetchAssetLibrary]);

  const isAssetImported = useCallback((imageUrl: string) => {
    return imageUrls.includes(imageUrl);
  }, [imageUrls]);

  const openAssetLibrary = () => {
    setAssetLibraryPage(1);
    setAssetModalOpen(true);
  };

  const handleImportAsset = (item: ImageAsset) => {
    if (imageUrls.length >= 10) {
      message.warning('参考图片最多上传10张');
      return;
    }
    if (isAssetImported(item.imageUrl)) {
      message.info('该图片已导入');
      return;
    }
    setImportingAssetUrl(item.imageUrl);
    setImageUrls((prev) => [...prev, item.imageUrl]);
    setImportingAssetUrl(null);
    setAssetModalOpen(false);
    message.success('已从资源库导入参考图');
  };

  const handleGenerate = useCallback(async () => {
    console.log('[Generate] 开始生成，activeTab:', activeTab);
    console.log('[Generate] prompt:', prompt);
    console.log('[Generate] model:', model);
    console.log('[Generate] imageUrls:', imageUrls);

    if (!prompt.trim()) {
      message.warning('请输入图片描述');
      return;
    }
    if (!currentUser?.isVip && (currentUser?.dailyUsed || 0) >= (currentUser?.dailyLimit || 10)) {
      message.error(`今日生成次数已达上限(${currentUser?.dailyLimit || 10}次)`);
      return;
    }
    setIsGenerating(true);
    setLoading(true);

    try {
      console.log('[Generate] currentModel:', currentModel);
      console.log('[Generate] model params:', modelParamValues);

      if (activeTab === 'img2img') {
        if (imageUrls.length === 0) {
          message.warning('请上传至少一张参考图');
          setLoading(false);
          setIsGenerating(false);
          return;
        }
        if (currentModel && !currentModel.supportsImageInput) {
          message.warning('当前模型暂不支持图生图，请切换模型');
          setLoading(false);
          setIsGenerating(false);
          return;
        }
      }

      console.log('[Generate] 准备调用API...');
      const res = (activeTab === 'img2img'
        ? await img2img({
          prompt: prompt.trim(),
          style: selectedStyle || undefined,
          modelId: model || undefined,
          aspectRatio: ratio as '1:1' | '2:3' | '3:2' | '3:4' | '4:3' | '4:5' | '5:4' | '9:16' | '16:9' | '21:9',
          imageSize: quality.toUpperCase() as '1K' | '2K' | '4K',
          imageUrls: imageUrls,
          params: Object.keys(modelParamValues).length > 0 ? modelParamValues : undefined,
        })
        : await text2img({
          prompt: prompt.trim(),
          style: selectedStyle || undefined,
          modelId: model || undefined,
          aspectRatio: ratio as '1:1' | '2:3' | '3:2' | '3:4' | '4:3' | '4:5' | '5:4' | '9:16' | '16:9' | '21:9',
          imageSize: quality.toUpperCase() as '1K' | '2K' | '4K',
          params: Object.keys(modelParamValues).length > 0 ? modelParamValues : undefined,
        })) as unknown as { code: number; data: GenerationResponse; message?: string };

      if (res.code === 200 && res.data) {
        const response = res.data;
        if (response.taskStatus === 'RUNNING') {
          message.info(response.message || '图片正在生成中，请稍候...');
        } else if (response.taskStatus === 'SUCCESS' && response.result) {
          setActivePreviewIndex(0);
          setImageLoaded(false);
          setGeneratedImage(response.result);
          if (response.result.revisedPrompt) {
            console.log('[Generate] AI 优化提示词:', response.result.revisedPrompt);
          }
          const timeMs = response.result.generationTimeMs || 0;
          message.success(`生成成功！耗时 ${(timeMs / 1000).toFixed(1)}s`);
          refreshUser();
          setLoading(false);
          setIsGenerating(false);
        } else if (response.taskStatus === 'FAILED') {
          message.error(response.message || '生成失败');
          setLoading(false);
          setIsGenerating(false);
        }
      } else {
        message.error(res.message || '生成失败');
        setLoading(false);
        setIsGenerating(false);
      }
    } catch (error: unknown) {
      console.error('[Generate] 捕获到错误:', error);
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '生成失败，请重试');
      setLoading(false);
      setIsGenerating(false);
    } finally {
      if (activeTab === 'img2img') {
        await cleanup();
      }
    }
  }, [activeTab, prompt, selectedStyle, model, currentModel, modelParamValues, ratio, quality, imageUrls, currentUser, refreshUser, cleanup]);

  const throttledGenerate = useThrottle(handleGenerate, 2000);

  const previewItems = useMemo(() => {
    if (!generatedImage) {
      return [] as Array<{ src: string; raw: string }>;
    }

    const ossUrls = generatedImage.ossUrls || [];
    const imageUrls = generatedImage.imageUrls || [];
    const imageBase64List = generatedImage.imageBase64List || [];
    const maxCount = Math.max(ossUrls.length, imageUrls.length, imageBase64List.length);

    if (maxCount > 0) {
      return Array.from({ length: maxCount }, (_, index) => {
        const raw = ossUrls[index]
          || imageUrls[index]
          || (imageBase64List[index] ? `data:image/png;base64,${imageBase64List[index]}` : '');
        return { src: raw, raw };
      }).filter(item => !!item.src);
    }

    const singleSrc = generatedImage.ossUrl
      || generatedImage.imageUrl
      || (generatedImage.imageBase64 ? `data:image/png;base64,${generatedImage.imageBase64}` : '');

    return singleSrc ? [{ src: singleSrc, raw: singleSrc }] : [];
  }, [generatedImage]);

  useEffect(() => {
    if (previewItems.length === 0) {
      setActivePreviewIndex(0);
      return;
    }
    if (activePreviewIndex >= previewItems.length) {
      setActivePreviewIndex(0);
    }
  }, [activePreviewIndex, previewItems]);

  const previewImageSrc = previewItems[activePreviewIndex]?.src || '';
  const previewRawSrc = previewItems[activePreviewIndex]?.raw || '';

  useEffect(() => {
    if (previewImageSrc) {
      setImageLoaded(false);
    }
  }, [previewImageSrc]);

  const showPreviewOverlay = loading || (!!previewImageSrc && !imageLoaded);
  const previewOverlayTitle = loading
    ? previewLoadingHints[loadingHintIndex]
    : '图片正在加载，马上完整呈现';
  const previewOverlayDescription = loading
    ? previewImageSrc
      ? '上一张结果会先保留，新图会从顶部向下自然揭幕。'
      : 'AI 会先确定构图，再逐步补完细节和光影。'
    : '高清图已返回，正在为你呈现更顺滑的加载动画。';

  const handleDownload = () => {
    const imageUrl = previewRawSrc;
    if (imageUrl) {
      const link = document.createElement('a');
      link.href = imageUrl;
      link.download = `pixel-${Date.now()}.png`;
      link.target = '_blank';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    }
  };

  const handleCopyLink = () => {
    const imageUrl = previewRawSrc;
    if (imageUrl) {
      navigator.clipboard.writeText(imageUrl).then(() => {
        message.success('链接已复制到剪贴板');
      }).catch(() => {
        message.error('复制失败');
      });
    }
  };

  const handlePreviewStep = useCallback((direction: 'prev' | 'next') => {
    if (previewItems.length <= 1) {
      return;
    }
    setActivePreviewIndex((prev) => {
      if (direction === 'prev') {
        return prev === 0 ? previewItems.length - 1 : prev - 1;
      }
      return prev === previewItems.length - 1 ? 0 : prev + 1;
    });
  }, [previewItems.length]);

  const handleSelectStyle = (template: StyleTemplate) => {
    setSelectedStyle(selectedStyle === template.nameEn ? null : template.nameEn);
  };

  const isVip = !!currentUser?.isVip;

  // 构建带VIP标识的下拉选项
  // 从params中获取参数选项
  const getParamOptions = useCallback((paramKey: string): string[] => {
    const currentModel = modelList.find(m => m.modelId === model);
    if (!currentModel?.params) return [];
    const param = currentModel.params.find(p => p.paramKey === paramKey);
    if (!param?.options) return [];
    try {
      return JSON.parse(param.options);
    } catch {
      return [];
    }
  }, [model, modelList]);

  const buildVipOptions = useCallback((options: { label: string; value: string; vip: boolean }[]) =>
    options.map(opt => ({
      value: opt.value,
      disabled: opt.vip && !isVip,
      label: (
        <span className="vip-option">
          {opt.label}
          {opt.vip && <Tag color="gold" className="vip-tag"><LockOutlined /> VIP</Tag>}
        </span>
      ),
    })), [isVip]);

  // 宽高比选项（从params动态读取）
  const ratioOptions = useMemo(() => {
    const paramOptions = getParamOptions('aspect_ratio');
    if (paramOptions.length === 0) {
      return RATIO_OPTIONS.map(opt => ({
        value: opt.value,
        disabled: opt.vip && !isVip,
        label: (
          <span className="vip-option">
            <span className="ratio-label-left">
              <RatioIcon w={opt.w} h={opt.h} />
              {opt.label}
            </span>
            {opt.vip && <Tag color="gold" className="vip-tag"><LockOutlined /> VIP</Tag>}
          </span>
        ),
      }));
    }
    return paramOptions.map(value => {
      const defaultOpt = RATIO_OPTIONS.find(o => o.value === value);
      return {
        value,
        disabled: defaultOpt?.vip && !isVip,
        label: (
          <span className="vip-option">
            <span className="ratio-label-left">
              <RatioIcon w={defaultOpt?.w || 14} h={defaultOpt?.h || 14} />
              {defaultOpt?.label || value}
            </span>
            {defaultOpt?.vip && <Tag color="gold" className="vip-tag"><LockOutlined /> VIP</Tag>}
          </span>
        ),
      };
    });
  }, [getParamOptions, isVip]);

  // 质量选项（从params动态读取）
  const qualityOptions = useMemo(() => {
    const paramOptions = getParamOptions('image_size');
    if (paramOptions.length === 0) {
      return buildVipOptions(QUALITY_OPTIONS);
    }
    return paramOptions.map(value => {
      const defaultOpt = QUALITY_OPTIONS.find(o => o.value.toLowerCase() === value.toLowerCase());
      return {
        value: value.toLowerCase(),
        disabled: defaultOpt?.vip && !isVip,
        label: (
          <span className="vip-option">
            {defaultOpt?.label || value}
            {defaultOpt?.vip && <Tag color="gold" className="vip-tag"><LockOutlined /> VIP</Tag>}
          </span>
        ),
      };
    });
  }, [buildVipOptions, getParamOptions, isVip]);

  const generateTheme = useMemo(() => {
    const isLight = themeMode === 'light';
    return {
      token: {
        colorBgContainer: isLight ? '#f5f7fa' : 'rgba(255, 255, 255, 0.06)',
        colorText: isLight ? '#1a202c' : '#ffffff',
        colorTextPlaceholder: isLight ? 'rgba(0, 0, 0, 0.35)' : 'rgba(255, 255, 255, 0.42)',
        colorBorder: isLight ? '#e2e8f0' : 'rgba(99, 179, 237, 0.2)',
        colorTextSecondary: isLight ? 'rgba(0, 0, 0, 0.45)' : 'rgba(255, 255, 255, 0.65)',
        colorTextTertiary: isLight ? 'rgba(0, 0, 0, 0.35)' : 'rgba(255, 255, 255, 0.45)',
        colorTextQuaternary: isLight ? 'rgba(0, 0, 0, 0.25)' : 'rgba(255, 255, 255, 0.35)',
      },
      components: {
        Input: {
          colorBgContainer: isLight ? '#f5f7fa' : 'rgba(255, 255, 255, 0.06)',
          colorText: isLight ? '#1a202c' : '#ffffff',
          colorTextPlaceholder: isLight ? 'rgba(0, 0, 0, 0.35)' : 'rgba(255, 255, 255, 0.42)',
          activeBg: isLight ? '#f5f7fa' : 'rgba(255, 255, 255, 0.06)',
          hoverBg: isLight ? '#f5f7fa' : 'rgba(255, 255, 255, 0.08)',
          activeBorderColor: isLight ? '#cbd5e1' : 'rgba(99, 179, 237, 0.32)',
          hoverBorderColor: isLight ? '#cbd5e1' : 'rgba(99, 179, 237, 0.32)',
        },
        Select: {
          selectorBg: isLight ? '#f5f7fa' : 'rgba(255, 255, 255, 0.06)',
          colorBgContainer: isLight ? '#f5f7fa' : 'rgba(255, 255, 255, 0.06)',
          colorText: isLight ? '#1a202c' : '#ffffff',
          colorTextPlaceholder: isLight ? 'rgba(0, 0, 0, 0.35)' : 'rgba(255, 255, 255, 0.42)',
          colorBorder: isLight ? '#e2e8f0' : 'rgba(99, 179, 237, 0.2)',
          activeBorderColor: isLight ? '#cbd5e1' : 'rgba(99, 179, 237, 0.32)',
          hoverBorderColor: isLight ? '#cbd5e1' : 'rgba(99, 179, 237, 0.32)',
          optionSelectedBg: isLight ? 'rgba(76, 81, 191, 0.1)' : 'rgba(99, 179, 237, 0.16)',
          optionActiveBg: isLight ? '#eef2f7' : 'rgba(255, 255, 255, 0.05)',
          optionSelectedColor: isLight ? '#1a202c' : '#ffffff',
        },
        InputNumber: {
          colorBgContainer: isLight ? '#f5f7fa' : 'rgba(255, 255, 255, 0.06)',
          colorText: isLight ? '#1a202c' : '#ffffff',
          colorTextPlaceholder: isLight ? 'rgba(0, 0, 0, 0.35)' : 'rgba(255, 255, 255, 0.42)',
          activeBorderColor: isLight ? '#cbd5e1' : 'rgba(99, 179, 237, 0.32)',
          hoverBorderColor: isLight ? '#cbd5e1' : 'rgba(99, 179, 237, 0.32)',
        },
      },
    };
  }, [themeMode]);

  const assetImportBtnStyle = useMemo<React.CSSProperties>(() => {
    return themeMode === 'light'
      ? {
        background: 'linear-gradient(135deg, #ffffff 0%, #f8fafc 100%)',
        backgroundColor: '#ffffff',
        color: '#111827',
        border: '1px solid #d6dde6',
        boxShadow: '0 8px 18px rgba(15, 23, 42, 0.06)',
        height: 36,
        paddingInline: 14,
      }
      : {
        background: 'linear-gradient(135deg, rgba(30, 41, 59, 0.96) 0%, rgba(15, 23, 42, 0.92) 100%)',
        backgroundColor: '#111827',
        color: '#f8fafc',
        border: '1px solid rgba(99, 179, 237, 0.28)',
        boxShadow: '0 10px 24px rgba(0, 0, 0, 0.26)',
        backdropFilter: 'blur(12px)',
        height: 36,
        paddingInline: 14,
      };
  }, [themeMode]);

  const assetLibraryModalStyles = useMemo(() => {
    const isLight = themeMode === 'light';
    return {
      mask: {
        background: isLight ? 'rgba(15, 23, 42, 0.28)' : 'rgba(2, 6, 23, 0.66)',
        backdropFilter: 'blur(8px)',
      },
      content: {
        background: isLight
          ? 'linear-gradient(180deg, #ffffff 0%, #f8fafc 100%)'
          : 'linear-gradient(180deg, rgba(17, 24, 39, 0.98) 0%, rgba(8, 15, 28, 0.98) 100%)',
        border: isLight ? '1px solid #e5e7eb' : '1px solid rgba(99, 179, 237, 0.18)',
        borderRadius: 24,
        boxShadow: isLight
          ? '0 20px 48px rgba(15, 23, 42, 0.12)'
          : '0 20px 48px rgba(0, 0, 0, 0.35)',
      },
      header: {
        background: 'transparent',
        borderBottom: isLight ? '1px solid #e5e7eb' : '1px solid rgba(255, 255, 255, 0.08)',
      },
      body: {
        background: 'transparent',
        paddingTop: 18,
      },
    };
  }, [themeMode]);

  return (
    <ConfigProvider theme={generateTheme}>
      <div className={`generate-page generate-page-compact ${themeClass} ${activeTab === 'text2img' ? 'tab-text2img' : 'tab-img2img'}`}>
      {/* 顶部标签切换 */}
      <div className="generate-tabs">
        <button
          className={`gen-tab ${activeTab === 'text2img' ? 'active' : ''}`}
          onClick={() => setActiveTab('text2img')}
        >
          <ThunderboltOutlined /> 文生图
        </button>
        <button
          className={`gen-tab ${activeTab === 'img2img' ? 'active' : ''}`}
          onClick={() => setActiveTab('img2img')}
        >
          <PictureOutlined /> 图生图
        </button>
        {!currentUser?.isVip && (
          <span className="usage-info">
            今日: {currentUser?.dailyUsed || 0}/{currentUser?.dailyLimit || 10}
          </span>
        )}
      </div>

      <div className="generate-layout">
        {/* 左侧控制面板 */}
        <div className="control-panel">
          <div className="control-panel-surface">
            <div className="panel-hero">
              <span className="panel-hero-kicker">AI 创作工作台</span>
              <div className="panel-hero-badge">
                {activeTab === 'img2img' ? `${imageUrls.length}/10 参考图` : `${ratio} · ${quality.toUpperCase()}`}
              </div>
            </div>

            {/* 模型选择 */}
            <div className="control-item control-card control-card-highlight">
              <label>模型</label>
              <Select
                value={model || undefined}
                onChange={(val) => {
                  const m = modelList.find(m => m.modelId === val);
                  if (m && !m.available) {
                    message.warning('该模型为VIP专属，请升级VIP');
                    return;
                  }
                  if (activeTab === 'img2img' && m && !m.supportsImageInput) {
                    message.warning('该模型暂不支持图生图');
                    return;
                  }
                  setModel(val);
                }}
                loading={modelsLoading}
                placeholder={modelsLoading ? '加载中...' : '选择模型'}
                options={modelList.map(m => ({
                  value: m.modelId,
                  disabled: !m.available || (activeTab === 'img2img' && !m.supportsImageInput),
                  label: (
                    <span className="vip-option">
                      {m.displayName}
                      {m.minVipLevel > 0 && <Tag color="gold" className="vip-tag"><LockOutlined /> VIP</Tag>}
                      {activeTab === 'img2img' && !m.supportsImageInput && (
                        <Tag color="default" className="vip-tag">仅文生图</Tag>
                      )}
                    </span>
                  ),
                }))}
                className="control-select"
                popupClassName="gen-select-dropdown"
              />
            </div>

            {/* 图生图：上传参考图 */}
            {activeTab === 'img2img' && (
              <div className="control-item control-card control-card-upload">
                <div className="upload-header">
                  <div className="upload-header-copy">
                    <label>上传参考图片</label>
                    <span>支持拖拽上传，也支持从历史资源库快速导入</span>
                  </div>
                  <Button
                    type="default"
                    size="middle"
                    shape="round"
                    icon={<FolderOpenOutlined />}
                    onClick={openAssetLibrary}
                    className="asset-import-btn"
                    style={assetImportBtnStyle}
                  >
                    从资源库导入
                  </Button>
                </div>
                <ImageArrayUpload
                  value={imageUrls}
                  onChange={setImageUrls}
                  onTempUrlAdd={addTempUrl}
                  onTempUrlRemove={removeTempUrl}
                  maxCount={10}
                  className={themeClass}
                />
              </div>
            )}

            {/* 提示词 */}
            <div className="control-item control-card">
              <label>提示词</label>
              <TextArea
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                placeholder="描述你想生成的内容..."
                rows={4}
                className="prompt-textarea"
                maxLength={5000}
              />
              <div className="prompt-count">{prompt.length}/5000</div>
            </div>

            {/* 热门风格 */}
            <div className="control-item control-card">
              <label>热门风格</label>
              {stylesLoading ? (
                <Spin size="small" />
              ) : (
                <div className="template-tags">
                  {styleTemplates.map((t) => (
                    <span
                      key={t.id}
                      className={`template-tag ${selectedStyle === t.nameEn ? 'selected' : ''}`}
                      onClick={() => handleSelectStyle(t)}
                      title={t.description}
                    >
                      {t.name}
                    </span>
                  ))}
                </div>
              )}
            </div>

            <div className="control-split-grid">
              {/* 宽高比 */}
              <div className="control-item control-card">
                <label>宽高比</label>
                <Select
                  value={ratio}
                  onChange={(val) => {
                    if (RATIO_OPTIONS.find(r => r.value === val)?.vip && !isVip) {
                      message.warning('该比例为VIP专属，请升级VIP');
                      return;
                    }
                    setRatio(val);
                  }}
                  options={ratioOptions}
                  className="control-select"
                  popupClassName="gen-select-dropdown"
                />
              </div>

              {/* 质量 */}
              <div className="control-item control-card">
                <label>质量</label>
                <Select
                  value={quality}
                  onChange={(val) => {
                    const selectedOpt = QUALITY_OPTIONS.find(q => q.value === val);
                    if (selectedOpt?.vip && !isVip) {
                      message.warning('该质量为VIP专属，请升级VIP');
                      return;
                    }
                    setQuality(val);
                  }}
                  options={qualityOptions}
                  className="control-select"
                  popupClassName="gen-select-dropdown"
                />
              </div>
            </div>

            {dynamicVisibleParams.length > 0 && (
              <ModelParamsForm
                params={dynamicVisibleParams}
                values={modelParamValues}
                onChange={setModelParamValues}
              />
            )}

            {/* 生成按钮 */}
            <div className="generate-btn-wrapper">
              <Button
                type="primary"
                size="large"
                loading={loading}
                onClick={throttledGenerate}
                className="generate-btn"
                icon={<SendOutlined />}
              >
                {loading ? '生成中...' : '开始生成'}
              </Button>
            </div>
          </div>
        </div>

        {/* 右侧预览区 */}
        <div className="preview-panel">
          <div className="preview-panel-surface">
          <div className="preview-area">
            {previewImageSrc ? (
              <div className="preview-result">
                <div className={`preview-image-stage ${loading ? 'is-generating' : ''}`}>
                  {previewItems.length > 1 && (
                    <>
                      <button
                        type="button"
                        className="preview-gallery-arrow preview-gallery-arrow-left"
                        onClick={() => handlePreviewStep('prev')}
                        aria-label="上一张"
                      >
                        <LeftOutlined />
                      </button>
                      <button
                        type="button"
                        className="preview-gallery-arrow preview-gallery-arrow-right"
                        onClick={() => handlePreviewStep('next')}
                        aria-label="下一张"
                      >
                        <RightOutlined />
                      </button>
                      <div className="preview-gallery-counter">
                        {activePreviewIndex + 1} / {previewItems.length}
                      </div>
                    </>
                  )}
                  <img
                    key={previewImageSrc}
                    src={previewImageSrc}
                    alt="Generated"
                    className={`preview-generated-image ${imageLoaded ? 'is-loaded' : 'is-loading'}`}
                    onLoad={() => setImageLoaded(true)}
                  />
                  {showPreviewOverlay && (
                    <div className="preview-overlay">
                      <div className="preview-overlay-beam" />
                      <div className="preview-overlay-content">
                        <span className="preview-overlay-badge">{loading ? 'AI 创作中' : '高清图载入中'}</span>
                        <Spin size="large" />
                        <strong className="preview-overlay-title">{previewOverlayTitle}</strong>
                        <span className="preview-overlay-description">{previewOverlayDescription}</span>
                      </div>
                    </div>
                  )}
                </div>
                {previewItems.length > 1 && (
                  <div className="preview-gallery-dots">
                    {previewItems.map((_, index) => (
                      <button
                        key={`preview-dot-${index}`}
                        type="button"
                        className={`preview-gallery-dot ${index === activePreviewIndex ? 'active' : ''}`}
                        onClick={() => setActivePreviewIndex(index)}
                        aria-label={`查看第 ${index + 1} 张`}
                      />
                    ))}
                  </div>
                )}
                <div className="image-toolbar">
                  <Tooltip title="下载"><Button icon={<DownloadOutlined />} onClick={handleDownload} /></Tooltip>
                  <Tooltip title="复制链接"><Button icon={<CopyOutlined />} onClick={handleCopyLink} /></Tooltip>
                  <Tooltip title="全屏"><Button icon={<ExpandOutlined />} onClick={() => message.info('开发中...')} /></Tooltip>
                  <Tooltip title="收藏"><Button icon={<HeartOutlined />} onClick={() => message.info('开发中...')} /></Tooltip>
                  <Tooltip title="重新生成"><Button icon={<ReloadOutlined />} onClick={throttledGenerate} /></Tooltip>
                </div>
                <div className="image-meta">
                  {generatedImage?.model && <span>模型: {generatedImage.model}</span>}
                  {previewItems.length > 1 && <span>组图: {previewItems.length} 张</span>}
                  {generatedImage?.generationTimeMs && (
                    <span>耗时: {(generatedImage.generationTimeMs / 1000).toFixed(1)}s</span>
                  )}
                  {generatedImage?.fromCache && <span className="cache-tag">缓存</span>}
                </div>
              </div>
            ) : (isGenerating || loading) ? (
              <div className="preview-loading preview-loading-rich">
                <div className="preview-loading-skeleton">
                  <div className="preview-loading-skeleton-frame" />
                  <div className="preview-loading-skeleton-line preview-loading-skeleton-line-short" />
                  <div className="preview-loading-skeleton-line" />
                </div>
                <div className="preview-loading-copy">
                  <span className="preview-overlay-badge">AI 创作中</span>
                  <div className="shimmer-text">{previewOverlayTitle}</div>
                  <div className="shimmer-progress"><div className="progress-bar"></div></div>
                  <p>{previewOverlayDescription}</p>
                </div>
              </div>
            ) : (
              <div className="preview-empty">
                <PictureOutlined />
                <h3>预览区域</h3>
                <p>生成的内容将显示在这里。</p>
              </div>
            )}
          </div>
          </div>
        </div>
      </div>

      <Modal
        open={assetModalOpen}
        onCancel={() => setAssetModalOpen(false)}
        footer={null}
        title="从资源库导入"
        width={880}
        centered
        rootClassName={`asset-library-modal-root ${themeClass}`}
        className="asset-library-modal"
        styles={assetLibraryModalStyles}
      >
        <Spin spinning={assetLibraryLoading}>
          {assetLibraryItems.length > 0 ? (
            <>
              <div className="asset-library-toolbar">
                <div>
                  <div className="asset-library-toolbar-title">历史生成资源库</div>
                  <div className="asset-library-toolbar-subtitle">
                    选择任意历史图片作为当前任务的参考图，导入后会立即出现在上传区域。
                  </div>
                </div>
                <div className="asset-library-toolbar-meta">已选 {imageUrls.length}/10</div>
              </div>
              <div className="asset-library-grid">
                {assetLibraryItems.map((item) => {
                  const imported = isAssetImported(item.imageUrl);
                  return (
                    <div key={item.id} className={`asset-library-card ${imported ? 'imported' : ''}`}>
                      <img src={item.imageUrl} alt={item.prompt || item.title} className="asset-library-image" />
                      <div className="asset-library-info">
                        <p className="asset-library-prompt" title={item.prompt || item.title}>
                          {item.prompt || item.title}
                        </p>
                        <p className="asset-library-time">{item.createdAt}</p>
                        <Button
                          type="primary"
                          size="small"
                          block
                          className="asset-library-import-btn"
                          disabled={imported || imageUrls.length >= 10}
                          loading={importingAssetUrl === item.imageUrl}
                          onClick={() => handleImportAsset(item)}
                        >
                          {imported ? '已导入' : '导入为参考图'}
                        </Button>
                      </div>
                    </div>
                  );
                })}
              </div>
              <div className="asset-library-pagination">
                <Pagination
                  current={assetLibraryPage}
                  total={assetLibraryTotal}
                  pageSize={assetLibraryPageSize}
                  onChange={setAssetLibraryPage}
                  size="small"
                  showSizeChanger={false}
                />
              </div>
            </>
          ) : (
            <Empty description="资源库暂无可导入图片" className="asset-library-empty" />
          )}
        </Spin>
      </Modal>
      </div>
    </ConfigProvider>
  );
};

export default GeneratePage;
