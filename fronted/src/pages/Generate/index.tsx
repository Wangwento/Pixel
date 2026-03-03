import React, { useState, useEffect } from 'react';
import {
  Card,
  Input,
  Button,
  Upload,
  message,
  Spin,
  Image,
  Tabs,
  Select,
  Row,
  Col,
  Typography,
} from 'antd';
import {
  PictureOutlined,
  UploadOutlined,
  DownloadOutlined,
  SendOutlined,
} from '@ant-design/icons';
import type { UploadFile, UploadProps } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { text2img, img2img } from '../../api/image';
import type { GenerationResult, Text2ImgRequest, Img2ImgRequest } from '../../api/image';
import './index.css';

const { TextArea } = Input;
const { Title, Paragraph } = Typography;

const styleOptions = [
  { value: 'realistic', label: '写实风格' },
  { value: 'anime', label: '动漫风格' },
  { value: 'cyberpunk', label: '赛博朋克' },
  { value: 'watercolor', label: '水彩风格' },
  { value: 'oil_painting', label: '油画风格' },
  { value: 'sketch', label: '素描风格' },
  { value: 'chinese', label: '国潮风格' },
  { value: '3d', label: '3D渲染' },
];

const Generate: React.FC = () => {
  const [searchParams] = useSearchParams();
  const initialPrompt = searchParams.get('prompt') || '';

  const [prompt, setPrompt] = useState(initialPrompt);
  const [style, setStyle] = useState('realistic');
  const [loading, setLoading] = useState(false);
  const [generatedImage, setGeneratedImage] = useState<string | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [activeTab, setActiveTab] = useState('text2img');

  // 如果有初始prompt，自动开始生成
  useEffect(() => {
    if (initialPrompt) {
      handleTextToImage();
    }
  }, []);

  // 文生图
  const handleTextToImage = async () => {
    if (!prompt.trim()) {
      message.warning('请输入提示词描述');
      return;
    }

    setLoading(true);
    try {
      const requestParams: Text2ImgRequest = {
        prompt: prompt.trim(),
        style: style,
      };

      const response = await text2img(requestParams) as unknown as { code: number; data: GenerationResult; message?: string };
      if (response.code === 200 && response.data) {
        setGeneratedImage(response.data.ossUrl || response.data.imageUrl || '');
        message.success('图片生成成功！');
      } else {
        message.error(response.message || '生成失败');
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '生成失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  // 图生图
  const handleImageToImage = async () => {
    if (!prompt.trim()) {
      message.warning('请输入提示词描述');
      return;
    }
    if (fileList.length === 0) {
      message.warning('请上传参考图片');
      return;
    }

    setLoading(true);
    try {
      // 将图片转为 base64
      const file = fileList[0].originFileObj as File;
      const base64 = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve((reader.result as string).split(',')[1]);
        reader.onerror = reject;
        reader.readAsDataURL(file);
      });

      const requestParams: Img2ImgRequest = {
        prompt: prompt.trim(),
        style: style,
        sourceImageBase64: base64,
      };

      const response = await img2img(requestParams) as unknown as { code: number; data: GenerationResult; message?: string };
      if (response.code === 200 && response.data) {
        setGeneratedImage(response.data.ossUrl || response.data.imageUrl || '');
        message.success('图片生成成功！');
      } else {
        message.error(response.message || '生成失败');
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '生成失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  // 下载图片
  const handleDownload = () => {
    if (!generatedImage) return;
    const link = document.createElement('a');
    link.href = generatedImage;
    link.download = `pixel-ai-${Date.now()}.png`;
    link.click();
  };

  const uploadProps: UploadProps = {
    beforeUpload: (file) => {
      const isImage = file.type.startsWith('image/');
      if (!isImage) {
        message.error('只能上传图片文件！');
        return false;
      }
      const isLt5M = file.size / 1024 / 1024 < 5;
      if (!isLt5M) {
        message.error('图片大小不能超过5MB！');
        return false;
      }
      return false;
    },
    fileList,
    onChange: ({ fileList }) => setFileList(fileList.slice(-1)),
    listType: 'picture-card',
    maxCount: 1,
  };

  const tabItems = [
    {
      key: 'text2img',
      label: '文生图',
      children: (
        <div className="generate-form">
          <div className="form-item">
            <label>提示词描述</label>
            <TextArea
              placeholder="描述你想要生成的头像，例如：一个戴着墨镜的酷炫年轻人，赛博朋克风格，霓虹灯背景"
              rows={4}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              maxLength={500}
              showCount
            />
          </div>
          <div className="form-item">
            <label>风格选择</label>
            <Select
              value={style}
              onChange={setStyle}
              options={styleOptions}
              style={{ width: '100%' }}
            />
          </div>
          <Button
            type="primary"
            icon={<SendOutlined />}
            size="large"
            onClick={handleTextToImage}
            loading={loading}
            block
          >
            生成头像
          </Button>
        </div>
      ),
    },
    {
      key: 'img2img',
      label: '图生图',
      children: (
        <div className="generate-form">
          <div className="form-item">
            <label>上传参考图片</label>
            <Upload {...uploadProps}>
              {fileList.length === 0 && (
                <div>
                  <UploadOutlined />
                  <div style={{ marginTop: 8 }}>点击上传</div>
                </div>
              )}
            </Upload>
          </div>
          <div className="form-item">
            <label>描述你想要的效果</label>
            <TextArea
              placeholder="描述你想要的变化效果，例如：将这张照片转换成动漫风格，保留人物特征"
              rows={4}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              maxLength={500}
              showCount
            />
          </div>
          <div className="form-item">
            <label>风格选择</label>
            <Select
              value={style}
              onChange={setStyle}
              options={styleOptions}
              style={{ width: '100%' }}
            />
          </div>
          <Button
            type="primary"
            icon={<SendOutlined />}
            size="large"
            onClick={handleImageToImage}
            loading={loading}
            block
          >
            生成头像
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="generate-container">
      <div className="page-header">
        <Title level={2}>
          <PictureOutlined /> AI头像生成
        </Title>
        <Paragraph type="secondary">
          输入描述或上传照片，AI帮你生成专属头像
        </Paragraph>
      </div>

      <Row gutter={24}>
        <Col xs={24} lg={12}>
          <Card title="生成设置" className="input-card">
            <Tabs
              activeKey={activeTab}
              onChange={setActiveTab}
              items={tabItems}
            />
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card
            title="生成结果"
            className="result-card"
            extra={
              generatedImage && (
                <Button
                  type="primary"
                  icon={<DownloadOutlined />}
                  onClick={handleDownload}
                >
                  下载
                </Button>
              )
            }
          >
            <div className="result-content">
              {loading ? (
                <div className="loading-container">
                  <Spin size="large" tip="AI正在生成中，请稍候..." />
                </div>
              ) : generatedImage ? (
                <Image
                  src={generatedImage}
                  alt="生成的头像"
                  className="generated-image"
                />
              ) : (
                <div className="placeholder">
                  <PictureOutlined style={{ fontSize: 64, color: '#d9d9d9' }} />
                  <p>生成的头像将显示在这里</p>
                </div>
              )}
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Generate;