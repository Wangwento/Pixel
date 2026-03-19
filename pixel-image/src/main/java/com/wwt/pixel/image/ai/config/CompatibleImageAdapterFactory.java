package com.wwt.pixel.image.ai.config;

import com.wwt.pixel.image.ai.ImageModelAdapter;
import com.wwt.pixel.image.ai.adapter.GeminiChatImageAdapter;
import com.wwt.pixel.image.ai.adapter.JsonImageGenerationsAdapter;
import com.wwt.pixel.image.ai.adapter.OpenAiCompatibleAdapter;
import com.wwt.pixel.image.ai.adapter.PlatoImageAdapter;
import org.springframework.util.StringUtils;

import java.util.Locale;

public final class CompatibleImageAdapterFactory {

    private CompatibleImageAdapterFactory() {
    }

    public static ImageModelAdapter createAdapter(AiVendorProperties.CompatibleVendorConfig vendor) {
        String providerModel = resolveProviderModel(vendor);
        String vendorName = StringUtils.hasText(vendor.getName()) ? vendor.getName() : vendor.getCode();
        String modelCode = resolveModelCode(vendor);
        boolean supportsImageInput = supportsImageInput(vendor);
        boolean supportsImageSize = supportsImageSize(vendor);

        if (usesJsonImageGenerationsApi(vendor)) {
            return new JsonImageGenerationsAdapter(
                    vendor.getCode(),
                    vendorName,
                    vendor.getApiKey(),
                    vendor.getBaseUrl(),
                    providerModel,
                    vendor.getWeight(),
                    vendor.getTimeout(),
                    modelCode,
                    vendor.getModelDisplayName(),
                    vendor.getMinVipLevel(),
                    supportsImageInput,
                    supportsImageSize,
                    vendor.getParams(),
                    vendor.getAspectRatio(),
                    vendor.getImageSize()
            );
        }

        if (isGeminiModel(providerModel)) {
            return new GeminiChatImageAdapter(
                    vendor.getCode(),
                    vendorName,
                    vendor.getApiKey(),
                    vendor.getBaseUrl(),
                    providerModel,
                    vendor.getWeight(),
                    vendor.getTimeout(),
                    modelCode,
                    vendor.getModelDisplayName(),
                    vendor.getMinVipLevel()
            );
        }

        if (supportsImageInput) {
            return new PlatoImageAdapter(
                    vendor.getCode(),
                    vendorName,
                    vendor.getApiKey(),
                    vendor.getBaseUrl(),
                    providerModel,
                    vendor.getWeight(),
                    vendor.getTimeout(),
                    modelCode,
                    vendor.getModelDisplayName(),
                    vendor.getMinVipLevel(),
                    vendor.getAspectRatio(),
                    vendor.getImageSize()
            );
        }

        return new OpenAiCompatibleAdapter(
                vendor.getCode(),
                vendorName,
                vendor.getApiKey(),
                vendor.getBaseUrl(),
                providerModel,
                vendor.getWeight(),
                vendor.getTimeout(),
                modelCode,
                vendor.getModelDisplayName(),
                vendor.getMinVipLevel()
        );
    }

    public static String resolveModelCode(AiVendorProperties.CompatibleVendorConfig vendor) {
        return StringUtils.hasText(vendor.getModelCode()) ? vendor.getModelCode() : resolveProviderModel(vendor);
    }

    public static String resolveModelId(AiVendorProperties.CompatibleVendorConfig vendor) {
        return resolveModelCode(vendor);
    }

    public static String resolveProviderModel(AiVendorProperties.CompatibleVendorConfig vendor) {
        return StringUtils.hasText(vendor.getProviderModel()) ? vendor.getProviderModel() : "dall-e-3";
    }

    public static boolean supportsImageInput(AiVendorProperties.CompatibleVendorConfig vendor) {
        String modelName = resolveProviderModel(vendor).toLowerCase(Locale.ROOT);
        String vendorCode = StringUtils.hasText(vendor.getCode())
                ? vendor.getCode().toLowerCase(Locale.ROOT)
                : "";

        return vendor.isSupportsImageInput()
                || modelName.contains("nano-banana")
                || vendorCode.equals("plato")
                || vendorCode.startsWith("plato-");
    }

    public static boolean supportsImageSize(AiVendorProperties.CompatibleVendorConfig vendor) {
        if (vendor.getParams() == null || vendor.getParams().isEmpty()) {
            return false;
        }
        return vendor.getParams().stream()
                .anyMatch(param -> "image_size".equalsIgnoreCase(param.getParamKey()));
    }

    private static boolean usesJsonImageGenerationsApi(AiVendorProperties.CompatibleVendorConfig vendor) {
        String modelName = resolveProviderModel(vendor).toLowerCase(Locale.ROOT);
        return modelName.contains("image-preview")
                || modelName.contains("seedream");
    }

    private static boolean isGeminiModel(String modelName) {
        return modelName.toLowerCase(Locale.ROOT).contains("gemini");
    }
}
