import React, { useState, useEffect } from 'react';
import { Input, Button, Card, Row, Col, Tabs, message, Spin, Tooltip } from 'antd';
import {
  PictureOutlined,
  UploadOutlined,
  ThunderboltOutlined,
  HistoryOutlined,
  DownloadOutlined,
  ShareAltOutlined,
  EditOutlined,
  ScissorOutlined,
  ReloadOutlined,
  ExpandOutlined,
  HeartOutlined,
  CopyOutlined,
} from '@ant-design/icons';
import { getStyleList } from '../../../api/style';
import type { StyleTemplate } from '../../../api/style';
import { text2img } from '../../../api/image';
import type { GenerationResult } from '../../../api/image';
import { useAuth } from '../../../contexts/AuthContext';
import './index.css';

const { TextArea } = Input;

const GeneratePage: React.FC = () => {
  const [prompt, setPrompt] = useState('');
  const [loading, setLoading] = useState(false);
  const [generatedImage, setGeneratedImage] = useState<GenerationResult | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [styleTemplates, setStyleTemplates] = useState<StyleTemplate[]>([]);
  const [stylesLoading, setStylesLoading] = useState(true);
  const [selectedStyle, setSelectedStyle] = useState<string | null>(null);
  const { refreshUser, currentUser } = useAuth();

  // 获取风格模板（使用缓存避免重复请求）
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
        if (isMounted) {
          setStylesLoading(false);
        }
      }
    };

    fetchStyles();

    return () => {
      isMounted = false;
    };
  }, []); // 空依赖数组，只在组件挂载时执行一次

  const handleGenerate = async () => {
    if (!prompt.trim()) {
      message.warning('请输入图片描述');
      return;
    }

    // 检查每日生成限制（VIP无限制）
    if (!currentUser?.isVip && (currentUser?.dailyUsed || 0) >= (currentUser?.dailyLimit || 10)) {
      message.error(`今日生成次数已达上限(${currentUser?.dailyLimit || 10}次)，请明天再来或升级VIP`);
      return;
    }

    // 立即显示模糊占位图
    setIsGenerating(true);
    setGeneratedImage(null);
    setLoading(true);

    try {
      const res = await text2img({
        prompt: prompt.trim(),
        style: selectedStyle || undefined,
      }) as unknown as { code: number; data: GenerationResult; message?: string };

      if (res.code === 200 && res.data) {
        setGeneratedImage(res.data);
        message.success(`生成成功！耗时 ${((res.data.generationTimeMs || 0) / 1000).toFixed(1)}s`);
        // 刷新用户信息，更新额度
        refreshUser();
      } else {
        message.error(res.message || '生成失败');
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '生成失败，请重试');
    } finally {
      setLoading(false);
      setIsGenerating(false);
    }
  };

  // 下载图片
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

  // 分享图片 (预留)
  const handleShare = () => {
    message.info('分享功能开发中...');
  };

  // 编辑文本 (预留)
  const handleEditPrompt = () => {
    message.info('编辑功能开发中...');
  };

  // 裁剪图片 (预留)
  const handleCrop = () => {
    message.info('裁剪功能开发中...');
  };

  // 重新生成
  const handleRegenerate = () => {
    handleGenerate();
  };

  // 全屏预览 (预留)
  const handleFullscreen = () => {
    message.info('全屏预览功能开发中...');
  };

  // 收藏 (预留)
  const handleFavorite = () => {
    message.info('收藏功能开发中...');
  };

  // 复制图片链接
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

  // 选择风格
  const handleSelectStyle = (template: StyleTemplate) => {
    if (selectedStyle === template.nameEn) {
      setSelectedStyle(null);
    } else {
      setSelectedStyle(template.nameEn);
    }
  };

  return (
    <div className="generate-page">
      <div className="generate-header">
        <h1>
          <PictureOutlined />
          AI 图片生成
        </h1>
        <p>输入描述，让 AI 为你创作独一无二的图片</p>
        {/* 显示今日生成次数 */}
        {!currentUser?.isVip && (
          <div style={{ marginTop: '8px', fontSize: '14px', color: 'rgba(255, 255, 255, 0.6)' }}>
            今日已生成: {currentUser?.dailyUsed || 0}/{currentUser?.dailyLimit || 10} 次
            {(currentUser?.dailyUsed || 0) >= (currentUser?.dailyLimit || 10) && (
              <span style={{ color: '#ff4d4f', marginLeft: '8px' }}>
                (已达上限，请明天再来或升级VIP)
              </span>
            )}
          </div>
        )}
      </div>

      <div className="generate-main">
        <Row gutter={24}>
          {/* 左侧输入区 */}
          <Col xs={24} lg={12}>
            <Card className="input-card">
              <Tabs
                items={[
                  {
                    key: 'text',
                    label: (
                      <span>
                        <ThunderboltOutlined />
                        文生图
                      </span>
                    ),
                    children: (
                      <div className="input-content">
                        <TextArea
                          value={prompt}
                          onChange={(e) => setPrompt(e.target.value)}
                          placeholder="描述你想要生成的图片，例如：一只可爱的橘猫在阳光下打盹，日系插画风格..."
                          rows={6}
                          className="prompt-textarea"
                          maxLength={500}
                        />
                        <div className="prompt-count">{prompt.length}/500</div>

                        <div className="template-section">
                          <h4>热门风格</h4>
                          {stylesLoading ? (
                            <div className="styles-loading">
                              <Spin size="small" />
                              <span>加载中...</span>
                            </div>
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

                        <Button
                          type="primary"
                          size="large"
                          block
                          loading={loading}
                          onClick={handleGenerate}
                          className="generate-btn"
                        >
                          {loading ? '生成中...' : '开始生成'}
                        </Button>
                      </div>
                    ),
                  },
                  {
                    key: 'image',
                    label: (
                      <span>
                        <UploadOutlined />
                        图生图
                      </span>
                    ),
                    children: (
                      <div className="input-content">
                        <div className="upload-area">
                          <UploadOutlined className="upload-icon" />
                          <p>点击或拖拽上传参考图片</p>
                          <span>支持 JPG、PNG 格式</span>
                        </div>
                        <TextArea
                          placeholder="描述你想要的变化..."
                          rows={4}
                          className="prompt-textarea"
                        />
                        <Button
                          type="primary"
                          size="large"
                          block
                          className="generate-btn"
                        >
                          开始生成
                        </Button>
                      </div>
                    ),
                  },
                ]}
              />
            </Card>
          </Col>

          {/* 右侧结果区 */}
          <Col xs={24} lg={12}>
            <Card className="result-card">
              <div className="result-header">
                <h3>生成结果</h3>
                <Button type="link" icon={<HistoryOutlined />}>
                  历史记录
                </Button>
              </div>
              <div className="result-content">
                {(isGenerating || loading) && !generatedImage ? (
                  <div className="result-image-wrapper">
                    <div className="image-placeholder">
                      <div className="placeholder-shimmer">
                        <div className="shimmer-icon">
                          <PictureOutlined />
                        </div>
                        <div className="shimmer-text">AI 正在创作中...</div>
                        <div className="shimmer-progress">
                          <div className="progress-bar"></div>
                        </div>
                      </div>
                    </div>
                  </div>
                ) : generatedImage ? (
                  <div className="result-image-wrapper">
                    <div className="generated-image">
                      <img
                        src={generatedImage.ossUrl || generatedImage.imageUrl || `data:image/png;base64,${generatedImage.imageBase64}`}
                        alt="Generated"
                      />
                      {/* 图片工具栏 */}
                      <div className="image-toolbar">
                        <Tooltip title="下载">
                          <Button icon={<DownloadOutlined />} onClick={handleDownload} />
                        </Tooltip>
                        <Tooltip title="分享">
                          <Button icon={<ShareAltOutlined />} onClick={handleShare} />
                        </Tooltip>
                        <Tooltip title="复制链接">
                          <Button icon={<CopyOutlined />} onClick={handleCopyLink} />
                        </Tooltip>
                        <Tooltip title="全屏预览">
                          <Button icon={<ExpandOutlined />} onClick={handleFullscreen} />
                        </Tooltip>
                        <Tooltip title="收藏">
                          <Button icon={<HeartOutlined />} onClick={handleFavorite} />
                        </Tooltip>
                        <div className="toolbar-divider"></div>
                        <Tooltip title="编辑描述">
                          <Button icon={<EditOutlined />} onClick={handleEditPrompt} />
                        </Tooltip>
                        <Tooltip title="裁剪">
                          <Button icon={<ScissorOutlined />} onClick={handleCrop} />
                        </Tooltip>
                        <Tooltip title="重新生成">
                          <Button icon={<ReloadOutlined />} onClick={handleRegenerate} />
                        </Tooltip>
                      </div>
                    </div>
                    {/* 图片信息区 */}
                    <div className="image-info-section">
                      {generatedImage.revisedPrompt && (
                        <div className="revised-prompt">
                          <span className="label">AI 优化后的描述：</span>
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
                  </div>
                ) : (
                  <div className="result-placeholder">
                    <PictureOutlined />
                    <p>生成的图片将在这里显示</p>
                  </div>
                )}
              </div>
            </Card>
          </Col>
        </Row>
      </div>
    </div>
  );
};

export default GeneratePage;
