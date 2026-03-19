package com.wwt.pixel.image.service;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.image.domain.AssetFolder;
import com.wwt.pixel.image.domain.GenerationRecord;
import com.wwt.pixel.image.domain.ImageAsset;
import com.wwt.pixel.image.mapper.AssetFolderMapper;
import com.wwt.pixel.image.mapper.GenerationRecordMapper;
import com.wwt.pixel.image.mapper.ImageAssetMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 图片资产服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    public static final long ROOT_FOLDER_ID = 0L;

    private static final DateTimeFormatter TITLE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMddHHmm");

    private final AssetFolderMapper assetFolderMapper;
    private final ImageAssetMapper imageAssetMapper;
    private final GenerationRecordMapper generationRecordMapper;

    public List<AssetFolder> listFolders(Long userId) {
        return assetFolderMapper.findByUserId(userId);
    }

    @Transactional
    public AssetFolder createFolder(Long userId, String folderName, Long parentId) {
        Long normalizedParentId = parentId == null ? ROOT_FOLDER_ID : parentId;
        if (normalizedParentId == null || normalizedParentId.longValue() != ROOT_FOLDER_ID) {
            requireFolder(userId, normalizedParentId);
        }
        AssetFolder folder = new AssetFolder();
        folder.setUserId(userId);
        folder.setParentId(normalizedParentId);
        folder.setFolderName(normalizeFolderName(folderName));
        folder.setSortOrder(0);
        try {
            assetFolderMapper.insert(folder);
            return folder;
        } catch (DuplicateKeyException e) {
            throw new BusinessException("同一目录下已存在同名文件夹");
        }
    }

    @Transactional
    public void renameFolder(Long userId, Long folderId, String folderName) {
        AssetFolder folder = requireFolder(userId, folderId);
        try {
            int updated = assetFolderMapper.updateName(folder.getId(), userId, normalizeFolderName(folderName));
            if (updated <= 0) {
                throw new BusinessException("文件夹重命名失败");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException("同一目录下已存在同名文件夹");
        }
    }

    @Transactional
    public void deleteFolder(Long userId, Long folderId) {
        AssetFolder folder = requireFolder(userId, folderId);
        int childFolderCount = assetFolderMapper.countByParentIdAndUserId(userId, folder.getId());
        if (childFolderCount > 0) {
            throw new BusinessException("文件夹内还有子文件夹，请先处理后再删除");
        }
        int assetCount = imageAssetMapper.countByUserIdAndFolderId(userId, folder.getId());
        if (assetCount > 0) {
            throw new BusinessException("文件夹内还有图片，请先移动图片后再删除");
        }
        assetFolderMapper.deleteByIdAndUserId(folder.getId(), userId);
    }

    public AssetPageResult listAssets(Long userId, Long folderId, String keyword,
                                      String startDate, String endDate, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        List<ImageAsset> list = imageAssetMapper.findByUserIdWithPaging(
                userId, folderId, normalizeKeyword(keyword), startDate, endDate, offset, pageSize);
        int total = imageAssetMapper.countByUserId(userId, folderId, normalizeKeyword(keyword), startDate, endDate);
        return AssetPageResult.builder()
                .list(list)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    @Transactional
    public ImageAsset updateAssetTitle(Long userId, Long assetId, String title) {
        ImageAsset asset = requireAsset(userId, assetId);
        String normalizedTitle = normalizeTitle(title);
        int updated = imageAssetMapper.updateTitle(asset.getId(), userId, normalizedTitle);
        if (updated <= 0) {
            throw new BusinessException("修改标题失败");
        }
        asset.setTitle(normalizedTitle);
        asset.setUpdatedAt(LocalDateTime.now());
        return asset;
    }

    @Transactional
    public void moveAssets(Long userId, List<Long> assetIds, Long folderId) {
        List<Long> normalizedAssetIds = normalizeAssetIds(assetIds, "请选择要移动的图片");
        Long targetFolderId = folderId == null ? ROOT_FOLDER_ID : folderId;
        if (targetFolderId == null || targetFolderId.longValue() != ROOT_FOLDER_ID) {
            requireFolder(userId, targetFolderId);
        }
        int ownedCount = imageAssetMapper.countByIdsAndUserId(userId, normalizedAssetIds);
        if (ownedCount != normalizedAssetIds.size()) {
            throw new BusinessException("存在无权限操作的图片");
        }
        imageAssetMapper.moveAssets(userId, targetFolderId, normalizedAssetIds);
    }

    @Transactional
    public void deleteAssets(Long userId, List<Long> assetIds) {
        List<Long> normalizedAssetIds = normalizeAssetIds(assetIds, "请选择要删除的图片");
        int ownedCount = imageAssetMapper.countByIdsAndUserId(userId, normalizedAssetIds);
        if (ownedCount != normalizedAssetIds.size()) {
            throw new BusinessException("存在无权限操作的图片");
        }
        imageAssetMapper.deleteAssets(userId, normalizedAssetIds);
    }

    @Transactional
    public void syncAssetFromGenerationRecord(Long recordId) {
        syncAssetFromGenerationRecord(recordId, null);
    }

    @Transactional
    public void syncAssetFromGenerationRecord(Long recordId, List<String> imageUrls) {
        if (recordId == null) {
            return;
        }
        GenerationRecord record = generationRecordMapper.findById(recordId);
        if (record == null || record.getStatus() == null || record.getStatus() != 1) {
            return;
        }

        List<String> finalImageUrls = resolveAssetImageUrls(imageUrls, record.getResultImageUrl());
        if (finalImageUrls.isEmpty()) {
            return;
        }

        List<ImageAsset> existingAssets = imageAssetMapper.findByGenerationRecordIdOrderByImageIndex(recordId);
        Set<Integer> existingIndexes = new HashSet<>();
        for (ImageAsset existingAsset : existingAssets) {
            if (existingAsset.getImageIndex() != null) {
                existingIndexes.add(existingAsset.getImageIndex());
            }
        }

        String baseTitle = buildDefaultTitle(record);
        LocalDateTime createdAt = record.getCreatedAt() == null ? LocalDateTime.now() : record.getCreatedAt();
        int totalImages = finalImageUrls.size();

        for (int index = 0; index < totalImages; index++) {
            if (existingIndexes.contains(index)) {
                continue;
            }

            ImageAsset asset = new ImageAsset();
            asset.setUserId(record.getUserId());
            asset.setGenerationRecordId(record.getId());
            asset.setImageIndex(index);
            asset.setFolderId(ROOT_FOLDER_ID);
            asset.setTitle(buildAssetTitle(baseTitle, index, totalImages));
            asset.setImageUrl(finalImageUrls.get(index));
            asset.setPrompt(record.getPrompt());
            asset.setStyle(record.getStyle());
            asset.setSourceType("GENERATED");
            asset.setCreatedAt(createdAt);
            try {
                imageAssetMapper.insert(asset);
            } catch (DuplicateKeyException e) {
                log.warn("资产已存在，跳过重复创建: recordId={}, imageIndex={}", recordId, index);
            }
        }
    }

    private AssetFolder requireFolder(Long userId, Long folderId) {
        if (folderId == null || ROOT_FOLDER_ID == folderId) {
            throw new BusinessException("根目录不支持此操作");
        }
        AssetFolder folder = assetFolderMapper.findByIdAndUserId(folderId, userId);
        if (folder == null) {
            throw new BusinessException("文件夹不存在");
        }
        return folder;
    }

    private ImageAsset requireAsset(Long userId, Long assetId) {
        ImageAsset asset = imageAssetMapper.findByIdAndUserId(assetId, userId);
        if (asset == null) {
            throw new BusinessException("图片资产不存在");
        }
        return asset;
    }

    private String normalizeFolderName(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            throw new BusinessException("文件夹名称不能为空");
        }
        String normalized = folderName.trim();
        if (normalized.length() > 32) {
            throw new BusinessException("文件夹名称不能超过32个字符");
        }
        return normalized;
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("标题不能为空");
        }
        String normalized = title.trim();
        if (normalized.length() > 120) {
            throw new BusinessException("标题不能超过120个字符");
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<Long> normalizeAssetIds(List<Long> assetIds, String emptyMessage) {
        if (assetIds == null || assetIds.isEmpty()) {
            throw new BusinessException(emptyMessage);
        }
        List<Long> normalized = assetIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new BusinessException(emptyMessage);
        }
        return normalized;
    }

    private List<String> resolveAssetImageUrls(List<String> imageUrls, String fallbackImageUrl) {
        List<String> normalized = new ArrayList<>();
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (imageUrl == null) {
                    continue;
                }
                String trimmed = imageUrl.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        if (normalized.isEmpty() && fallbackImageUrl != null) {
            String trimmed = fallbackImageUrl.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private String buildDefaultTitle(GenerationRecord record) {
        String prompt = record.getPrompt() == null ? "" : record.getPrompt().trim();
        if (!prompt.isEmpty()) {
            return prompt.length() > 30 ? prompt.substring(0, 30) + "..." : prompt;
        }
        LocalDateTime createdAt = record.getCreatedAt() == null ? LocalDateTime.now() : record.getCreatedAt();
        return "作品-" + createdAt.format(TITLE_TIME_FORMATTER);
    }

    private String buildAssetTitle(String baseTitle, int imageIndex, int totalImages) {
        if (totalImages <= 1) {
            return baseTitle;
        }
        return baseTitle + " (" + (imageIndex + 1) + "/" + totalImages + ")";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetPageResult {
        private List<ImageAsset> list;
        private Integer total;
        private Integer page;
        private Integer pageSize;
    }
}
