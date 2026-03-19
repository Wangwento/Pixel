package com.wwt.pixel.video.service;

import com.wwt.pixel.video.domain.AssetFolder;
import com.wwt.pixel.video.domain.VideoAsset;
import com.wwt.pixel.video.mapper.AssetFolderMapper;
import com.wwt.pixel.video.mapper.VideoAssetMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoAssetService {

    private final VideoAssetMapper videoAssetMapper;
    private final AssetFolderMapper assetFolderMapper;

    public AssetPageResult listAssets(Long userId, Long folderId, String keyword,
                                      String startDate, String endDate, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<VideoAsset> list = videoAssetMapper.listAssets(userId, folderId, keyword, startDate, endDate, offset, pageSize);
        int total = videoAssetMapper.countAssets(userId, folderId, keyword, startDate, endDate);
        return new AssetPageResult(list, total, page, pageSize);
    }

    @Transactional
    public VideoAsset updateAssetTitle(Long userId, Long assetId, String title) {
        videoAssetMapper.updateTitle(userId, assetId, title);
        return videoAssetMapper.listAssets(userId, null, null, null, null, 0, 1)
                .stream().filter(a -> a.getId().equals(assetId)).findFirst().orElse(null);
    }

    @Transactional
    public void moveAssets(Long userId, List<Long> assetIds, Long folderId) {
        if (folderId != null && folderId != 0) {
            AssetFolder folder = assetFolderMapper.findById(folderId);
            if (folder == null || !folder.getUserId().equals(userId)) {
                throw new RuntimeException("文件夹不存在");
            }
        }
        videoAssetMapper.moveAssets(userId, assetIds, folderId == null ? 0 : folderId);
    }

    @Transactional
    public void deleteAssets(Long userId, List<Long> assetIds) {
        videoAssetMapper.deleteAssets(userId, assetIds);
    }

    @Data
    public static class AssetPageResult {
        private List<VideoAsset> list;
        private int total;
        private int page;
        private int pageSize;

        public AssetPageResult(List<VideoAsset> list, int total, int page, int pageSize) {
            this.list = list;
            this.total = total;
            this.page = page;
            this.pageSize = pageSize;
        }
    }
}