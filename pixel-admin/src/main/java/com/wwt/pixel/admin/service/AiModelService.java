package com.wwt.pixel.admin.service;

import com.wwt.pixel.admin.domain.AiModel;
import com.wwt.pixel.admin.domain.AiModelParamDef;
import com.wwt.pixel.admin.mapper.AiModelMapper;
import com.wwt.pixel.admin.mapper.AiModelParamDefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiModelMapper modelMapper;
    private final AiModelParamDefMapper paramDefMapper;
    private final AiConfigSyncService syncService;

    public List<AiModel> listByProviderId(Long providerId) {
        return modelMapper.findByProviderId(providerId);
    }

    public AiModel getById(Long id) {
        return modelMapper.findById(id);
    }

    public List<AiModelParamDef> listParams(Long modelId) {
        return paramDefMapper.findByModelId(modelId);
    }

    @Transactional
    public AiModel create(AiModel model) {
        modelMapper.insert(model);
        syncService.syncToNacos();
        return model;
    }

    public void update(AiModel model) {
        modelMapper.update(model);
        syncService.syncToNacos();
    }

    public void delete(Long id) {
        modelMapper.delete(id);
        syncService.syncToNacos();
    }

    public void createParam(AiModelParamDef paramDef) {
        paramDefMapper.insert(paramDef);
        syncService.syncToNacos();
    }

    public void updateParam(AiModelParamDef paramDef) {
        String options = paramDef.getOptions();
        if (options != null) {
            options = options.trim();
            if (options.isEmpty() || options.equals("null") || options.equals("undefined")) {
                paramDef.setOptions(null);
            }
        }
        paramDefMapper.update(paramDef);
        syncService.syncToNacos();
    }

    public void deleteParam(Long id) {
        paramDefMapper.delete(id);
        syncService.syncToNacos();
    }
}