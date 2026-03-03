import React, { useEffect, useState, useRef } from 'react';
import {
  Spin,
  Empty,
  DatePicker,
  Space,
  message,
  Modal,
  Input,
  Tooltip,
  Button,
} from 'antd';
import {
  DownloadOutlined,
  ExpandOutlined,
  ShareAltOutlined,
  ScissorOutlined,
  EditOutlined,
  FontSizeOutlined,
  CloseOutlined,
  CopyOutlined,
  CheckOutlined,
} from '@ant-design/icons';
import { getImageHistory } from '../../api/image';
import dayjs, { Dayjs } from 'dayjs';
import './index.css';

const { RangePicker } = DatePicker;

interface AssetItem {
  id: string;
  imageUrl: string;
  prompt: string;
  style: string;
  createdAt: string;
}

const Assets: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [data, setData] = useState<AssetItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [dateRange, setDateRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);

  // Modal states
  const [fullscreenVisible, setFullscreenVisible] = useState(false);
  const [fullscreenImage, setFullscreenImage] = useState<AssetItem | null>(null);
  const [textModalVisible, setTextModalVisible] = useState(false);
  const [textModalImage, setTextModalImage] = useState<AssetItem | null>(null);
  const [overlayText, setOverlayText] = useState('');
  const [cropModalVisible, setCropModalVisible] = useState(false);
  const [cropImage, setCropImage] = useState<AssetItem | null>(null);
  const [shareModalVisible, setShareModalVisible] = useState(false);
  const [shareImage, setShareImage] = useState<AssetItem | null>(null);
  const [copied, setCopied] = useState(false);

  const pageSize = 12;
  const containerRef = useRef<HTMLDivElement>(null);
  const observerRef = useRef<IntersectionObserver | null>(null);
  const loadMoreRef = useRef<HTMLDivElement>(null);

  const fetchAssets = async (page: number, append: boolean = false) => {
    if (page === 1) {
      setLoading(true);
    } else {
      setLoadingMore(true);
    }

    try {
      const response: any = await getImageHistory(page, pageSize);
      if (response.code === 200) {
        let list = response.data.list || [];
        const totalCount = response.data.total || 0;

        // Apply date filter on client side if set
        if (dateRange && dateRange[0] && dateRange[1]) {
          const startDate = dateRange[0].startOf('day');
          const endDate = dateRange[1].endOf('day');
          list = list.filter((item: AssetItem) => {
            const itemDate = dayjs(item.createdAt);
            return itemDate.isAfter(startDate) && itemDate.isBefore(endDate);
          });
        }

        if (append) {
          setData(prev => [...prev, ...list]);
        } else {
          setData(list);
        }
        setTotal(totalCount);
        setHasMore(page * pageSize < totalCount);
      }
    } catch (error) {
      message.error('获取资产列表失败');
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  };

  useEffect(() => {
    setCurrent(1);
    setData([]);
    fetchAssets(1, false);
  }, [dateRange]);

  // Setup Intersection Observer for infinite scroll
  useEffect(() => {
    if (observerRef.current) {
      observerRef.current.disconnect();
    }

    observerRef.current = new IntersectionObserver(
      (entries) => {
        const first = entries[0];
        if (first.isIntersecting && hasMore && !loading && !loadingMore) {
          const nextPage = current + 1;
          setCurrent(nextPage);
          fetchAssets(nextPage, true);
        }
      },
      { threshold: 0.1 }
    );

    if (loadMoreRef.current) {
      observerRef.current.observe(loadMoreRef.current);
    }

    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect();
      }
    };
  }, [hasMore, loading, loadingMore, current]);

  const handleDownload = async (item: AssetItem) => {
    try {
      const response = await fetch(item.imageUrl);
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `pixel-ai-${item.id}-${Date.now()}.png`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      message.success('下载成功');
    } catch (error) {
      // Fallback for cross-origin images
      const link = document.createElement('a');
      link.href = item.imageUrl;
      link.download = `pixel-ai-${item.id}-${Date.now()}.png`;
      link.target = '_blank';
      link.click();
    }
  };

  const handleFullscreen = (item: AssetItem) => {
    setFullscreenImage(item);
    setFullscreenVisible(true);
  };

  const handleShare = (item: AssetItem) => {
    setShareImage(item);
    setShareModalVisible(true);
    setCopied(false);
  };

  const handleCopyLink = async () => {
    if (shareImage) {
      try {
        await navigator.clipboard.writeText(shareImage.imageUrl);
        setCopied(true);
        message.success('链接已复制');
        setTimeout(() => setCopied(false), 2000);
      } catch (error) {
        message.error('复制失败');
      }
    }
  };

  const handleCrop = (item: AssetItem) => {
    setCropImage(item);
    setCropModalVisible(true);
  };

  const handleEdit = (_item: AssetItem) => {
    message.info('编辑功能即将上线');
  };

  const handleAddText = (item: AssetItem) => {
    setTextModalImage(item);
    setOverlayText('');
    setTextModalVisible(true);
  };

  const handleDateRangeChange = (dates: [Dayjs | null, Dayjs | null] | null) => {
    setDateRange(dates);
  };

  const renderAssetCard = (item: AssetItem) => (
    <div key={item.id} className="asset-card">
      <div className="asset-image-wrapper">
        <img
          src={item.imageUrl}
          alt={item.prompt}
          className="asset-image"
          loading="lazy"
        />
        <div className="asset-overlay">
          <div className="asset-actions">
            <Tooltip title="编辑">
              <button className="action-btn" onClick={() => handleEdit(item)}>
                <EditOutlined />
              </button>
            </Tooltip>
            <Tooltip title="全屏">
              <button className="action-btn" onClick={() => handleFullscreen(item)}>
                <ExpandOutlined />
              </button>
            </Tooltip>
            <Tooltip title="分享">
              <button className="action-btn" onClick={() => handleShare(item)}>
                <ShareAltOutlined />
              </button>
            </Tooltip>
            <Tooltip title="裁剪">
              <button className="action-btn" onClick={() => handleCrop(item)}>
                <ScissorOutlined />
              </button>
            </Tooltip>
            <Tooltip title="下载">
              <button className="action-btn" onClick={() => handleDownload(item)}>
                <DownloadOutlined />
              </button>
            </Tooltip>
            <Tooltip title="添加文字">
              <button className="action-btn" onClick={() => handleAddText(item)}>
                <FontSizeOutlined />
              </button>
            </Tooltip>
          </div>
        </div>
      </div>
      <div className="asset-info">
        <p className="asset-prompt" title={item.prompt}>
          {item.prompt || '无提示词'}
        </p>
        <p className="asset-time">
          {dayjs(item.createdAt).format('YYYY-MM-DD HH:mm')}
        </p>
      </div>
    </div>
  );

  return (
    <div className="assets-container" ref={containerRef}>
      {/* Header */}
      <div className="assets-header">
        <div className="assets-title">
          <h2>我的资产</h2>
          <span className="assets-count">共 {total} 张图片</span>
        </div>
        <div className="assets-filters">
          <Space>
            <RangePicker
              onChange={handleDateRangeChange}
              placeholder={['开始日期', '结束日期']}
              allowClear
              className="date-picker"
            />
          </Space>
        </div>
      </div>

      {/* Content */}
      <Spin spinning={loading && current === 1}>
        {data.length > 0 ? (
          <>
            <div className="assets-grid">
              {data.map(renderAssetCard)}
            </div>

            {/* Load more trigger */}
            <div ref={loadMoreRef} className="load-more-trigger">
              {loadingMore && (
                <div className="loading-more">
                  <Spin size="small" />
                  <span>加载更多...</span>
                </div>
              )}
              {!hasMore && data.length > 0 && (
                <div className="no-more">已经到底啦 ~</div>
              )}
            </div>
          </>
        ) : (
          !loading && <Empty description="暂无资产" className="empty-state" />
        )}
      </Spin>

      {/* Fullscreen Modal */}
      <Modal
        open={fullscreenVisible}
        onCancel={() => setFullscreenVisible(false)}
        footer={null}
        width="90vw"
        className="fullscreen-modal"
        centered
        closeIcon={<CloseOutlined className="close-icon" />}
      >
        {fullscreenImage && (
          <div className="fullscreen-content">
            <img
              src={fullscreenImage.imageUrl}
              alt={fullscreenImage.prompt}
              className="fullscreen-image"
            />
            <div className="fullscreen-info">
              <p className="fullscreen-prompt">{fullscreenImage.prompt}</p>
              <p className="fullscreen-time">
                {dayjs(fullscreenImage.createdAt).format('YYYY-MM-DD HH:mm:ss')}
              </p>
            </div>
          </div>
        )}
      </Modal>

      {/* Share Modal */}
      <Modal
        open={shareModalVisible}
        onCancel={() => setShareModalVisible(false)}
        footer={null}
        title="分享图片"
        className="share-modal"
        centered
      >
        {shareImage && (
          <div className="share-content">
            <img
              src={shareImage.imageUrl}
              alt={shareImage.prompt}
              className="share-preview"
            />
            <div className="share-link-wrapper">
              <Input
                value={shareImage.imageUrl}
                readOnly
                className="share-link-input"
              />
              <Button
                type="primary"
                icon={copied ? <CheckOutlined /> : <CopyOutlined />}
                onClick={handleCopyLink}
                className="copy-btn"
              >
                {copied ? '已复制' : '复制链接'}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* Crop Modal */}
      <Modal
        open={cropModalVisible}
        onCancel={() => setCropModalVisible(false)}
        footer={null}
        title="裁剪图片"
        className="crop-modal"
        centered
        width={800}
      >
        {cropImage && (
          <div className="crop-content">
            <img
              src={cropImage.imageUrl}
              alt={cropImage.prompt}
              className="crop-preview"
            />
            <div className="crop-actions">
              <Space>
                <Button onClick={() => message.info('1:1 裁剪即将上线')}>1:1</Button>
                <Button onClick={() => message.info('4:3 裁剪即将上线')}>4:3</Button>
                <Button onClick={() => message.info('16:9 裁剪即将上线')}>16:9</Button>
                <Button onClick={() => message.info('自由裁剪即将上线')}>自由裁剪</Button>
              </Space>
            </div>
          </div>
        )}
      </Modal>

      {/* Add Text Modal */}
      <Modal
        open={textModalVisible}
        onCancel={() => setTextModalVisible(false)}
        title="添加文字"
        className="text-modal"
        centered
        width={800}
        footer={[
          <Button key="cancel" onClick={() => setTextModalVisible(false)}>
            取消
          </Button>,
          <Button
            key="save"
            type="primary"
            onClick={() => {
              message.success('文字添加功能即将上线');
              setTextModalVisible(false);
            }}
          >
            保存
          </Button>,
        ]}
      >
        {textModalImage && (
          <div className="text-content">
            <div className="text-preview-wrapper">
              <img
                src={textModalImage.imageUrl}
                alt={textModalImage.prompt}
                className="text-preview"
              />
              {overlayText && (
                <div className="text-overlay-preview">{overlayText}</div>
              )}
            </div>
            <div className="text-input-wrapper">
              <Input.TextArea
                value={overlayText}
                onChange={(e) => setOverlayText(e.target.value)}
                placeholder="输入要添加的文字..."
                rows={3}
                className="text-input"
              />
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default Assets;
