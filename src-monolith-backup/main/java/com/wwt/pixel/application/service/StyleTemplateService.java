package com.wwt.pixel.application.service;

import com.wwt.pixel.domain.model.StyleTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StyleTemplateService {

    private static final List<StyleTemplate> TEMPLATES = new ArrayList<>();

    static {
        TEMPLATES.add(StyleTemplate.builder()
                .id(1L)
                .name("赛博朋克")
                .nameEn("cyberpunk")
                .description("霓虹灯光、科技感十足")
                .promptTemplate("cyberpunk style portrait, neon lights, futuristic, high tech, {prompt}")
                .category("科幻")
                .sortOrder(1)
                .build());

        TEMPLATES.add(StyleTemplate.builder()
                .id(2L)
                .name("国潮风")
                .nameEn("guochao")
                .description("中国传统元素与现代潮流结合")
                .promptTemplate("Chinese traditional style portrait, guochao, modern fusion, {prompt}")
                .category("传统")
                .sortOrder(2)
                .build());

        TEMPLATES.add(StyleTemplate.builder()
                .id(3L)
                .name("日系动漫")
                .nameEn("anime")
                .description("日本动漫风格头像")
                .promptTemplate("anime style portrait, Japanese animation, detailed, {prompt}")
                .category("动漫")
                .sortOrder(3)
                .build());

        TEMPLATES.add(StyleTemplate.builder()
                .id(4L)
                .name("油画风格")
                .nameEn("oil-painting")
                .description("经典油画艺术风格")
                .promptTemplate("oil painting style portrait, classical art, masterpiece, {prompt}")
                .category("艺术")
                .sortOrder(4)
                .build());

        TEMPLATES.add(StyleTemplate.builder()
                .id(5L)
                .name("极简头像")
                .nameEn("minimalist")
                .description("简洁现代的极简风格")
                .promptTemplate("minimalist portrait, clean lines, simple colors, modern, {prompt}")
                .category("简约")
                .sortOrder(5)
                .build());

        TEMPLATES.add(StyleTemplate.builder()
                .id(6L)
                .name("像素艺术")
                .nameEn("pixel-art")
                .description("复古游戏像素风格")
                .promptTemplate("pixel art style portrait, 8-bit, retro game style, {prompt}")
                .category("复古")
                .sortOrder(6)
                .build());

        TEMPLATES.add(StyleTemplate.builder()
                .id(7L)
                .name("水彩画")
                .nameEn("watercolor")
                .description("柔和唯美的水彩效果")
                .promptTemplate("watercolor painting portrait, soft colors, artistic, {prompt}")
                .category("艺术")
                .sortOrder(7)
                .build());

        TEMPLATES.add(StyleTemplate.builder()
                .id(8L)
                .name("3D卡通")
                .nameEn("3d-cartoon")
                .description("皮克斯风格3D卡通头像")
                .promptTemplate("3D cartoon portrait, Pixar style, cute, detailed rendering, {prompt}")
                .category("卡通")
                .sortOrder(8)
                .build());
    }

    public List<StyleTemplate> getAllTemplates() {
        return new ArrayList<>(TEMPLATES);
    }

    public StyleTemplate getTemplateById(Long id) {
        return TEMPLATES.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public StyleTemplate getTemplateByNameEn(String nameEn) {
        return TEMPLATES.stream()
                .filter(t -> t.getNameEn().equalsIgnoreCase(nameEn))
                .findFirst()
                .orElse(null);
    }

    public List<StyleTemplate> getTemplatesByCategory(String category) {
        return TEMPLATES.stream()
                .filter(t -> t.getCategory().equals(category))
                .toList();
    }
}
