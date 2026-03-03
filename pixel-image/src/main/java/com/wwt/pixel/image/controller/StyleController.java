package com.wwt.pixel.image.controller;

import com.wwt.pixel.common.dto.Result;
import com.wwt.pixel.image.domain.StyleTemplate;
import com.wwt.pixel.image.service.StyleTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 风格模板控制器
 */
@RestController
@RequestMapping("/api/style")
@RequiredArgsConstructor
public class StyleController {

    private final StyleTemplateService styleTemplateService;

    @GetMapping("/list")
    public Result<List<StyleTemplate>> list() {
        return Result.success(styleTemplateService.getAllTemplates());
    }

    @GetMapping("/{id}")
    public Result<StyleTemplate> getById(@PathVariable Long id) {
        StyleTemplate template = styleTemplateService.getTemplateById(id);
        if (template == null) {
            return Result.error(404, "模板不存在");
        }
        return Result.success(template);
    }

    @GetMapping("/name/{nameEn}")
    public Result<StyleTemplate> getByName(@PathVariable String nameEn) {
        StyleTemplate template = styleTemplateService.getTemplateByNameEn(nameEn);
        if (template == null) {
            return Result.error(404, "模板不存在");
        }
        return Result.success(template);
    }

    @GetMapping("/category/{category}")
    public Result<List<StyleTemplate>> getByCategory(@PathVariable String category) {
        return Result.success(styleTemplateService.getTemplatesByCategory(category));
    }
}
