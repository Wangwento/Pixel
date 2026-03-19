package com.wwt.pixel.admin.service;

import com.wwt.pixel.admin.domain.AiProvider;
import com.wwt.pixel.admin.mapper.AiProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiProviderService {

    private final AiProviderMapper providerMapper;
    private final AiConfigSyncService syncService;

    public List<AiProvider> listAll() {
        return providerMapper.findAll();
    }

    public AiProvider getById(Long id) {
        return providerMapper.findById(id);
    }

    public AiProvider create(AiProvider provider) {
        providerMapper.insert(provider);
        syncService.syncToNacos();
        return provider;
    }

    public void update(AiProvider provider) {
        providerMapper.update(provider);
        syncService.syncToNacos();
    }

    public void delete(Long id) {
        providerMapper.delete(id);
        syncService.syncToNacos();
    }
}