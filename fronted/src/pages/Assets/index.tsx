import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ConfigProvider,
  Spin,
  Empty,
  DatePicker,
  Space,
  message,
  Modal,
  Input,
  Tooltip,
  Button,
  Pagination,
  TreeSelect,
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
  FolderOpenOutlined,
  FolderOutlined,
  PlusOutlined,
  HomeOutlined,
  SearchOutlined,
  DeleteOutlined,
  DownOutlined,
  FireOutlined,
  PictureOutlined,
  VideoCameraOutlined,
  CustomerServiceOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons';
import {
  createAssetFolder,
  deleteAssetFolder,
  deleteAssets,
  getAssetFolders,
  getAssets,
  moveAssets,
  renameAssetFolder,
  updateAssetTitle,
  submitToHot,
} from '../../api/image';
import type { AssetFolder, ImageAsset } from '../../api/image';
import {
  getVideoAssets,
  deleteVideoAssets,
  moveVideoAssets,
  updateVideoAssetTitle,
} from '../../api/video';
import type { VideoAsset } from '../../api/video';
import {
  getAudioAssets,
  deleteAudioAssets,
  moveAudioAssets,
  updateAudioAssetTitle,
} from '../../api/audio';
import type { AudioAsset } from '../../api/audio';
import dayjs, { Dayjs } from 'dayjs';
import './index.css';

const { RangePicker } = DatePicker;

const ROOT_FOLDER_ID = 0;
type ThemeMode = 'light' | 'dark';

type ApiResponse<T> = {
  code: number;
  data?: T;
  message?: string;
};

type FolderTreeNode = AssetFolder & {
  depth: number;
  children: FolderTreeNode[];
};

type FolderTreeSelectNode = {
  title: string;
  value: number;
  key: number;
  children?: FolderTreeSelectNode[];
};

type FullscreenAsset =
  | { type: 'image'; item: ImageAsset }
  | { type: 'video'; item: VideoAsset }
  | { type: 'audio'; item: AudioAsset };

const buildFolderTree = (folders: AssetFolder[]): FolderTreeNode[] => {
  const folderMap = new Map<number, AssetFolder>();
  const childrenMap = new Map<number, AssetFolder[]>();

  folders.forEach(folder => {
    folderMap.set(folder.id, folder);
  });

  folders.forEach(folder => {
    const parentId = folder.parentId && folderMap.has(folder.parentId)
      ? folder.parentId
      : ROOT_FOLDER_ID;
    const siblings = childrenMap.get(parentId) || [];
    siblings.push(folder);
    childrenMap.set(parentId, siblings);
  });

  const buildNodes = (parentId: number, depth: number): FolderTreeNode[] => {
    return (childrenMap.get(parentId) || []).map(folder => ({
      ...folder,
      depth,
      children: buildNodes(folder.id, depth + 1),
    }));
  };

  return buildNodes(ROOT_FOLDER_ID, 0);
};

const buildFolderPathMap = (nodes: FolderTreeNode[]) => {
  const pathMap = new Map<number, string>();

  const walk = (items: FolderTreeNode[], parentSegments: string[]) => {
    items.forEach(item => {
      const segments = [...parentSegments, item.folderName];
      pathMap.set(item.id, segments.join(' / '));
      if (item.children.length > 0) {
        walk(item.children, segments);
      }
    });
  };

  walk(nodes, []);
  return pathMap;
};

const buildFolderTreeSelectData = (nodes: FolderTreeNode[]): FolderTreeSelectNode[] => {
  return nodes.map(node => ({
    title: node.folderName,
    value: node.id,
    key: node.id,
    children: buildFolderTreeSelectData(node.children),
  }));
};

const buildPreviewUrl = (imageUrl: string, size: number = 720) => {
  if (!imageUrl || !imageUrl.includes('aliyuncs.com') || imageUrl.includes('x-oss-process=')) {
    return imageUrl;
  }
  const separator = imageUrl.includes('?') ? '&' : '?';
  return `${imageUrl}${separator}x-oss-process=image/resize,m_fill,w_${size},h_${size}/quality,q_85`;
};

const Assets: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [foldersLoading, setFoldersLoading] = useState(false);
  const [assets, setAssets] = useState<ImageAsset[]>([]);
  const [folders, setFolders] = useState<AssetFolder[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [dateRange, setDateRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [selectedFolderId, setSelectedFolderId] = useState<number | null>(null);
  const [keyword, setKeyword] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [folderPopoverOpen, setFolderPopoverOpen] = useState(false);
  const [manageMode, setManageMode] = useState(false);
  const [selectedAssetIds, setSelectedAssetIds] = useState<number[]>([]);
  const [deleteSubmitting, setDeleteSubmitting] = useState(false);
  const [themeMode, setThemeMode] = useState<ThemeMode>(() => {
    if (typeof document !== 'undefined') {
      return document.body.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
    }
    if (typeof localStorage !== 'undefined') {
      return localStorage.getItem('dashboard-theme') === 'light' ? 'light' : 'dark';
    }
    return 'dark';
  });

  const [fullscreenVisible, setFullscreenVisible] = useState(false);
  const [fullscreenAsset, setFullscreenAsset] = useState<FullscreenAsset | null>(null);
  const [textModalVisible, setTextModalVisible] = useState(false);
  const [textModalImage, setTextModalImage] = useState<ImageAsset | null>(null);
  const [overlayText, setOverlayText] = useState('');
  const [cropModalVisible, setCropModalVisible] = useState(false);
  const [cropImage, setCropImage] = useState<ImageAsset | null>(null);
  const [shareModalVisible, setShareModalVisible] = useState(false);
  const [assetType, setAssetType] = useState<'image' | 'video' | 'audio'>('image');
  const [shareImage, setShareImage] = useState<ImageAsset | null>(null);
  const [copied, setCopied] = useState(false);
  const [previewFallbackMap, setPreviewFallbackMap] = useState<Record<string, boolean>>({});

  const [folderModalOpen, setFolderModalOpen] = useState(false);
  const [folderModalMode, setFolderModalMode] = useState<'create' | 'rename'>('create');
  const [editingFolder, setEditingFolder] = useState<AssetFolder | null>(null);
  const [folderNameInput, setFolderNameInput] = useState('');
  const [folderParentId, setFolderParentId] = useState<number>(ROOT_FOLDER_ID);
  const [folderSubmitting, setFolderSubmitting] = useState(false);

  const [titleModalOpen, setTitleModalOpen] = useState(false);
  const [editingAsset, setEditingAsset] = useState<ImageAsset | null>(null);
  const [titleInput, setTitleInput] = useState('');
  const [titleSubmitting, setTitleSubmitting] = useState(false);

  const [moveModalOpen, setMoveModalOpen] = useState(false);
  const [moveTargetFolderId, setMoveTargetFolderId] = useState<number>(ROOT_FOLDER_ID);
  const [moveSubmitting, setMoveSubmitting] = useState(false);
  const [movingAssetIds, setMovingAssetIds] = useState<number[]>([]);
  const [expandedFolderIds, setExpandedFolderIds] = useState<number[]>([]);

  const [hotSubmitModalOpen, setHotSubmitModalOpen] = useState(false);
  const [hotSubmitAsset, setHotSubmitAsset] = useState<ImageAsset | null>(null);
  const [hotSubmitDesc, setHotSubmitDesc] = useState('');
  const [hotSubmitting, setHotSubmitting] = useState(false);

  const pageSize = 24;
  const containerRef = useRef<HTMLDivElement>(null);
  const folderSwitcherRef = useRef<HTMLDivElement>(null);
  const folderLookup = useMemo(() => {
    return new Map(folders.map(folder => [folder.id, folder]));
  }, [folders]);

  const folderTree = useMemo(() => buildFolderTree(folders), [folders]);

  const folderPathMap = useMemo(() => buildFolderPathMap(folderTree), [folderTree]);

  const folderTreeSelectData = useMemo<FolderTreeSelectNode[]>(() => {
    return [
      {
        title: '根目录',
        value: ROOT_FOLDER_ID,
        key: ROOT_FOLDER_ID,
        children: buildFolderTreeSelectData(folderTree),
      },
    ];
  }, [folderTree]);

  const assetFormTheme = useMemo(() => {
    const isLight = themeMode === 'light';
    return {
      token: {
        colorPrimary: '#667eea',
        colorPrimaryHover: '#5a6fd6',
        colorText: isLight ? '#1a202c' : 'rgba(255,255,255,0.88)',
        colorTextHeading: isLight ? '#1a202c' : '#ffffff',
        colorTextPlaceholder: isLight ? 'rgba(0,0,0,0.35)' : 'rgba(255,255,255,0.42)',
        colorBgElevated: isLight ? '#ffffff' : '#1a1a2e',
        colorBgContainer: isLight ? '#f7f8fa' : 'rgba(255,255,255,0.08)',
        colorBorder: isLight ? '#d9d9d9' : 'rgba(255,255,255,0.15)',
        boxShadowSecondary: isLight ? '0 18px 40px rgba(0, 0, 0, 0.1)' : '0 18px 40px rgba(0, 0, 0, 0.35)',
      },
      components: {
        Modal: {
          contentBg: isLight ? '#ffffff' : '#1a1a2e',
          headerBg: 'transparent',
          titleColor: isLight ? '#1a202c' : '#ffffff',
        },
        Input: {
          activeBg: isLight ? '#f7f8fa' : 'rgba(255,255,255,0.08)',
          hoverBg: isLight ? '#f7f8fa' : 'rgba(255,255,255,0.08)',
          activeBorderColor: '#667eea',
          hoverBorderColor: isLight ? '#d9d9d9' : 'rgba(255,255,255,0.15)',
          activeShadow: '0 0 0 2px rgba(102, 126, 234, 0.18)',
        },
        Select: {
          selectorBg: isLight ? '#f7f8fa' : 'rgba(255,255,255,0.08)',
          optionSelectedBg: isLight ? 'rgba(76, 81, 191, 0.1)' : 'rgba(102, 126, 234, 0.18)',
          optionActiveBg: isLight ? '#f7f8fa' : 'rgba(255,255,255,0.06)',
          optionSelectedColor: isLight ? '#1a202c' : '#ffffff',
          activeBorderColor: '#667eea',
          hoverBorderColor: isLight ? '#d9d9d9' : 'rgba(255,255,255,0.15)',
          activeOutlineColor: 'rgba(102, 126, 234, 0.18)',
          clearBg: isLight ? '#f7f8fa' : '#1a1a2e',
        },
        Button: {
          defaultBg: isLight ? '#f7f8fa' : 'rgba(255,255,255,0.08)',
          defaultColor: isLight ? '#1a202c' : 'rgba(255,255,255,0.88)',
          defaultBorderColor: isLight ? '#d9d9d9' : 'rgba(255,255,255,0.15)',
          defaultHoverBg: isLight ? '#eef2ff' : 'rgba(255,255,255,0.12)',
          defaultHoverColor: isLight ? '#1a202c' : '#ffffff',
          defaultHoverBorderColor: '#667eea',
          primaryColor: '#ffffff',
          primaryShadow: 'none',
        },
      },
    };
  }, [themeMode]);

  const shareModalStyles = useMemo(() => {
    const isLight = themeMode === 'light';
    return {
      content: {
        background: isLight
          ? 'linear-gradient(180deg, #ffffff 0%, #f8f9fb 100%)'
          : 'linear-gradient(180deg, #171b28 0%, #111522 100%)',
        border: isLight ? '1px solid #e8ecf1' : '1px solid rgba(255, 255, 255, 0.08)',
        borderRadius: 24,
        boxShadow: isLight
          ? '0 24px 64px rgba(15, 23, 42, 0.12)'
          : '0 24px 64px rgba(0, 0, 0, 0.35)',
        overflow: 'hidden',
      },
      header: {
        background: 'transparent',
        borderBottom: isLight ? '1px solid #e8ecf1' : '1px solid rgba(255, 255, 255, 0.08)',
        padding: '22px 24px 16px',
      },
      body: {
        background: 'transparent',
        color: isLight ? '#1a202c' : 'rgba(255, 255, 255, 0.9)',
        padding: '20px 24px 24px',
      },
      mask: {
        backdropFilter: 'blur(4px)',
      },
    };
  }, [themeMode]);

  const assetTypeLabel = assetType === 'image' ? '图片' : assetType === 'video' ? '视频' : '音频';

  const currentFolderName = useMemo(() => {
    if (selectedFolderId === null) {
      return `全部${assetTypeLabel}`;
    }
    if (selectedFolderId === ROOT_FOLDER_ID) {
      return '根目录';
    }
    return folderPathMap.get(selectedFolderId) || '未知文件夹';
  }, [folderPathMap, selectedFolderId, assetTypeLabel]);

  const allCurrentPageSelected = useMemo(() => {
    return assets.length > 0 && assets.every(asset => selectedAssetIds.includes(asset.id));
  }, [assets, selectedAssetIds]);

  const selectedAssetCount = selectedAssetIds.length;

  const loadFolders = useCallback(async () => {
    setFoldersLoading(true);
    try {
      const response = await getAssetFolders() as unknown as ApiResponse<AssetFolder[]>;
      if (response.code === 200) {
        setFolders(response.data || []);
      } else {
        message.error(response.message || '获取文件夹失败');
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '获取文件夹失败');
    } finally {
      setFoldersLoading(false);
    }
  }, []);

  const loadAssets = useCallback(async (page: number) => {
    setLoading(true);
    try {
      const params = {
        page,
        pageSize,
        folderId: selectedFolderId === null ? undefined : selectedFolderId,
        keyword: keyword || undefined,
        startDate: dateRange?.[0]?.format('YYYY-MM-DD'),
        endDate: dateRange?.[1]?.format('YYYY-MM-DD'),
      };

      const response = assetType === 'image'
        ? await getAssets(params)
        : assetType === 'video'
          ? await getVideoAssets(params)
          : await getAudioAssets(params);

      if (response.code === 200 && response.data) {
        const nextAssets = response.data.list || [];
        setAssets(nextAssets);
        setTotal(response.data.total || 0);
        setSelectedAssetIds(prev => prev.filter(id => nextAssets.some(item => item.id === id)));
      } else {
        message.error(response.message || '获取资产列表失败');
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '获取资产列表失败');
    } finally {
      setLoading(false);
    }
  }, [dateRange, keyword, selectedFolderId, assetType]);

  useEffect(() => {
    loadFolders();
  }, [loadFolders]);

  useEffect(() => {
    loadAssets(current);
  }, [current, loadAssets]);

  useEffect(() => {
    if (!manageMode) {
      setSelectedAssetIds([]);
    }
  }, [manageMode]);

  useEffect(() => {
    const rootIds = folderTree.map(node => node.id);
    setExpandedFolderIds(prev => {
      const next = new Set(prev.filter(id => folderLookup.has(id)));
      rootIds.forEach(id => next.add(id));

      if (selectedFolderId !== null && selectedFolderId !== ROOT_FOLDER_ID) {
        let currentId: number | undefined = selectedFolderId;
        while (currentId && currentId !== ROOT_FOLDER_ID) {
          next.add(currentId);
          const currentFolder = folderLookup.get(currentId);
          currentId = currentFolder?.parentId && currentFolder.parentId !== ROOT_FOLDER_ID
            ? currentFolder.parentId
            : undefined;
        }
      }

      return Array.from(next);
    });
  }, [folderLookup, folderTree, selectedFolderId]);

  useEffect(() => {
    if (!folderPopoverOpen) {
      return undefined;
    }

    const handleClickOutside = (event: MouseEvent) => {
      if (!folderSwitcherRef.current?.contains(event.target as Node)) {
        setFolderPopoverOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [folderPopoverOpen]);

  useEffect(() => {
    const syncTheme = () => {
      const nextTheme = document.body.getAttribute('data-theme') === 'light'
        || localStorage.getItem('dashboard-theme') === 'light'
        ? 'light'
        : 'dark';
      setThemeMode(nextTheme);
    };

    syncTheme();
    const observer = new MutationObserver(syncTheme);
    observer.observe(document.body, { attributes: true, attributeFilter: ['data-theme'] });

    window.addEventListener('storage', syncTheme);
    return () => {
      observer.disconnect();
      window.removeEventListener('storage', syncTheme);
    };
  }, []);

  const scrollToTop = () => {
    containerRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const markPreviewFallback = useCallback((imageUrl?: string) => {
    if (!imageUrl) {
      return;
    }
    setPreviewFallbackMap(prev => (
      prev[imageUrl]
        ? prev
        : { ...prev, [imageUrl]: true }
    ));
  }, []);

  const resolvePreviewSrc = useCallback((imageUrl?: string, size: number = 720) => {
    if (!imageUrl) {
      return '';
    }
    if (previewFallbackMap[imageUrl]) {
      return imageUrl;
    }
    return buildPreviewUrl(imageUrl, size);
  }, [previewFallbackMap]);

  const closeFolderModal = () => {
    setFolderModalOpen(false);
    setEditingFolder(null);
    setFolderNameInput('');
    setFolderParentId(ROOT_FOLDER_ID);
  };

  const closeTitleModal = () => {
    setTitleModalOpen(false);
    setEditingAsset(null);
    setTitleInput('');
  };

  const closeMoveModal = () => {
    setMoveModalOpen(false);
    setMovingAssetIds([]);
    setMoveTargetFolderId(ROOT_FOLDER_ID);
  };

  const handleDownload = async (item: ImageAsset) => {
    try {
      const downloadUrl = item.imageUrl;
      const response = await fetch(downloadUrl);
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      const ext = assetType === 'audio' ? 'mp3' : assetType === 'video' ? 'mp4' : 'png';
      link.download = `pixel-ai-${item.id}-${Date.now()}.${ext}`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      message.success('下载成功');
    } catch (error) {
      const link = document.createElement('a');
      link.href = item.imageUrl;
      link.download = `pixel-ai-${item.id}-${Date.now()}.png`;
      link.target = '_blank';
      link.click();
    }
  };

  const handleFullscreen = (item: ImageAsset) => {
    setFullscreenAsset({ type: 'image', item });
    setFullscreenVisible(true);
  };

  const handleVideoFullscreen = (item: VideoAsset) => {
    setFullscreenAsset({ type: 'video', item });
    setFullscreenVisible(true);
  };

  const handleShare = (item: ImageAsset) => {
    setShareImage(item);
    setShareModalVisible(true);
    setCopied(false);
  };

  const handleCopyLink = async () => {
    if (!shareImage) {
      return;
    }
    try {
      await navigator.clipboard.writeText(shareImage.imageUrl);
      setCopied(true);
      message.success('链接已复制');
      setTimeout(() => setCopied(false), 2000);
    } catch (error) {
      message.error('复制失败');
    }
  };

  const handleCrop = (item: ImageAsset) => {
    setCropImage(item);
    setCropModalVisible(true);
  };

  const openTitleModal = (item: ImageAsset) => {
    setEditingAsset(item);
    setTitleInput(item.title || '');
    setTitleModalOpen(true);
  };

  const handleAddText = (item: ImageAsset) => {
    setTextModalImage(item);
    setOverlayText('');
    setTextModalVisible(true);
  };

  const openHotSubmitModal = (item: ImageAsset) => {
    setHotSubmitAsset(item);
    setHotSubmitDesc('');
    setHotSubmitModalOpen(true);
  };

  const handleHotSubmit = async () => {
    if (!hotSubmitAsset) return;
    setHotSubmitting(true);
    try {
      const res = await submitToHot({
        imageAssetId: hotSubmitAsset.id,
        description: hotSubmitDesc || undefined,
      }) as unknown as ApiResponse<unknown>;
      if (res.code === 200) {
        message.success('提交成功，等待审核');
        setHotSubmitModalOpen(false);
      } else {
        message.error(res.message || '提交失败');
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '提交失败，请稍后重试');
    } finally {
      setHotSubmitting(false);
    }
  };

  const openCreateFolderModal = (parentId?: number) => {
    setFolderPopoverOpen(false);
    setFolderModalMode('create');
    setEditingFolder(null);
    setFolderNameInput('');
    setFolderParentId(parentId ?? (selectedFolderId === null ? ROOT_FOLDER_ID : selectedFolderId));
    setFolderModalOpen(true);
  };

  const openRenameFolderModal = (folder: AssetFolder) => {
    setFolderPopoverOpen(false);
    setFolderModalMode('rename');
    setEditingFolder(folder);
    setFolderNameInput(folder.folderName);
    setFolderParentId(folder.parentId ?? ROOT_FOLDER_ID);
    setFolderModalOpen(true);
  };

  const submitFolderModal = async () => {
    const folderName = folderNameInput.trim();
    if (!folderName) {
      message.warning('请输入文件夹名称');
      return;
    }

    setFolderSubmitting(true);
    try {
      let response: unknown;
      if (folderModalMode === 'create') {
        response = await createAssetFolder({ folderName, parentId: folderParentId });
      } else if (editingFolder) {
        response = await renameAssetFolder(editingFolder.id, { folderName });
      }

      const result = response as { code: number; message?: string };
      if (result.code !== 200) {
        message.error(result.message || '文件夹操作失败');
        return;
      }

      message.success(folderModalMode === 'create' ? '文件夹创建成功' : '文件夹重命名成功');
      closeFolderModal();
      await loadFolders();
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '文件夹操作失败');
    } finally {
      setFolderSubmitting(false);
    }
  };

  const handleDeleteFolder = async (folder: AssetFolder) => {
    try {
      const response = await deleteAssetFolder(folder.id) as unknown as { code: number; message?: string };
      if (response.code !== 200) {
        message.error(response.message || '删除文件夹失败');
        return;
      }
      message.success('文件夹删除成功');
      setFolders(prev => prev.filter(item => item.id !== folder.id));
      if (selectedFolderId === folder.id) {
        setSelectedFolderId(null);
        setCurrent(1);
        await loadFolders();
      } else {
        await Promise.all([loadFolders(), loadAssets(current)]);
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '删除文件夹失败');
    }
  };

  const confirmDeleteFolder = (folder: AssetFolder) => {
    Modal.confirm({
      centered: true,
      title: `确认删除文件夹「${folder.folderName}」吗？`,
      content: '删除前请确保文件夹内没有图片和子文件夹。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => handleDeleteFolder(folder),
    });
  };

  const submitTitleUpdate = async () => {
    const title = titleInput.trim();
    if (!editingAsset || !title) {
      message.warning('请输入图片标题');
      return;
    }
    setTitleSubmitting(true);
    try {
      const response = assetType === 'image'
        ? await updateAssetTitle(editingAsset.id, { title })
        : assetType === 'video'
          ? await updateVideoAssetTitle(editingAsset.id, title)
          : await updateAudioAssetTitle(editingAsset.id, title);

      if (response.code !== 200) {
        message.error(response.message || '标题修改失败');
        return;
      }
      const newTitle = response.data?.title || title;
      setAssets(prev => prev.map(item => item.id === editingAsset.id ? { ...item, title: newTitle } : item));
      closeTitleModal();
      message.success('标题修改成功');
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '标题修改失败');
    } finally {
      setTitleSubmitting(false);
    }
  };

  const openMoveModal = (assetIds: number[], defaultFolderId: number) => {
    if (assetIds.length === 0) {
      message.warning('请选择要移动的图片');
      return;
    }
    setMovingAssetIds(assetIds);
    setMoveTargetFolderId(defaultFolderId);
    setMoveModalOpen(true);
  };

  const openSingleMoveModal = (item: ImageAsset) => {
    openMoveModal([item.id], item.folderId ?? ROOT_FOLDER_ID);
  };

  const openBatchMoveModal = () => {
    openMoveModal(selectedAssetIds, selectedFolderId ?? ROOT_FOLDER_ID);
  };

  const submitMoveAssets = async () => {
    if (movingAssetIds.length === 0) {
      return;
    }

    const idsToMove = [...movingAssetIds];
    setMoveSubmitting(true);
    try {
      const response = assetType === 'image'
        ? await moveAssets({ assetIds: idsToMove, folderId: moveTargetFolderId })
        : assetType === 'video'
          ? await moveVideoAssets(idsToMove, moveTargetFolderId)
          : await moveAudioAssets(idsToMove, moveTargetFolderId);

      if (response.code !== 200) {
        message.error(response.message || `移动${assetTypeLabel}失败`);
        return;
      }
      message.success(idsToMove.length > 1 ? `${assetTypeLabel}批量移动成功` : `${assetTypeLabel}移动成功`);
      closeMoveModal();
      setSelectedAssetIds(prev => prev.filter(id => !idsToMove.includes(id)));

      const movedAllCurrentPageItems = assets.length === idsToMove.length;
      const movingOutFromCurrentFolder = selectedFolderId !== null && moveTargetFolderId !== selectedFolderId;

      if (current > 1 && movedAllCurrentPageItems && movingOutFromCurrentFolder) {
        setCurrent(prev => Math.max(1, prev - 1));
      } else {
        await loadAssets(current);
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '移动图片失败');
    } finally {
      setMoveSubmitting(false);
    }
  };

  const executeDeleteAssets = async (assetIds: number[]) => {
    const idsToDelete = Array.from(new Set(assetIds));
    if (idsToDelete.length === 0) {
      return;
    }

    setDeleteSubmitting(true);
    try {
      const response = assetType === 'image'
        ? await deleteAssets({ assetIds: idsToDelete })
        : assetType === 'video'
          ? await deleteVideoAssets(idsToDelete)
          : await deleteAudioAssets(idsToDelete);

      if (response.code !== 200) {
        message.error(response.message || `删除${assetTypeLabel}失败`);
        return;
      }
      message.success(idsToDelete.length > 1 ? `${assetTypeLabel}批量删除成功` : `${assetTypeLabel}删除成功`);
      setSelectedAssetIds(prev => prev.filter(id => !idsToDelete.includes(id)));

      const deletedAllCurrentPageItems = assets.length === idsToDelete.length;
      if (current > 1 && deletedAllCurrentPageItems) {
        setCurrent(prev => Math.max(1, prev - 1));
      } else {
        await loadAssets(current);
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || '删除图片失败');
    } finally {
      setDeleteSubmitting(false);
    }
  };

  const confirmDeleteSelectedAssets = () => {
    if (selectedAssetIds.length === 0) {
      message.warning('请先选择图片');
      return;
    }
    Modal.confirm({
      centered: true,
      title: `确认删除已选中的 ${selectedAssetIds.length} 张图片吗？`,
      content: '删除后图片会从资产库移除，但不会影响历史生成记录。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => executeDeleteAssets(selectedAssetIds),
    });
  };

  const toggleAssetSelection = (assetId: number) => {
    setSelectedAssetIds(prev => prev.includes(assetId)
      ? prev.filter(id => id !== assetId)
      : [...prev, assetId]);
  };

  const toggleSelectCurrentPage = () => {
    if (assets.length === 0) {
      return;
    }
    if (allCurrentPageSelected) {
      setSelectedAssetIds([]);
      return;
    }
    setSelectedAssetIds(assets.map(item => item.id));
  };

  const exitManageMode = () => {
    setManageMode(false);
    setSelectedAssetIds([]);
  };

  const handleDateRangeChange = (dates: [Dayjs | null, Dayjs | null] | null) => {
    setCurrent(1);
    setDateRange(dates);
  };

  const handlePageChange = (page: number) => {
    setCurrent(page);
    scrollToTop();
  };

  const handleSearch = () => {
    setCurrent(1);
    setKeyword(keywordInput.trim());
  };

  const handleFolderSelect = (folderId: number | null) => {
    if (folderId !== null && folderId !== ROOT_FOLDER_ID) {
      setExpandedFolderIds(prev => {
        const next = new Set(prev);
        let currentId: number | undefined = folderId;
        while (currentId && currentId !== ROOT_FOLDER_ID) {
          next.add(currentId);
          const currentFolder = folderLookup.get(currentId);
          currentId = currentFolder?.parentId && currentFolder.parentId !== ROOT_FOLDER_ID
            ? currentFolder.parentId
            : undefined;
        }
        return Array.from(next);
      });
    }
    setSelectedFolderId(folderId);
    setCurrent(1);
    setSelectedAssetIds([]);
    setFolderPopoverOpen(false);
  };

  const toggleFolderExpand = (folderId: number) => {
    setExpandedFolderIds(prev => prev.includes(folderId)
      ? prev.filter(id => id !== folderId)
      : [...prev, folderId]);
  };

  const renderFolderTreeNode = (folder: FolderTreeNode): React.ReactNode => {
    const hasChildren = folder.children.length > 0;
    const expanded = expandedFolderIds.includes(folder.id);

    return (
      <div key={folder.id} className="folder-tree-node">
        <div
          className={`folder-switcher-item ${selectedFolderId === folder.id ? 'active' : ''}`}
          onClick={() => handleFolderSelect(folder.id)}
        >
          <div
            className="folder-switcher-item-main"
            style={{ paddingLeft: `${folder.depth * 18}px` }}
          >
            <button
              type="button"
              className={`folder-tree-toggle ${expanded ? 'expanded' : ''} ${!hasChildren ? 'placeholder' : ''}`}
              onClick={(event) => {
                event.stopPropagation();
                if (hasChildren) {
                  toggleFolderExpand(folder.id);
                }
              }}
            >
              <DownOutlined />
            </button>
            <FolderOutlined className="folder-tree-icon" />
            <span>{folder.folderName}</span>
          </div>
          <div
            className="folder-switcher-item-actions"
            onClick={(event) => event.stopPropagation()}
          >
            <button
              type="button"
              className="folder-inline-action create"
              onClick={() => openCreateFolderModal(folder.id)}
              title="新建子文件夹"
            >
              <PlusOutlined />
            </button>
            <button
              type="button"
              className="folder-inline-action"
              onClick={() => openRenameFolderModal(folder)}
              title="重命名"
            >
              <EditOutlined />
            </button>
            <button
              type="button"
              className="folder-inline-action danger"
              onClick={() => confirmDeleteFolder(folder)}
              title="删除"
            >
              <DeleteOutlined />
            </button>
          </div>
        </div>

        {hasChildren && expanded && (
          <div className="folder-switcher-children">
            {folder.children.map(renderFolderTreeNode)}
          </div>
        )}
      </div>
    );
  };

  const [playingAudioId, setPlayingAudioId] = useState<number | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const handleAudioPlay = useCallback((item: AudioAsset) => {
    if (playingAudioId === item.id) {
      audioRef.current?.pause();
      setPlayingAudioId(null);
      return;
    }
    if (audioRef.current) {
      audioRef.current.pause();
    }
    const audio = new Audio(item.audioUrl);
    audio.onended = () => setPlayingAudioId(null);
    audio.onerror = () => {
      message.error('音频播放失败');
      setPlayingAudioId(null);
    };
    audio.play();
    audioRef.current = audio;
    setPlayingAudioId(item.id);
  }, [playingAudioId]);

  useEffect(() => {
    return () => {
      audioRef.current?.pause();
    };
  }, [assetType]);

  const renderAudioCard = (item: AudioAsset) => {
    const selected = selectedAssetIds.includes(item.id);
    const isPlaying = playingAudioId === item.id;

    return (
      <div
        key={item.id}
        className={`asset-card audio-card ${manageMode ? 'manage-mode' : ''} ${selected ? 'selected' : ''}`}
        onClick={manageMode ? () => toggleAssetSelection(item.id) : undefined}
      >
        <div className="asset-image-wrapper audio-cover-wrapper">
          {manageMode && (
            <button
              type="button"
              className={`asset-select-toggle ${selected ? 'selected' : ''}`}
              onClick={(e) => { e.stopPropagation(); toggleAssetSelection(item.id); }}
            >
              {selected && <CheckOutlined />}
            </button>
          )}
          {item.coverUrl ? (
            <img
              src={resolvePreviewSrc(item.coverUrl)}
              alt={item.title || '音频封面'}
              className="asset-image audio-cover-img"
              loading="lazy"
              onError={() => markPreviewFallback(item.coverUrl)}
            />
          ) : (
            <div className="audio-cover-placeholder">
              <CustomerServiceOutlined />
            </div>
          )}
          {!manageMode && (
            <div className="audio-play-overlay" onClick={() => handleAudioPlay(item)}>
              <div className={`audio-play-btn ${isPlaying ? 'playing' : ''}`}>
                {isPlaying ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
              </div>
            </div>
          )}
          {isPlaying && (
            <div className="audio-wave-indicator">
              <span /><span /><span /><span /><span />
            </div>
          )}
          {!manageMode && (
            <div className="audio-action-bar">
              <Tooltip title="修改标题">
                <button type="button" className="action-btn" onClick={(e) => { e.stopPropagation(); openTitleModal(item as any); }}>
                  <EditOutlined />
                </button>
              </Tooltip>
              <Tooltip title="下载">
                <button type="button" className="action-btn" onClick={(e) => { e.stopPropagation(); handleDownload({ ...item, imageUrl: item.audioUrl } as any); }}>
                  <DownloadOutlined />
                </button>
              </Tooltip>
            </div>
          )}
        </div>
        <div className="asset-info">
          <div className="asset-title-row">
            <p className="asset-title-text" title={item.title}>
              {item.title || '未命名音频'}
            </p>
            {manageMode && selected && <span className="asset-selected-badge">已选</span>}
          </div>
          <p className="asset-prompt" title={item.tags || item.prompt}>
            {item.tags || item.prompt || '无描述'}
          </p>
          <p className="asset-time">
            {dayjs(item.createdAt).format('YYYY-MM-DD HH:mm')}
          </p>
        </div>
      </div>
    );
  };

  const renderAssetCard = (item: ImageAsset) => {
    const selected = selectedAssetIds.includes(item.id);

    return (
      <div
        key={item.id}
        className={`asset-card ${manageMode ? 'manage-mode' : ''} ${selected ? 'selected' : ''}`}
        onClick={
          manageMode
            ? () => toggleAssetSelection(item.id)
            : assetType === 'video' && (item as VideoAsset).videoUrl
              ? () => handleVideoFullscreen(item as VideoAsset)
              : undefined
        }
      >
        <div className="asset-image-wrapper" onMouseEnter={(e) => {
          const video = e.currentTarget.querySelector('video');
          if (video) video.play();
        }} onMouseLeave={(e) => {
          const video = e.currentTarget.querySelector('video');
          if (video) {
            video.pause();
            video.currentTime = 0;
          }
        }}>
          {manageMode && (
            <button
              type="button"
              className={`asset-select-toggle ${selected ? 'selected' : ''}`}
              onClick={(event) => {
                event.stopPropagation();
                toggleAssetSelection(item.id);
              }}
            >
              {selected && <CheckOutlined />}
            </button>
          )}
          {assetType === 'video' && (item as any).videoUrl ? (
            <>
              <img
                src={resolvePreviewSrc((item as any).coverUrl)}
                alt={item.title || item.prompt || '视频封面'}
                className="asset-image asset-cover"
                loading="lazy"
                onError={() => markPreviewFallback((item as any).coverUrl)}
              />
              <video
                src={(item as any).videoUrl}
                className="asset-video-preview"
                loop
                muted
                playsInline
                preload="metadata"
              />
              {!manageMode && (
                <div className="asset-video-overlay">
                  <div className="asset-video-play">
                    <VideoCameraOutlined />
                    <span>点击全屏播放</span>
                  </div>
                </div>
              )}
            </>
          ) : (
            <img
              src={resolvePreviewSrc((item as any).imageUrl || (item as any).coverUrl)}
              alt={item.title || item.prompt || `${assetTypeLabel}资产`}
              className="asset-image"
              loading="lazy"
              decoding="async"
              fetchPriority="low"
              onError={() => markPreviewFallback((item as any).imageUrl || (item as any).coverUrl)}
            />
          )}
          {!manageMode && assetType === 'image' && (
            <div className="asset-overlay">
              <div className="asset-actions">
                <Tooltip title="修改标题">
                  <button type="button" className="action-btn" onClick={() => openTitleModal(item)}>
                    <EditOutlined />
                  </button>
                </Tooltip>
                <Tooltip title="移动文件夹">
                  <button type="button" className="action-btn" onClick={() => openSingleMoveModal(item)}>
                    <FolderOpenOutlined />
                  </button>
                </Tooltip>
                <Tooltip title="全屏">
                  <button type="button" className="action-btn" onClick={() => handleFullscreen(item)}>
                    <ExpandOutlined />
                  </button>
                </Tooltip>
                <Tooltip title="分享">
                  <button type="button" className="action-btn" onClick={() => handleShare(item)}>
                    <ShareAltOutlined />
                  </button>
                </Tooltip>
                <Tooltip title="裁剪">
                  <button type="button" className="action-btn" onClick={() => handleCrop(item)}>
                    <ScissorOutlined />
                  </button>
                </Tooltip>
                <Tooltip title="下载">
                  <button type="button" className="action-btn" onClick={() => handleDownload(item)}>
                    <DownloadOutlined />
                  </button>
                </Tooltip>
                <Tooltip title="添加文字">
                  <button type="button" className="action-btn" onClick={() => handleAddText(item)}>
                    <FontSizeOutlined />
                  </button>
                </Tooltip>
                <Tooltip title="申请上热门">
                  <button type="button" className="action-btn hot-btn" onClick={() => openHotSubmitModal(item)}>
                    <FireOutlined />
                  </button>
                </Tooltip>
              </div>
            </div>
          )}
        </div>
        <div className="asset-info">
          <div className="asset-title-row">
            <p className="asset-title-text" title={item.title}>
              {item.title || '未命名作品'}
            </p>
            {assetType === 'video' && !manageMode && (
              <Tooltip title="修改标题">
                <button type="button" className="title-edit-btn" onClick={() => openTitleModal(item)}>
                  <EditOutlined />
                </button>
              </Tooltip>
            )}
            {manageMode && selected && <span className="asset-selected-badge">已选</span>}
          </div>
          <p className="asset-prompt" title={item.prompt}>
            {item.prompt || '无提示词'}
          </p>
          <p className="asset-time">
            {dayjs(item.createdAt).format('YYYY-MM-DD HH:mm')}
          </p>
        </div>
      </div>
    );
  };

  return (
    <div className="assets-container" ref={containerRef}>
      <div className="assets-header">
        <div className="assets-title">
          <h2>我的资产</h2>
          <span className="assets-count">{currentFolderName} · 共 {total} {assetType === 'image' ? '张图片' : assetType === 'video' ? '个视频' : '个音频'}</span>
        </div>
        <div className="asset-type-tabs">
          <button
            className={`asset-type-tab ${assetType === 'image' ? 'active' : ''}`}
            onClick={() => setAssetType('image')}
          >
            <PictureOutlined />
            <span>图片</span>
          </button>
          <button
            className={`asset-type-tab ${assetType === 'video' ? 'active' : ''}`}
            onClick={() => setAssetType('video')}
          >
            <VideoCameraOutlined />
            <span>视频</span>
          </button>
          <button
            className={`asset-type-tab ${assetType === 'audio' ? 'active' : ''}`}
            onClick={() => setAssetType('audio')}
          >
            <CustomerServiceOutlined />
            <span>音频</span>
          </button>
        </div>
        <div className={`assets-toolbar ${manageMode ? 'manage-active' : ''}`}>
          <div className="assets-filters">
            <div className="folder-switcher" ref={folderSwitcherRef}>
              <button
                type="button"
                className="folder-switcher-trigger"
                onClick={() => setFolderPopoverOpen(prev => !prev)}
              >
                <span className="folder-switcher-trigger-main">
                  {selectedFolderId === ROOT_FOLDER_ID ? <HomeOutlined /> : <FolderOpenOutlined />}
                  <span className="folder-switcher-trigger-text">{currentFolderName}</span>
                </span>
                <DownOutlined
                  className={`folder-switcher-trigger-arrow ${folderPopoverOpen ? 'open' : ''}`}
                />
              </button>
              {folderPopoverOpen && (
                <div className="folder-switcher-dropdown">
                  <div className="folder-switcher-panel">
                    <div className="folder-switcher-header">
                      <div className="folder-switcher-title">切换目录</div>
                      <div className="folder-switcher-subtitle">当前目录：{currentFolderName}</div>
                    </div>
                    <Spin spinning={foldersLoading}>
                      <div className="folder-switcher-list">
                        <div
                          className={`folder-switcher-item ${selectedFolderId === null ? 'active' : ''}`}
                          onClick={() => handleFolderSelect(null)}
                        >
                          <div className="folder-switcher-item-main">
                            <FolderOpenOutlined />
                            <span>全部{assetTypeLabel}</span>
                          </div>
                        </div>
                        <div
                          className={`folder-switcher-item ${selectedFolderId === ROOT_FOLDER_ID ? 'active' : ''}`}
                          onClick={() => handleFolderSelect(ROOT_FOLDER_ID)}
                        >
                          <div className="folder-switcher-item-main">
                            <HomeOutlined />
                            <span>根目录</span>
                          </div>
                        </div>
                        {folderTree.map(renderFolderTreeNode)}
                        {folders.length === 0 && (
                          <div className="folder-switcher-empty">暂无自定义文件夹</div>
                        )}
                      </div>
                    </Spin>
                    <button
                      type="button"
                      className="folder-switcher-create"
                      onClick={() => openCreateFolderModal()}
                    >
                      <PlusOutlined />
                      <span>{selectedFolderId !== null ? '在当前目录新建' : '新建根目录文件夹'}</span>
                    </button>
                  </div>
                </div>
              )}
            </div>

            <Input
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              onPressEnter={handleSearch}
              placeholder="搜索标题或提示词"
              allowClear
              className="asset-search-input"
              suffix={<SearchOutlined onClick={handleSearch} className="asset-search-icon" />}
            />

            <RangePicker
              onChange={handleDateRangeChange}
              placeholder={['开始日期', '结束日期']}
              allowClear
              className="date-picker"
            />
          </div>

          <div className={`assets-manage-toolbar ${manageMode ? 'active' : ''}`}>
            {manageMode ? (
              <>
                <span className="manage-summary">
                  <span>已选</span>
                  <span className="manage-summary-count">{selectedAssetCount}</span>
                  <span>张</span>
                </span>
                <div className="manage-action-group">
                  <Button
                    className="manage-action-btn manage-action-btn-neutral"
                    icon={<CheckOutlined />}
                    onClick={toggleSelectCurrentPage}
                  >
                    {allCurrentPageSelected ? '取消全选' : '本页全选'}
                  </Button>
                  <Button
                    className="manage-action-btn manage-action-btn-primary"
                    icon={<FolderOpenOutlined />}
                    disabled={selectedAssetCount === 0}
                    onClick={openBatchMoveModal}
                  >
                    移动目录
                  </Button>
                  <Button
                    className="manage-action-btn manage-action-btn-danger"
                    icon={<DeleteOutlined />}
                    danger
                    disabled={selectedAssetCount === 0 || deleteSubmitting}
                    onClick={confirmDeleteSelectedAssets}
                  >
                    删除
                  </Button>
                  <Button
                    className="manage-action-btn manage-action-btn-finish"
                    icon={<CheckOutlined />}
                    onClick={exitManageMode}
                  >
                    完成
                  </Button>
                </div>
              </>
            ) : (
              <Button
                className="manage-entry-btn"
                icon={<FolderOpenOutlined />}
                onClick={() => setManageMode(true)}
              >
                {assetTypeLabel}管理
              </Button>
            )}
          </div>
        </div>
      </div>

      <div className="assets-main">
        <Spin spinning={loading}>
          {assets.length > 0 ? (
            <>
              <div className="assets-grid">
                {assets.map(item => assetType === 'audio' ? renderAudioCard(item as unknown as AudioAsset) : renderAssetCard(item))}
              </div>
              <div className="assets-pagination">
                <Pagination
                  current={current}
                  total={total}
                  pageSize={pageSize}
                  onChange={handlePageChange}
                  showSizeChanger={false}
                  showTotal={(count) => `共 ${count} 个${assetTypeLabel}`}
                />
              </div>
            </>
          ) : (
            !loading && <Empty description={`当前目录暂无${assetTypeLabel}`} className="empty-state" />
          )}
        </Spin>
      </div>

      <ConfigProvider theme={assetFormTheme}>
        <Modal
          open={folderModalOpen}
          onCancel={closeFolderModal}
          onOk={submitFolderModal}
          confirmLoading={folderSubmitting}
          title={folderModalMode === 'create' ? '新建文件夹' : '重命名文件夹'}
          className="asset-form-modal"
          wrapClassName={`asset-form-modal-wrap asset-form-modal-wrap-${themeMode}`}
        >
          {folderModalMode === 'create' && (
            <div className="folder-modal-field">
              <div className="folder-modal-label">上级目录</div>
              <TreeSelect
                value={folderParentId}
                onChange={(value) => setFolderParentId(value)}
                treeData={folderTreeSelectData}
                treeDefaultExpandAll
                treeLine
                placeholder="请选择上级目录"
                className={`move-folder-select move-folder-select-${themeMode}`}
                popupClassName={`asset-folder-select-dropdown asset-folder-select-dropdown-${themeMode}`}
                getPopupContainer={(triggerNode) => triggerNode.parentElement || document.body}
              />
            </div>
          )}
          <Input
            value={folderNameInput}
            onChange={(event) => setFolderNameInput(event.target.value)}
            placeholder="请输入文件夹名称"
            maxLength={32}
            className={`asset-modal-input asset-modal-input-${themeMode}`}
          />
        </Modal>
      </ConfigProvider>

      <ConfigProvider theme={assetFormTheme}>
        <Modal
          open={titleModalOpen}
          onCancel={closeTitleModal}
          onOk={submitTitleUpdate}
          confirmLoading={titleSubmitting}
          title="修改图片标题"
          className="asset-form-modal"
          wrapClassName={`asset-form-modal-wrap asset-form-modal-wrap-${themeMode}`}
        >
          <Input
            value={titleInput}
            onChange={(event) => setTitleInput(event.target.value)}
            placeholder="请输入图片标题"
            maxLength={120}
            className={`asset-modal-input asset-modal-input-${themeMode}`}
          />
        </Modal>
      </ConfigProvider>

      <ConfigProvider theme={assetFormTheme}>
        <Modal
          open={moveModalOpen}
          onCancel={closeMoveModal}
          onOk={submitMoveAssets}
          confirmLoading={moveSubmitting}
          title={movingAssetIds.length > 1 ? '批量移动到文件夹' : '移动到文件夹'}
          className="asset-form-modal"
          wrapClassName={`asset-form-modal-wrap asset-form-modal-wrap-${themeMode}`}
        >
          <TreeSelect
            value={moveTargetFolderId}
            onChange={(value) => setMoveTargetFolderId(value)}
            treeData={folderTreeSelectData}
            treeDefaultExpandAll
            treeLine
            placeholder="请选择目标文件夹"
            className={`move-folder-select move-folder-select-${themeMode}`}
            popupClassName={`asset-folder-select-dropdown asset-folder-select-dropdown-${themeMode}`}
            getPopupContainer={(triggerNode) => triggerNode.parentElement || document.body}
          />
        </Modal>
      </ConfigProvider>

      <Modal
        open={fullscreenVisible}
        onCancel={() => {
          setFullscreenVisible(false);
          setFullscreenAsset(null);
        }}
        footer={null}
        width="100vw"
        className="fullscreen-modal"
        wrapClassName="fullscreen-modal-wrap"
        style={{ top: 0, paddingBottom: 0, margin: 0 }}
        centered
        destroyOnHidden
        closeIcon={<CloseOutlined className="close-icon" />}
      >
        {fullscreenAsset && (
          <div className="fullscreen-content">
            <div className="fullscreen-stage">
              {fullscreenAsset.type === 'image' ? (
                <img
                  src={fullscreenAsset.item.imageUrl}
                  alt={fullscreenAsset.item.title || fullscreenAsset.item.prompt}
                  className="fullscreen-image"
                />
              ) : fullscreenAsset.type === 'video' ? (
                <div className="fullscreen-video-shell">
                  <div className="fullscreen-video-glow" />
                  <video
                    src={fullscreenAsset.item.videoUrl}
                    poster={resolvePreviewSrc(fullscreenAsset.item.coverUrl)}
                    className="fullscreen-video"
                    controls
                    autoPlay
                    playsInline
                    preload="auto"
                  />
                </div>
              ) : (
                <div className="fullscreen-audio-shell">
                  {(fullscreenAsset.item as AudioAsset).coverUrl ? (
                    <img
                      src={(fullscreenAsset.item as AudioAsset).coverUrl}
                      alt={fullscreenAsset.item.title || '音频封面'}
                      className="fullscreen-audio-cover"
                    />
                  ) : (
                    <div className="fullscreen-audio-placeholder">
                      <CustomerServiceOutlined />
                    </div>
                  )}
                  <audio
                    src={(fullscreenAsset.item as AudioAsset).audioUrl}
                    className="fullscreen-audio-player"
                    controls
                    autoPlay
                  />
                </div>
              )}
            </div>
            <div className="fullscreen-info">
              <p className="fullscreen-prompt">
                {fullscreenAsset.item.title || fullscreenAsset.item.prompt || (
                  fullscreenAsset.type === 'video' ? '未命名视频' : fullscreenAsset.type === 'audio' ? '未命名音频' : '未命名图片'
                )}
              </p>
              <p className="fullscreen-time">
                {dayjs(fullscreenAsset.item.createdAt).format('YYYY-MM-DD HH:mm:ss')}
              </p>
            </div>
          </div>
        )}
      </Modal>

      <ConfigProvider theme={assetFormTheme}>
        <Modal
          open={shareModalVisible}
          onCancel={() => {
            setShareModalVisible(false);
            setShareImage(null);
          }}
          footer={null}
          title="分享图片"
          className="share-modal"
          rootClassName={`share-modal-root share-modal-root-${themeMode}`}
          wrapClassName={`share-modal-wrap share-modal-wrap-${themeMode}`}
          centered
          destroyOnHidden
          styles={shareModalStyles}
        >
          {shareImage && (
            <div className="share-content">
              <div className="share-preview-panel">
                <img
                  src={resolvePreviewSrc(shareImage.imageUrl, 960)}
                  alt={shareImage.title || shareImage.prompt}
                  className="share-preview"
                  onError={() => markPreviewFallback(shareImage.imageUrl)}
                />
              </div>

              <div className="share-meta">
                <div className="share-meta-title">
                  {shareImage.title || shareImage.prompt || '未命名图片'}
                </div>
                <div className="share-meta-subtitle">
                  {dayjs(shareImage.createdAt).format('YYYY-MM-DD HH:mm:ss')} · 复制链接即可分享原图
                </div>
              </div>

              <div className="share-link-section">
                <div className="share-link-label">分享链接</div>
                <div className="share-link-wrapper">
                  <Input
                    value={shareImage.imageUrl}
                    readOnly
                    className="share-link-input"
                    style={{
                      background: themeMode === 'light' ? '#ffffff' : 'rgba(255, 255, 255, 0.08)',
                      color: themeMode === 'light' ? '#1a202c' : 'rgba(255, 255, 255, 0.9)',
                      borderColor: themeMode === 'light' ? '#d9d9d9' : 'rgba(255, 255, 255, 0.14)',
                    }}
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
            </div>
          )}
        </Modal>
      </ConfigProvider>

      <Modal
        open={cropModalVisible}
        onCancel={() => {
          setCropModalVisible(false);
          setCropImage(null);
        }}
        footer={null}
        title="裁剪图片"
        className="crop-modal"
        centered
        width={800}
        destroyOnHidden
      >
        {cropImage && (
          <div className="crop-content">
            <img
              src={cropImage.imageUrl}
              alt={cropImage.title || cropImage.prompt}
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

      <Modal
        open={textModalVisible}
        onCancel={() => {
          setTextModalVisible(false);
          setTextModalImage(null);
          setOverlayText('');
        }}
        title="添加文字"
        className="text-modal"
        centered
        width={800}
        destroyOnHidden
        footer={[
          <Button
            key="cancel"
            onClick={() => {
              setTextModalVisible(false);
              setTextModalImage(null);
              setOverlayText('');
            }}
          >
            取消
          </Button>,
          <Button
            key="save"
            type="primary"
            onClick={() => {
              message.success('文字添加功能即将上线');
              setTextModalVisible(false);
              setTextModalImage(null);
              setOverlayText('');
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
                alt={textModalImage.title || textModalImage.prompt}
                className="text-preview"
              />
              {overlayText && (
                <div className="text-overlay-preview">{overlayText}</div>
              )}
            </div>
            <div className="text-input-wrapper">
              <Input.TextArea
                value={overlayText}
                onChange={(event) => setOverlayText(event.target.value)}
                placeholder="输入要添加的文字..."
                rows={3}
                className="text-input"
              />
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={hotSubmitModalOpen}
        onCancel={() => {
          setHotSubmitModalOpen(false);
          setHotSubmitAsset(null);
          setHotSubmitDesc('');
        }}
        title="申请上热门"
        centered
        width={480}
        destroyOnHidden
        onOk={handleHotSubmit}
        okText="提交申请"
        cancelText="取消"
        confirmLoading={hotSubmitting}
      >
        {hotSubmitAsset && (
          <div style={{ textAlign: 'center' }}>
            <img
              src={hotSubmitAsset.imageUrl}
              alt={hotSubmitAsset.title}
              style={{ maxWidth: '100%', maxHeight: 260, borderRadius: 8, marginBottom: 16 }}
            />
            <Input.TextArea
              value={hotSubmitDesc}
              onChange={(e) => setHotSubmitDesc(e.target.value)}
              placeholder="添加描述（可选）"
              rows={3}
              maxLength={500}
              showCount
            />
          </div>
        )}
      </Modal>
    </div>
  );
};

export default Assets;
