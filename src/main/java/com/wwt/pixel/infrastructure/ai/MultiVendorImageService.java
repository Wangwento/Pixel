package com.wwt.pixel.infrastructure.ai;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.domain.model.GenerationRequest;
import com.wwt.pixel.domain.model.GenerationResult;
import com.wwt.pixel.domain.service.ImageGenerationService;
import com.wwt.pixel.infrastructure.ai.adapter.GeminiChatImageAdapter;
import com.wwt.pixel.infrastructure.ai.adapter.OpenAiCompatibleAdapter;
import com.wwt.pixel.infrastructure.ai.config.AiVendorProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class MultiVendorImageService implements ImageGenerationService {

    private final List<ImageModelAdapter> allAdapters = new ArrayList<>();
    private final AiVendorProperties properties;

    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final Random random = new Random();

    @Autowired
    public MultiVendorImageService(
            List<ImageModelAdapter> builtInAdapters,
            @Autowired(required = false) List<ImageModelAdapter> compatibleAdapters,
            AiVendorProperties properties) {
        this.properties = properties;

        // 添加内置适配器
        if (builtInAdapters != null) {
            allAdapters.addAll(builtInAdapters);
        }

        // 添加兼容适配器
        if (compatibleAdapters != null) {
            for (ImageModelAdapter adapter : compatibleAdapters) {
                // 避免重复添加
                if (!allAdapters.contains(adapter)) {
                    allAdapters.add(adapter);
                }
            }
        }
    }

    @PostConstruct
    public void init() {
        log.info("已加载 {} 个AI图片生成适配器", allAdapters.size());
        allAdapters.forEach(a -> {
            String name = getAdapterName(a);
            log.info("  - {} (available: {}, weight: {})", name, a.isAvailable(), a.getWeight());
        });
    }

    private String getAdapterName(ImageModelAdapter adapter) {
        if (adapter instanceof OpenAiCompatibleAdapter) {
            return ((OpenAiCompatibleAdapter) adapter).getVendorName();
        }
        if (adapter instanceof GeminiChatImageAdapter) {
            return ((GeminiChatImageAdapter) adapter).getVendorName();
        }
        return adapter.getVendor().getName();
    }

    private String getAdapterCode(ImageModelAdapter adapter) {
        if (adapter instanceof OpenAiCompatibleAdapter) {
            return ((OpenAiCompatibleAdapter) adapter).getVendorCode();
        }
        return adapter.getVendor().getCode();
    }

    @Override
    public GenerationResult generateImage(GenerationRequest request) {
        if (StringUtils.hasText(request.getVendor())) {
            return generateWithSpecificVendor(request, request.getVendor());
        }
        return generateWithStrategy(request);
    }

    @Override
    public GenerationResult generateImageFromImage(GenerationRequest request) {
        if (!StringUtils.hasText(request.getSourceImageUrl())
                && !StringUtils.hasText(request.getSourceImageBase64())) {
            throw new BusinessException(400, "图生图模式需要提供原图");
        }
        return generateImage(request);
    }

    private GenerationResult generateWithSpecificVendor(GenerationRequest request, String vendorCode) {
        ImageModelAdapter adapter = findAdapterByVendor(vendorCode);
        if (adapter == null || !adapter.isAvailable()) {
            throw new BusinessException(1003, "指定的AI厂商不可用: " + vendorCode);
        }
        return adapter.generate(request);
    }

    private GenerationResult generateWithStrategy(GenerationRequest request) {
        List<ImageModelAdapter> availableAdapters = getAvailableAdapters();
        if (availableAdapters.isEmpty()) {
            throw new BusinessException(1003, "没有可用的AI服务");
        }

        ImageModelAdapter selected = selectAdapter(availableAdapters);
        List<ImageModelAdapter> tried = new ArrayList<>();
        tried.add(selected);

        while (true) {
            try {
                log.info("使用 {} 生成图片", getAdapterName(selected));
                return selected.generate(request);
            } catch (Exception e) {
                log.warn("{} 调用失败: {}", getAdapterName(selected), e.getMessage());

                if (!properties.isFallbackEnabled()) {
                    throw e;
                }

                List<ImageModelAdapter> remaining = availableAdapters.stream()
                        .filter(a -> !tried.contains(a))
                        .toList();

                if (remaining.isEmpty()) {
                    log.error("所有AI服务都调用失败");
                    throw new BusinessException(1003, "所有AI服务都调用失败，请稍后重试");
                }

                selected = selectAdapter(remaining);
                tried.add(selected);
                log.info("降级到 {}", getAdapterName(selected));
            }
        }
    }

    private ImageModelAdapter selectAdapter(List<ImageModelAdapter> adapters) {
        if (adapters.size() == 1) {
            return adapters.get(0);
        }

        return switch (properties.getStrategy().toLowerCase()) {
            case "weighted", "weight" -> selectByWeight(adapters);
            case "random" -> adapters.get(random.nextInt(adapters.size()));
            default -> selectByRoundRobin(adapters);
        };
    }

    private ImageModelAdapter selectByRoundRobin(List<ImageModelAdapter> adapters) {
        int index = roundRobinIndex.getAndIncrement() % adapters.size();
        return adapters.get(index);
    }

    private ImageModelAdapter selectByWeight(List<ImageModelAdapter> adapters) {
        int totalWeight = adapters.stream().mapToInt(ImageModelAdapter::getWeight).sum();
        int randomWeight = random.nextInt(totalWeight);

        int currentWeight = 0;
        for (ImageModelAdapter adapter : adapters) {
            currentWeight += adapter.getWeight();
            if (randomWeight < currentWeight) {
                return adapter;
            }
        }
        return adapters.get(adapters.size() - 1);
    }

    private ImageModelAdapter findAdapterByVendor(String vendorCode) {
        return allAdapters.stream()
                .filter(a -> getAdapterCode(a).equalsIgnoreCase(vendorCode))
                .findFirst()
                .orElse(null);
    }

    private List<ImageModelAdapter> getAvailableAdapters() {
        return allAdapters.stream()
                .filter(ImageModelAdapter::isAvailable)
                .toList();
    }

    public List<String> getAvailableVendors() {
        return getAvailableAdapters().stream()
                .map(this::getAdapterCode)
                .toList();
    }
}
