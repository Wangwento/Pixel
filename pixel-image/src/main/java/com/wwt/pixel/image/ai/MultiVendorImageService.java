package com.wwt.pixel.image.ai;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.image.domain.GenerationRequest;
import com.wwt.pixel.image.domain.GenerationResult;
import com.wwt.pixel.image.dto.ModelInfo;
import com.wwt.pixel.image.dto.ModelParamInfo;
import com.wwt.pixel.image.ai.ImageGenerationService;
import com.wwt.pixel.image.ai.adapter.GeminiChatImageAdapter;
import com.wwt.pixel.image.ai.adapter.JsonImageGenerationsAdapter;
import com.wwt.pixel.image.ai.adapter.OpenAiCompatibleAdapter;
import com.wwt.pixel.image.ai.adapter.PlatoImageAdapter;
import com.wwt.pixel.image.ai.config.CompatibleImageAdapterFactory;
import com.wwt.pixel.image.ai.config.AiVendorProperties;
import com.wwt.pixel.image.infrastructure.oss.OssStorageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MultiVendorImageService implements ImageGenerationService {

    // volatile 引用，支持运行时原子替换
    private volatile List<ImageModelAdapter> allAdapters;
    private volatile List<AiVendorProperties.CompatibleVendorConfig> nacosVendorConfigs = new ArrayList<>();

    // 本地缓存：模型参数映射
    private volatile Map<String, List<ModelParamInfo>> cachedModelParamsMap = new LinkedHashMap<>();
    // 本地缓存：模型是否支持图片输入
    private volatile Map<String, Boolean> cachedSupportsImageInputMap = new LinkedHashMap<>();
    // 本地缓存：模型单次生成价格
    private volatile Map<String, BigDecimal> cachedCostPerUnitMap = new LinkedHashMap<>();

    private final AiVendorProperties properties;
    private final OssStorageService ossStorageService;

    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final Random random = new Random();

    @Autowired
    public MultiVendorImageService(
            List<ImageModelAdapter> builtInAdapters,
            @Autowired(required = false) List<ImageModelAdapter> compatibleAdapters,
            AiVendorProperties properties,
            OssStorageService ossStorageService) {
        this.properties = properties;
        this.ossStorageService = ossStorageService;

        List<ImageModelAdapter> initial = new ArrayList<>();
        if (builtInAdapters != null) {
            initial.addAll(builtInAdapters);
        }
        if (compatibleAdapters != null) {
            for (ImageModelAdapter adapter : compatibleAdapters) {
                if (!initial.contains(adapter)) {
                    initial.add(adapter);
                }
            }
        }
        this.allAdapters = Collections.unmodifiableList(initial);
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
        if (adapter instanceof OpenAiCompatibleAdapter a) {
            return a.getVendorName();
        }
        if (adapter instanceof GeminiChatImageAdapter a) {
            return a.getVendorName();
        }
        if (adapter instanceof JsonImageGenerationsAdapter a) {
            return a.getVendorName();
        }
        if (adapter instanceof PlatoImageAdapter a) {
            return a.getVendorName();
        }
        return adapter.getVendor().getName();
    }

    private String getAdapterCode(ImageModelAdapter adapter) {
        if (adapter instanceof OpenAiCompatibleAdapter a) {
            return a.getVendorCode();
        }
        if (adapter instanceof JsonImageGenerationsAdapter a) {
            return a.getVendorCode();
        }
        if (adapter instanceof PlatoImageAdapter a) {
            return a.getVendorCode();
        }
        return adapter.getVendor().getCode();
    }

    @Override
    public GenerationResult generateImage(GenerationRequest request) {
        return generateInternal(request, false);
    }

    @Override
    public GenerationResult generateImageFromImage(GenerationRequest request) {
        if (!request.hasSourceImages()) {
            throw new BusinessException(400, "图生图模式需要提供原图");
        }
        return generateInternal(request, true);
    }

    private GenerationResult generateInternal(GenerationRequest request, boolean requireImageInputSupport) {
        if (StringUtils.hasText(request.getModelId())) {
            return generateByModelId(request, request.getModelId(), requireImageInputSupport);
        }
        if (StringUtils.hasText(request.getVendor())) {
            return generateWithSpecificVendor(request, request.getVendor(), requireImageInputSupport);
        }
        return generateWithStrategy(request, requireImageInputSupport);
    }

    private GenerationResult generateWithSpecificVendor(GenerationRequest request, String vendorCode,
                                                        boolean requireImageInputSupport) {
        ImageModelAdapter adapter = findAdapterByVendor(vendorCode);
        if (adapter == null || !adapter.isAvailable()) {
            throw new BusinessException(1003, "指定的AI厂商不可用: " + vendorCode);
        }
        if (requireImageInputSupport && !adapter.supportsImageInput()) {
            throw new BusinessException(1003, "指定的模型暂不支持图生图: " + vendorCode);
        }
        GenerationResult result = adapter.generate(request);
        return uploadToOss(result);
    }

    private GenerationResult generateWithStrategy(GenerationRequest request, boolean requireImageInputSupport) {
        List<ImageModelAdapter> availableAdapters = getAvailableAdapters(requireImageInputSupport);
        if (availableAdapters.isEmpty()) {
            throw new BusinessException(1003,
                    requireImageInputSupport ? "当前没有支持图生图的AI服务" : "没有可用的AI服务");
        }

        ImageModelAdapter selected = selectAdapter(availableAdapters);
        List<ImageModelAdapter> tried = new ArrayList<>();
        tried.add(selected);

        while (true) {
            try {
                log.info("使用 {} 生成图片", getAdapterName(selected));
                GenerationResult result = selected.generate(request);
                return uploadToOss(result);
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

    private List<ImageModelAdapter> getAvailableAdapters(boolean requireImageInputSupport) {
        return allAdapters.stream()
                .filter(ImageModelAdapter::isAvailable)
                .filter(adapter -> !requireImageInputSupport || adapter.supportsImageInput())
                .toList();
    }

    public List<String> getAvailableVendors() {
        return getAvailableAdapters(false).stream()
                .map(this::getAdapterCode)
                .toList();
    }

    /**
     * 获取模型列表（按modelId去重，标记当前用户是否可用）
     */
    public List<ModelInfo> getModelList(int userVipLevel) {
        Map<String, ModelInfo> modelMap = new LinkedHashMap<>();

        // 使用本地缓存的数据
        Map<String, List<ModelParamInfo>> modelParamsMap = cachedModelParamsMap;
        Map<String, Boolean> modelSupportsImageInputMap = cachedSupportsImageInputMap;
        Map<String, BigDecimal> modelCostPerUnitMap = cachedCostPerUnitMap;

        for (ImageModelAdapter adapter : allAdapters) {
            if (!adapter.isAvailable()) continue;
            String mid = adapter.getModelId();
            if (mid == null || mid.isBlank()) continue;
            ModelInfo existing = modelMap.get(mid);
            boolean supportsImageInput = modelSupportsImageInputMap.getOrDefault(mid, adapter.supportsImageInput());
            if (existing == null) {
                modelMap.put(mid, ModelInfo.builder()
                        .modelId(mid)
                        .displayName(adapter.getModelDisplayName())
                        .minVipLevel(adapter.getMinVipLevel())
                        .available(userVipLevel >= adapter.getMinVipLevel())
                        .costPerUnit(modelCostPerUnitMap.get(mid))
                        .supportsImageInput(supportsImageInput)
                        .params(modelParamsMap.get(mid))
                        .build());
                continue;
            }
            existing.setAvailable(existing.isAvailable() || userVipLevel >= adapter.getMinVipLevel());
            existing.setMinVipLevel(Math.min(existing.getMinVipLevel(), adapter.getMinVipLevel()));
            existing.setSupportsImageInput(existing.isSupportsImageInput() || supportsImageInput);
            if (!StringUtils.hasText(existing.getDisplayName()) && StringUtils.hasText(adapter.getModelDisplayName())) {
                existing.setDisplayName(adapter.getModelDisplayName());
            }
            if (existing.getParams() == null && modelParamsMap.get(mid) != null) {
                existing.setParams(modelParamsMap.get(mid));
            }
            if ((existing.getCostPerUnit() == null || existing.getCostPerUnit().signum() <= 0)
                    && modelCostPerUnitMap.get(mid) != null) {
                existing.setCostPerUnit(modelCostPerUnitMap.get(mid));
            }
        }
        return new ArrayList<>(modelMap.values());
    }

    /**
     * 根据modelId筛选对应的vendors，再按策略选一个执行
     */
    private GenerationResult generateByModelId(GenerationRequest request, String modelId,
                                               boolean requireImageInputSupport) {
        List<ImageModelAdapter> candidates = allAdapters.stream()
                .filter(ImageModelAdapter::isAvailable)
                .filter(a -> modelId.equals(a.getModelId()))
                .filter(a -> !requireImageInputSupport || a.supportsImageInput())
                .toList();
        if (candidates.isEmpty()) {
            throw new BusinessException(1003,
                    requireImageInputSupport
                            ? "指定的模型暂不支持图生图: " + modelId
                            : "指定的模型不可用: " + modelId);
        }
        ImageModelAdapter selected = selectAdapter(candidates);
        List<ImageModelAdapter> tried = new ArrayList<>();
        tried.add(selected);
        while (true) {
            try {
                log.info("使用 {} 生成图片 (modelId={})", getAdapterName(selected), modelId);
                GenerationResult result = selected.generate(request);
                return uploadToOss(result);
            } catch (Exception e) {
                log.warn("{} 调用失败: {}", getAdapterName(selected), e.getMessage());
                if (!properties.isFallbackEnabled()) throw e;
                List<ImageModelAdapter> remaining = candidates.stream()
                        .filter(a -> !tried.contains(a)).toList();
                if (remaining.isEmpty()) {
                    throw new BusinessException(1003, "模型 " + modelId + " 所有代理商都调用失败");
                }
                selected = selectAdapter(remaining);
                tried.add(selected);
                log.info("降级到 {}", getAdapterName(selected));
            }
        }
    }

    /**
     * 动态刷新厂商列表（由Nacos配置变更触发）
     * 整体替换 allAdapters 引用，保证线程安全
     */
    public void refreshVendors(List<AiVendorProperties.CompatibleVendorConfig> vendorConfigs) {
        // 保存配置供 getModelList 使用
        this.nacosVendorConfigs = new ArrayList<>(vendorConfigs);

        // 构建本地缓存
        Map<String, List<ModelParamInfo>> paramsMap = new LinkedHashMap<>();
        Map<String, Boolean> supportsInputMap = new LinkedHashMap<>();
        Map<String, BigDecimal> costPerUnitMap = new LinkedHashMap<>();

        for (AiVendorProperties.CompatibleVendorConfig vendor : vendorConfigs) {
            String modelId = CompatibleImageAdapterFactory.resolveModelCode(vendor);
            if (!StringUtils.hasText(modelId)) {
                continue;
            }

            // 缓存 supportsImageInput
            supportsInputMap.merge(modelId,
                    CompatibleImageAdapterFactory.supportsImageInput(vendor),
                    Boolean::logicalOr);

            BigDecimal costPerUnit = vendor.getCostPerUnit();
            if (costPerUnit != null) {
                costPerUnitMap.merge(modelId, costPerUnit, (existing, incoming) -> {
                    if (existing == null || existing.signum() <= 0) {
                        return incoming;
                    }
                    if (incoming == null || incoming.signum() <= 0) {
                        return existing;
                    }
                    return existing.min(incoming);
                });
            }

            // 缓存参数
            if (vendor.getParams() != null && !vendor.getParams().isEmpty()) {
                List<ModelParamInfo> paramInfos = vendor.getParams().stream()
                    .map(p -> ModelParamInfo.builder()
                        .paramKey(p.getParamKey())
                        .paramName(p.getParamName())
                        .paramType(p.getParamType())
                        .required(p.getRequired())
                        .visible(p.getVisible())
                        .defaultValue(p.getDefaultValue())
                        .options(p.getOptions())
                        .validationRule(p.getValidationRule())
                        .description(p.getDescription())
                        .displayOrder(p.getDisplayOrder())
                        .build())
                    .collect(Collectors.toList());
                paramsMap.put(modelId, paramInfos);
            }
        }

        // 原子更新缓存
        this.cachedModelParamsMap = paramsMap;
        this.cachedSupportsImageInputMap = supportsInputMap;
        this.cachedCostPerUnitMap = costPerUnitMap;

        log.info("已更新本地缓存: {} 个模型配置", vendorConfigs.size());

        List<ImageModelAdapter> newAdapters = new ArrayList<>();
        for (AiVendorProperties.CompatibleVendorConfig vendor : vendorConfigs) {
            if (!vendor.isEnabled()) {
                log.debug("跳过禁用厂商: {}", vendor.getCode());
                continue;
            }
            if (!StringUtils.hasText(vendor.getApiKey()) || !StringUtils.hasText(vendor.getBaseUrl())) {
                log.debug("厂商 {} 缺少apiKey/baseUrl，跳过", vendor.getCode());
                continue;
            }

            ImageModelAdapter adapter = CompatibleImageAdapterFactory.createAdapter(vendor);
            newAdapters.add(adapter);
        }

        this.allAdapters = Collections.unmodifiableList(newAdapters);
        roundRobinIndex.set(0);
        log.info("厂商列表已热刷新，当前 {} 个适配器", newAdapters.size());
        newAdapters.forEach(a -> log.info("  - {} (weight: {})", getAdapterName(a), a.getWeight()));
    }

    /**
     * 上传图片到OSS
     */
    private GenerationResult uploadToOss(GenerationResult result) {
        if (!ossStorageService.isEnabled()) {
            log.debug("OSS未启用，跳过上传");
            return result;
        }

        try {
            normalizePrimaryImageFields(result);

            List<String> ossUrls = new ArrayList<>();
            List<String> imageBase64List = result.getImageBase64List();
            List<String> imageUrlList = result.getImageUrls();

            if (imageBase64List != null && !imageBase64List.isEmpty()) {
                for (String imageBase64 : imageBase64List) {
                    if (!StringUtils.hasText(imageBase64)) {
                        continue;
                    }
                    String uploaded = ossStorageService.uploadBase64Image(imageBase64);
                    if (uploaded != null) {
                        ossUrls.add(uploaded);
                    }
                }
            } else if (imageUrlList != null && !imageUrlList.isEmpty()) {
                for (String imageUrl : imageUrlList) {
                    if (!StringUtils.hasText(imageUrl)) {
                        continue;
                    }
                    String uploaded = ossStorageService.uploadFromUrl(imageUrl);
                    if (uploaded != null) {
                        ossUrls.add(uploaded);
                    }
                }
            } else if (StringUtils.hasText(result.getImageBase64())) {
                String uploaded = ossStorageService.uploadBase64Image(result.getImageBase64());
                if (uploaded != null) {
                    ossUrls.add(uploaded);
                }
            } else if (StringUtils.hasText(result.getImageUrl())) {
                String uploaded = ossStorageService.uploadFromUrl(result.getImageUrl());
                if (uploaded != null) {
                    ossUrls.add(uploaded);
                }
            }

            if (!ossUrls.isEmpty()) {
                result.setOssUrls(ossUrls);
                result.setOssUrl(ossUrls.get(0));
                log.info("图片已上传到OSS: {}", ossUrls);
            }
        } catch (Exception e) {
            log.error("上传图片到OSS失败", e);
            // 不影响主流程，继续返回原始结果
        }

        return result;
    }

    private void normalizePrimaryImageFields(GenerationResult result) {
        if ((result.getImageUrls() == null || result.getImageUrls().isEmpty())
                && StringUtils.hasText(result.getImageUrl())) {
            result.setImageUrls(List.of(result.getImageUrl()));
        } else if (!StringUtils.hasText(result.getImageUrl())
                && result.getImageUrls() != null
                && !result.getImageUrls().isEmpty()) {
            result.setImageUrl(result.getImageUrls().get(0));
        }

        if ((result.getImageBase64List() == null || result.getImageBase64List().isEmpty())
                && StringUtils.hasText(result.getImageBase64())) {
            result.setImageBase64List(List.of(result.getImageBase64()));
        } else if (!StringUtils.hasText(result.getImageBase64())
                && result.getImageBase64List() != null
                && !result.getImageBase64List().isEmpty()) {
            result.setImageBase64(result.getImageBase64List().get(0));
        }

        if (!StringUtils.hasText(result.getOssUrl())
                && result.getOssUrls() != null
                && !result.getOssUrls().isEmpty()) {
            result.setOssUrl(result.getOssUrls().get(0));
        }
    }
}
