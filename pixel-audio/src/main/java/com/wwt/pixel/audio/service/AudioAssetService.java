package com.wwt.pixel.audio.service;

import com.wwt.pixel.audio.domain.AudioAsset;
import com.wwt.pixel.audio.mapper.AudioAssetMapper;
import com.wwt.pixel.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AudioAssetService {

    private final AudioAssetMapper audioAssetMapper;

    public Map<String, Object> getAssets(Long userId, Integer page, Integer pageSize,
                                         Long folderId, String keyword,
                                         String startDate, String endDate) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 24 : Math.min(pageSize, 100);
        int offset = (p - 1) * size;

        int total = audioAssetMapper.countByUser(userId, folderId, keyword, startDate, endDate);
        List<AudioAsset> list = audioAssetMapper.selectByUser(userId, folderId, keyword, startDate, endDate, offset, size);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", p);
        result.put("pageSize", size);
        return result;
    }

    public void updateTitle(Long userId, Long assetId, String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(400, "标题不能为空");
        }
        int rows = audioAssetMapper.updateTitle(assetId, userId, title.trim());
        if (rows == 0) {
            throw new BusinessException(404, "音频资产不存在或无权操作");
        }
    }

    public void moveAssets(Long userId, List<Long> assetIds, Long folderId) {
        if (assetIds == null || assetIds.isEmpty()) {
            throw new BusinessException(400, "请选择要移动的音频");
        }
        for (Long id : assetIds) {
            audioAssetMapper.updateFolderId(id, userId, folderId == null ? 0L : folderId);
        }
    }

    public void deleteAssets(Long userId, List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            throw new BusinessException(400, "请选择要删除的音频");
        }
        for (Long id : assetIds) {
            audioAssetMapper.deleteByIdAndUser(id, userId);
        }
    }
}