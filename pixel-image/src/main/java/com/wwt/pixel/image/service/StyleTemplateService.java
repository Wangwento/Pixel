package com.wwt.pixel.image.service;

import com.wwt.pixel.image.domain.StyleTemplate;
import com.wwt.pixel.image.mapper.StyleTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 风格模板服务
 */
@Service
@RequiredArgsConstructor
public class StyleTemplateService {

    private final StyleTemplateMapper styleTemplateMapper;

    public List<StyleTemplate> getAllTemplates() {
        return styleTemplateMapper.findAll();
    }

    public StyleTemplate getTemplateById(Long id) {
        return styleTemplateMapper.findById(id);
    }

    public StyleTemplate getTemplateByNameEn(String nameEn) {
        return styleTemplateMapper.findByNameEn(nameEn);
    }

    public List<StyleTemplate> getTemplatesByCategory(String category) {
        return styleTemplateMapper.findByCategory(category);
    }
}