package com.wwt.pixel.admin.controller;

import com.wwt.pixel.admin.domain.AiProvider;
import com.wwt.pixel.admin.service.AiProviderService;
import com.wwt.pixel.common.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-providers")
@RequiredArgsConstructor
public class AiProviderController {

    private final AiProviderService providerService;

    @GetMapping
    public Result<List<AiProvider>> list() {
        return Result.success(providerService.listAll());
    }

    @GetMapping("/{id}")
    public Result<AiProvider> get(@PathVariable Long id) {
        return Result.success(providerService.getById(id));
    }

    @PostMapping
    public Result<AiProvider> create(@RequestBody AiProvider provider) {
        return Result.success(providerService.create(provider));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AiProvider provider) {
        provider.setId(id);
        providerService.update(provider);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        providerService.delete(id);
        return Result.success(null);
    }
}