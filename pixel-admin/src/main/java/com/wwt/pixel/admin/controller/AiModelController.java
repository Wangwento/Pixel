package com.wwt.pixel.admin.controller;

import com.wwt.pixel.admin.domain.AiModel;
import com.wwt.pixel.admin.domain.AiModelParamDef;
import com.wwt.pixel.admin.service.AiModelService;
import com.wwt.pixel.common.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-models")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelService modelService;

    @GetMapping("/provider/{providerId}")
    public Result<List<AiModel>> listByProvider(@PathVariable Long providerId) {
        return Result.success(modelService.listByProviderId(providerId));
    }

    @GetMapping("/{id}")
    public Result<AiModel> get(@PathVariable Long id) {
        return Result.success(modelService.getById(id));
    }

    @GetMapping("/{id}/params")
    public Result<List<AiModelParamDef>> listParams(@PathVariable Long id) {
        return Result.success(modelService.listParams(id));
    }

    @PostMapping
    public Result<AiModel> create(@RequestBody AiModel model) {
        return Result.success(modelService.create(model));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AiModel model) {
        model.setId(id);
        modelService.update(model);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        modelService.delete(id);
        return Result.success(null);
    }

    @PostMapping("/{id}/params")
    public Result<Void> createParam(@PathVariable Long id, @RequestBody AiModelParamDef paramDef) {
        paramDef.setModelId(id);
        modelService.createParam(paramDef);
        return Result.success(null);
    }

    @PutMapping("/params/{paramId}")
    public Result<Void> updateParam(@PathVariable Long paramId, @RequestBody AiModelParamDef paramDef) {
        paramDef.setId(paramId);
        // 处理 options 字段：空字符串或无效JSON转为null
        if (paramDef.getOptions() != null) {
            String options = paramDef.getOptions().trim();
            if (options.isEmpty() || options.equals("null") || options.equals("undefined")) {
                paramDef.setOptions(null);
            }
        }
        modelService.updateParam(paramDef);
        return Result.success(null);
    }

    @DeleteMapping("/params/{paramId}")
    public Result<Void> deleteParam(@PathVariable Long paramId) {
        modelService.deleteParam(paramId);
        return Result.success(null);
    }
}