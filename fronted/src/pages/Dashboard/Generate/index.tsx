import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Input, Button, Select, Upload, message, Spin, Tooltip, Tag, Modal, Empty, Pagination } from 'antd';
import { LockOutlined, SendOutlined } from '@ant-design/icons';
import {
  PictureOutlined,
  ThunderboltOutlined,
  DownloadOutlined,
  ReloadOutlined,
  ExpandOutlined,
  HeartOutlined,
  CopyOutlined,
  InboxOutlined,
  FolderOpenOutlined,
  CloseOutlined,
} from '@ant-design/icons';
import { getStyleList } from '../../../api/style';
import type { StyleTemplate } from '../../../api/style';
import { text2img, img2img, getModels, getImageHistory } from '../../../api/image';
import type { GenerationResult, GenerationResponse, ModelInfo } from '../../../api/image';
import { useAuth } from '../../../contexts/AuthContext';
import { useThrottle } from '../../../hooks/useDebounce';
import type { UploadFile } from 'antd/es/upload/interface';
import './index.css';

const { TextArea } = Input;
const { Dragger } = Upload;

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

interface AssetLibraryItem {
  id: string;
  imageUrl: string;
  prompt: string;
  style: string;
  createdAt: string;
}

const GeneratePage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'text2img' | 'img2img'>('text2img');
  const [prompt, setPrompt] = useState('');
  const [model, setModel] = useState('');
  const [modelList, setModelList] = useState<ModelInfo[]>([]);
  const [modelsLoading, setModelsLoading] = useState(true);
  const [ratio, setRatio] = useState('1:1');
  const [quality, setQuality] = useState('1k');
  const [loading, setLoading] = useState(false);
  const [generatedImage, setGeneratedImage] = useState<GenerationResult | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [styleTemplates, setStyleTemplates] = useState<StyleTemplate[]>([]);
  const [stylesLoading, setStylesLoading] = useState(true);
  const [selectedStyle, setSelectedStyle] = useState<string | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [assetModalOpen, setAssetModalOpen] = useState(false);
  const [assetLibraryLoading, setAssetLibraryLoading] = useState(false);
  const [assetLibraryItems, setAssetLibraryItems] = useState<AssetLibraryItem[]>([]);
  const [assetLibraryPage, setAssetLibraryPage] = useState(1);
  const [assetLibraryTotal, setAssetLibraryTotal] = useState(0);
  const [importingAssetUrl, setImportingAssetUrl] = useState<string | null>(null);
  const { refreshUser, currentUser } = useAuth();
  const assetLibraryPageSize = 8;
  const previousFilesRef = useRef<UploadFile[]>([]);

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
    if (activeTab !== 'img2img' || modelList.length === 0) {
      return;
    }
    const currentModel = modelList.find((item) => item.modelId === model);
    if (currentModel?.available && currentModel.imageToImageSupported) {
      return;
    }
    const firstSupported = modelList.find((item) => item.available && item.imageToImageSupported);
    if (firstSupported) {
      setModel(firstSupported.modelId);
    }
  }, [activeTab, modelList, model]);

  const fetchAssetLibrary = useCallback(async (page: number) => {
    setAssetLibraryLoading(true);
    try {
      const res = await getImageHistory(page, assetLibraryPageSize) as unknown as {
        code: number;
        data?: { list?: AssetLibraryItem[]; total?: number };
        message?: string;
      };
      if (res.code === 200 && res.data) {
        setAssetLibraryItems(res.data.list || []);
        setAssetLibraryTotal(res.data.total || 0);
      } else {
        message.error(res.message || '获取资源库失败');
      }
    } catch (error) {
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
    return fileList.some((file) => file.url === imageUrl);
  }, [fileList]);

  const openAssetLibrary = () => {
    setAssetLibraryPage(1);
    setAssetModalOpen(true);
  };

  const handleImportAsset = (item: AssetLibraryItem) => {
    if (fileList.length >= 10) {
      message.warning('参考图片最多上传10张');
      return;
    }
    if (isAssetImported(item.imageUrl)) {
      message.info('该图片已导入');
      return;
    }
    setImportingAssetUrl(item.imageUrl);
    setFileList((prev) => [
      ...prev,
      {
        uid: `asset-${item.id}-${Date.now()}`,
        name: `asset-${item.id}.png`,
        status: 'done',
        url: item.imageUrl,
        thumbUrl: item.imageUrl,
      },
    ]);
    setImportingAssetUrl(null);
    setAssetModalOpen(false);
    message.success('已从资源库导入参考图');
  };

  const ensurePreviewFile = useCallback((file: UploadFile): UploadFile => {
    if (!file.thumbUrl && file.url) {
      return { ...file, thumbUrl: file.url };
    }
    if (!file.thumbUrl && file.originFileObj) {
      return {
        ...file,
        thumbUrl: URL.createObjectURL(file.originFileObj as Blob),
      };
    }
    return file;
  }, []);

  const releasePreviewFile = useCallback((file: UploadFile) => {
    if (file.thumbUrl?.startsWith('blob:')) {
      URL.revokeObjectURL(file.thumbUrl);
    }
  }, []);

  useEffect(() => {
    const previousFiles = previousFilesRef.current;
    const currentThumbMap = new Map(fileList.map((file) => [file.uid, file.thumbUrl]));
    previousFiles.forEach((file) => {
      const currentThumbUrl = currentThumbMap.get(file.uid);
      const previewChanged = Boolean(currentThumbUrl && currentThumbUrl !== file.thumbUrl);
      if (!currentThumbMap.has(file.uid) || previewChanged) {
        releasePreviewFile(file);
      }
    });
    previousFilesRef.current = fileList;
  }, [fileList, releasePreviewFile]);

  useEffect(() => {
    return () => {
      previousFilesRef.current.forEach(releasePreviewFile);
    };
  }, [releasePreviewFile]);

  const handleUploadChange = ({ fileList: fl }: { fileList: UploadFile[] }) => {
    if (fl.length > 10) {
      message.warning('参考图片最多上传10张');
    }
    setFileList((prev) => {
      const prevMap = new Map(prev.map((file) => [file.uid, file]));
      return fl.slice(0, 10).map((file) => {
        const previousFile = prevMap.get(file.uid);
        if (previousFile?.thumbUrl && !file.thumbUrl) {
          return {
            ...file,
            url: file.url || previousFile.url,
            thumbUrl: previousFile.thumbUrl,
          };
        }
        return ensurePreviewFile(file);
      });
    });
  };

  const handleRemoveReference = (uid: string) => {
    setFileList((prev) => {
      const target = prev.find((file) => file.uid === uid);
      if (target) {
        releasePreviewFile(target);
      }
      return prev.filter((file) => file.uid !== uid);
    });
  };

  const handleGenerate = useCallback(async () => {
    if (!prompt.trim()) {
      message.warning('请输入图片描述');
      return;
    }
    if (!currentUser?.isVip && (currentUser?.dailyUsed || 0) >= (currentUser?.dailyLimit || 10)) {
      message.error(`今日生成次数已达上限(${currentUser?.dailyLimit || 10}次)`);
      return;
    }
    setIsGenerating(true);
    setGeneratedImage(null);
    setLoading(true);

    try {
      const currentModel = modelList.find((item) => item.modelId === model);
      if (activeTab === 'img2img') {
        const uploadedImages = fileList
          .map((file) => file.originFileObj as File | undefined)
          .filter((file): file is File => Boolean(file));
        const importedImageUrls = fileList
          .map((file) => (!file.originFileObj ? file.url : undefined))
          .filter((imageUrl): imageUrl is string => Boolean(imageUrl));

        if (uploadedImages.length === 0 && importedImageUrls.length === 0) {
          message.warning('请上传至少一张参考图');
          setLoading(false);
          setIsGenerating(false);
          return;
        }
        if (currentModel && !currentModel.imageToImageSupported) {
          message.warning('当前模型暂不支持图生图，请切换模型');
          setLoading(false);
          setIsGenerating(false);
          return;
        }
      }

      const res = (activeTab === 'img2img'
        ? await img2img({
          prompt: prompt.trim(),
          style: selectedStyle || undefined,
          modelId: model || undefined,
          aspectRatio: ratio as '1:1' | '2:3' | '3:2' | '3:4' | '4:3' | '4:5' | '5:4' | '9:16' | '16:9' | '21:9',
          imageSize: quality.toUpperCase() as '1K' | '2K' | '4K',
          images: fileList
            .map((file) => file.originFileObj as File | undefined)
            .filter((file): file is File => Boolean(file)),
          imageUrls: fileList
            .map((file) => (!file.originFileObj ? file.url : undefined))
            .filter((imageUrl): imageUrl is string => Boolean(imageUrl)),
        })
        : await text2img({
          prompt: prompt.trim(),
          style: selectedStyle || undefined,
          modelId: model || undefined,
          aspectRatio: ratio as '1:1' | '2:3' | '3:2' | '3:4' | '4:3' | '4:5' | '5:4' | '9:16' | '16:9' | '21:9',
          imageSize: quality.toUpperCase() as '1K' | '2K' | '4K',
        })) as unknown as { code: number; data: GenerationResponse; message?: string };

      if (res.code === 200 && res.data) {
        const response = res.data;
        if (response.taskStatus === 'RUNNING') {
          message.info(response.message || '图片正在生成中，请稍候...');
        } else if (response.taskStatus === 'SUCCESS' && response.result) {
          setGeneratedImage(response.result);
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
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '生成失败，请重试');
      setLoading(false);
      setIsGenerating(false);
    }
  }, [activeTab, prompt, selectedStyle, model, modelList, ratio, quality, fileList, currentUser, refreshUser]);

  const throttledGenerate = useThrottle(handleGenerate, 2000);

  const handleDownload = () => {
    const imageUrl = generatedImage?.ossUrl || generatedImage?.imageUrl;
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
    const imageUrl = generatedImage?.ossUrl || generatedImage?.imageUrl;
    if (imageUrl) {
      navigator.clipboard.writeText(imageUrl).then(() => {
        message.success('链接已复制到剪贴板');
      }).catch(() => {
        message.error('复制失败');
      });
    }
  };

  const handleSelectStyle = (template: StyleTemplate) => {
    setSelectedStyle(selectedStyle === template.nameEn ? null : template.nameEn);
  };

  const isVip = !!currentUser?.isVip;

  // 构建带VIP标识的下拉选项
  const buildVipOptions = (options: { label: string; value: string; vip: boolean }[]) =>
    options.map(opt => ({
      value: opt.value,
      disabled: opt.vip && !isVip,
      label: (
        <span className="vip-option">
          {opt.label}
          {opt.vip && <Tag color="gold" className="vip-tag"><LockOutlined /> VIP</Tag>}
        </span>
      ),
    }));

  // 宽高比选项（带矩形图标 + VIP标识）
  const ratioOptions = RATIO_OPTIONS.map(opt => ({
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

  return (
    <div className="generate-page">
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
          {/* 模型选择 */}
          <div className="control-item">
            <label>模型</label>
            <Select
              value={model || undefined}
              onChange={(val) => {
                const m = modelList.find(m => m.modelId === val);
                if (m && !m.available) {
                  message.warning('该模型为VIP专属，请升级VIP');
                  return;
                }
                if (activeTab === 'img2img' && m && !m.imageToImageSupported) {
                  message.warning('该模型暂不支持图生图');
                  return;
                }
                setModel(val);
              }}
              loading={modelsLoading}
              placeholder={modelsLoading ? '加载中...' : '选择模型'}
              options={modelList.map(m => ({
                value: m.modelId,
                disabled: !m.available || (activeTab === 'img2img' && !m.imageToImageSupported),
                label: (
                  <span className="vip-option">
                    {m.displayName}
                    {m.minVipLevel > 0 && <Tag color="gold" className="vip-tag"><LockOutlined /> VIP</Tag>}
                    {activeTab === 'img2img' && !m.imageToImageSupported && (
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
            <div className="control-item">
              <div className="upload-header">
                <label>上传参考图片</label>
                <Button
                  type="default"
                  size="small"
                  icon={<FolderOpenOutlined />}
                  onClick={openAssetLibrary}
                  className="asset-import-btn"
                >
                  从资源库导入
                </Button>
              </div>
              <Dragger
                fileList={fileList}
                multiple
                maxCount={10}
                onChange={handleUploadChange}
                beforeUpload={(file) => {
                  const isImage = file.type.startsWith('image/');
                  if (!isImage) {
                    message.error('只能上传图片文件');
                    return Upload.LIST_IGNORE;
                  }
                  const isLt5M = file.size / 1024 / 1024 < 5;
                  if (!isLt5M) {
                    message.error('单张图片不能超过5MB');
                    return Upload.LIST_IGNORE;
                  }
                  return false;
                }}
                accept="image/png,image/jpeg,image/webp"
                className="ref-upload"
                showUploadList={false}
              >
                {fileList.length === 0 ? (
                  <div className="upload-placeholder">
                    <InboxOutlined />
                    <p>点击或拖拽上传参考图片，或从资源库导入</p>
                    <span>支持 PNG / JPG / WEBP，最多 10 张，单张不超过 5MB</span>
                  </div>
                ) : (
                  <div className="upload-dropzone-content">
                    <div className="upload-preview-grid">
                      {fileList.map((file) => {
                        const previewSrc = file.thumbUrl || file.url;
                        const fileName = file.name || '参考图片';
                        return (
                          <div key={file.uid} className="upload-preview-card">
                            {previewSrc ? (
                              <img
                                src={previewSrc}
                                alt={fileName}
                                className="upload-preview-image"
                              />
                            ) : (
                              <div className="upload-preview-image upload-preview-fallback">
                                <PictureOutlined />
                              </div>
                            )}
                            <button
                              type="button"
                              className="upload-preview-remove"
                              onClick={(event) => {
                                event.preventDefault();
                                event.stopPropagation();
                                handleRemoveReference(file.uid);
                              }}
                            >
                              <CloseOutlined />
                            </button>
                            <div className="upload-preview-meta">
                              <span className="upload-preview-name" title={fileName}>
                                {fileName}
                              </span>
                              <span className="upload-preview-tag">
                                {file.originFileObj ? '本地上传' : '资源库导入'}
                              </span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                    <p className="upload-helper-text">
                      {fileList.length >= 10
                        ? '已达到 10 张上限，可删除后继续添加'
                        : `继续拖拽/点击上传，或从资源库导入（${fileList.length}/10）`}
                    </p>
                  </div>
                )}
              </Dragger>
            </div>
          )}

          {/* 提示词 */}
          <div className="control-item">
            <label>提示词</label>
            <TextArea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="描述你想生成的内容..."
              rows={activeTab === 'text2img' ? 8 : 5}
              className="prompt-textarea"
              maxLength={500}
            />
            <div className="prompt-count">{prompt.length}/500</div>
          </div>

          {/* 热门风格 */}
          <div className="control-item">
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

          {/* 宽高比 */}
          <div className="control-item">
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
          <div className="control-item">
            <label>质量</label>
            <Select
              value={quality}
              onChange={(val) => {
                if (QUALITY_OPTIONS.find(q => q.value === val)?.vip && !isVip) {
                  message.warning('该质量为VIP专属，请升级VIP');
                  return;
                }
                setQuality(val);
              }}
              options={buildVipOptions(QUALITY_OPTIONS)}
              className="control-select"
              popupClassName="gen-select-dropdown"
            />
          </div>

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

        {/* 右侧预览区 */}
        <div className="preview-panel">
          <div className="preview-area">
            {(isGenerating || loading) && !generatedImage ? (
              <div className="preview-loading">
                <div className="shimmer-icon"><PictureOutlined /></div>
                <div className="shimmer-text">AI 正在创作中...</div>
                <div className="shimmer-progress"><div className="progress-bar"></div></div>
              </div>
            ) : generatedImage ? (
              <div className="preview-result">
                <img
                  src={generatedImage.ossUrl || generatedImage.imageUrl || `data:image/png;base64,${generatedImage.imageBase64}`}
                  alt="Generated"
                />
                <div className="image-toolbar">
                  <Tooltip title="下载"><Button icon={<DownloadOutlined />} onClick={handleDownload} /></Tooltip>
                  <Tooltip title="复制链接"><Button icon={<CopyOutlined />} onClick={handleCopyLink} /></Tooltip>
                  <Tooltip title="全屏"><Button icon={<ExpandOutlined />} onClick={() => message.info('开发中...')} /></Tooltip>
                  <Tooltip title="收藏"><Button icon={<HeartOutlined />} onClick={() => message.info('开发中...')} /></Tooltip>
                  <Tooltip title="重新生成"><Button icon={<ReloadOutlined />} onClick={throttledGenerate} /></Tooltip>
                </div>
                {generatedImage.revisedPrompt && (
                  <div className="revised-prompt">
                    <span className="label">AI 优化描述：</span>
                    <p>{generatedImage.revisedPrompt}</p>
                  </div>
                )}
                <div className="image-meta">
                  {generatedImage.model && <span>模型: {generatedImage.model}</span>}
                  {generatedImage.generationTimeMs && (
                    <span>耗时: {(generatedImage.generationTimeMs / 1000).toFixed(1)}s</span>
                  )}
                  {generatedImage.fromCache && <span className="cache-tag">缓存</span>}
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

      <Modal
        open={assetModalOpen}
        onCancel={() => setAssetModalOpen(false)}
        footer={null}
        title="从资源库导入"
        width={880}
        centered
        className="asset-library-modal"
      >
        <Spin spinning={assetLibraryLoading}>
          {assetLibraryItems.length > 0 ? (
            <>
              <div className="asset-library-grid">
                {assetLibraryItems.map((item) => {
                  const imported = isAssetImported(item.imageUrl);
                  return (
                    <div key={item.id} className={`asset-library-card ${imported ? 'imported' : ''}`}>
                      <img src={item.imageUrl} alt={item.prompt} className="asset-library-image" />
                      <div className="asset-library-info">
                        <p className="asset-library-prompt" title={item.prompt}>{item.prompt}</p>
                        <p className="asset-library-time">{item.createdAt}</p>
                        <Button
                          type="primary"
                          size="small"
                          block
                          disabled={imported || fileList.length >= 10}
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
  );
};

export default GeneratePage;
