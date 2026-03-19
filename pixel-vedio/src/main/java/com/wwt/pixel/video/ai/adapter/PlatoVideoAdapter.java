package com.wwt.pixel.video.ai.adapter;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.video.ai.VideoModelAdapter;
import com.wwt.pixel.video.ai.VideoVendor;
import com.wwt.pixel.video.ai.config.AiVendorProperties;
import com.wwt.pixel.video.domain.VideoGenerationRequest;
import com.wwt.pixel.video.domain.VideoSubmitResult;
import com.wwt.pixel.video.domain.VideoTaskResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * 柏拉图视频适配器，兼容 Sora / Veo 模型族。
 */
@Slf4j
public class PlatoVideoAdapter implements VideoModelAdapter {

    private static final String SUBMIT_PATH = "/v2/videos/generations";
    private static final String DEFAULT_VIDEO_DURATION = "8";

    private final String vendorCode;
    private final String vendorName;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int weight;
    private final int timeoutMs;
    private final String modelId;
    private final String modelDisplayName;
    private final int minVipLevel;
    private final String aspectRatio;
    private final String duration;
    private final BigDecimal costPerSecond;
    private final Boolean hd;
    private final Boolean watermark;
    private final Boolean privateMode;
    private final String notifyHook;
    private final RestTemplate restTemplate;
    private final CapabilityProfile profile;

    public PlatoVideoAdapter(String vendorCode,
                             String vendorName,
                             AiVendorProperties.CompatibleVendorConfig config) {
        this.vendorCode = vendorCode;
        this.vendorName = vendorName;
        this.apiKey = config.getApiKey();
        this.baseUrl = config.getBaseUrl();
        this.model = StringUtils.hasText(config.getModel()) ? config.getModel() : "sora-2";
        this.weight = config.getWeight() > 0 ? config.getWeight() : 1;
        this.timeoutMs = config.getTimeout() > 0 ? config.getTimeout() : 600000;
        this.modelId = StringUtils.hasText(config.getModelId()) ? config.getModelId() : this.model;
        this.modelDisplayName = StringUtils.hasText(config.getModelDisplayName())
                ? config.getModelDisplayName()
                : buildDisplayName(this.modelId);
        this.minVipLevel = config.getMinVipLevel();
        this.aspectRatio = config.getAspectRatio();
        this.duration = config.getDuration();
        this.costPerSecond = config.getCostPerSecond() == null || config.getCostPerSecond().signum() < 0
                ? BigDecimal.ZERO
                : config.getCostPerSecond();
        this.hd = config.getHd();
        this.watermark = config.getWatermark();
        this.privateMode = config.getPrivateMode();
        this.notifyHook = config.getNotifyHook();
        this.profile = resolveProfile(config);
        this.restTemplate = createRestTemplate(this.timeoutMs);
    }

    @Override
    public VideoVendor getVendor() {
        VideoVendor vendor = VideoVendor.fromCode(vendorCode);
        return vendor != null ? vendor : VideoVendor.PLATO;
    }

    @Override
    public String getVendorCode() {
        return vendorCode;
    }

    @Override
    public String getVendorName() {
        return vendorName;
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(baseUrl);
    }

    @Override
    public boolean supportsTextToVideo() {
        return profile.textToVideoSupported();
    }

    @Override
    public boolean supportsImageInput() {
        return profile.imageInputSupported();
    }

    @Override
    public boolean supportsHd() {
        return profile.hdSupported();
    }

    @Override
    public boolean supportsEnhancePrompt() {
        return profile.enhancePromptSupported();
    }

    @Override
    public boolean supportsUpsample() {
        return profile.upsampleSupported();
    }

    @Override
    public String getModelFamily() {
        return profile.family();
    }

    @Override
    public List<String> getSupportedAspectRatios() {
        return profile.supportedAspectRatios();
    }

    @Override
    public List<String> getSupportedTextDurations() {
        return profile.supportedTextDurations();
    }

    @Override
    public List<String> getSupportedImageDurations() {
        return profile.supportedImageDurations();
    }

    @Override
    public int getMinImageCount() {
        return profile.minImageCount();
    }

    @Override
    public int getMaxImageCount() {
        return profile.maxImageCount();
    }

    @Override
    public String getDefaultAspectRatio() {
        return profile.defaultAspectRatio();
    }

    @Override
    public String getDefaultTextDuration() {
        return profile.defaultTextDuration();
    }

    @Override
    public String getDefaultImageDuration() {
        return profile.defaultImageDuration();
    }

    @Override
    public boolean isDefaultHdEnabled() {
        return profile.defaultHd();
    }

    @Override
    public boolean isDefaultEnhancePromptEnabled() {
        return profile.defaultEnhancePrompt();
    }

    @Override
    public boolean isDefaultUpsampleEnabled() {
        return profile.defaultUpsample();
    }

    @Override
    public BigDecimal getCostPerSecond() {
        return costPerSecond;
    }

    @Override
    public VideoSubmitResult submitTask(VideoGenerationRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            boolean imageMode = request.hasSourceImages();
            if (imageMode && !supportsImageInput()) {
                throw new BusinessException(400, "当前模型暂不支持图生视频");
            }
            if (!imageMode && !supportsTextToVideo()) {
                throw new BusinessException(400, "当前模型暂不支持文生视频");
            }

            Map<String, Object> body = isVeoFamily()
                    ? buildVeoRequestBody(request, imageMode)
                    : buildSoraRequestBody(request, imageMode);

            ResponseEntity<Map> response = restTemplate.exchange(
                    buildSubmitUri(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, buildHeaders()),
                    Map.class);

            String providerTaskId = extractTaskId(response.getBody());
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[{}] 视频任务提交成功, providerTaskId={}, model={}, 耗时={}ms",
                    vendorCode, providerTaskId, model, elapsed);

            return VideoSubmitResult.builder()
                    .providerTaskId(providerTaskId)
                    .taskStatus("SUBMITTED")
                    .vendor(vendorCode)
                    .model(model)
                    .message("视频任务已提交")
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] 视频任务提交失败, model={}", vendorCode, model, e);
            throw new BusinessException(1003, vendorName + "服务调用失败: " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public VideoTaskResult queryTask(String providerTaskId) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    buildQueryUri(providerTaskId),
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null || body.isEmpty()) {
                throw new BusinessException(1003, "查询结果为空");
            }

            String status = firstNonBlank(stringValue(body.get("status")), "UNKNOWN");
            String taskId = stringValue(body.get("task_id"));
            String progress = stringValue(body.get("progress"));
            String failReason = stringValue(body.get("fail_reason"));
            String videoUrl = extractVideoUrl(body.get("data"));

            return VideoTaskResult.builder()
                    .providerTaskId(StringUtils.hasText(taskId) ? taskId : providerTaskId)
                    .taskStatus(status)
                    .progress(progress)
                    .failReason(failReason)
                    .videoUrl(videoUrl)
                    .vendor(vendorCode)
                    .model(model)
                    .submitTime(longValue(body.get("submit_time")))
                    .startTime(longValue(body.get("start_time")))
                    .finishTime(longValue(body.get("finish_time")))
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] 柏拉图视频任务查询失败, providerTaskId={}", vendorCode, providerTaskId, e);
            throw new BusinessException(1003, vendorName + "任务查询失败: " + e.getMessage());
        }
    }

    @Override
    public int getWeight() {
        return weight;
    }

    @Override
    public int getTimeoutMs() {
        return timeoutMs;
    }

    @Override
    public String getModelId() {
        return modelId;
    }

    @Override
    public String getModelDisplayName() {
        return modelDisplayName;
    }

    @Override
    public int getMinVipLevel() {
        return minVipLevel;
    }

    private RestTemplate createRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(timeoutMs, 30000));
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private URI buildSubmitUri() {
        return UriComponentsBuilder.fromHttpUrl(trimTrailingSlash(baseUrl))
                .path(SUBMIT_PATH)
                .build(true)
                .toUri();
    }

    private URI buildQueryUri(String providerTaskId) {
        return UriComponentsBuilder.fromHttpUrl(trimTrailingSlash(baseUrl))
                .path(SUBMIT_PATH)
                .pathSegment(providerTaskId)
                .build(true)
                .toUri();
    }

    private Map<String, Object> buildSoraRequestBody(VideoGenerationRequest request, boolean imageMode) {
        String resolvedAspectRatio = resolveAspectRatio(request.getAspectRatio());
        String resolvedDuration = resolveDuration(request.getDuration(), imageMode);
        boolean resolvedHd = resolveSoraHd(request.getHd(), resolvedDuration);
        Boolean resolvedWatermark = request.getWatermark() != null ? request.getWatermark() : watermark;
        Boolean resolvedPrivateMode = request.getPrivateMode() != null ? request.getPrivateMode() : privateMode;
        String resolvedNotifyHook = firstNonBlank(request.getNotifyHook(), notifyHook);

        validateSoraRequest(resolvedAspectRatio, resolvedDuration, resolvedHd, imageMode);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", request.getPrompt());
        body.put("model", model);
        if (StringUtils.hasText(resolvedAspectRatio)) {
            body.put("aspect_ratio", resolvedAspectRatio);
        }
        if (StringUtils.hasText(resolvedDuration)) {
            body.put("duration", resolvedDuration);
        }
        body.put("hd", resolvedHd);
        body.put("watermark", Boolean.TRUE.equals(resolvedWatermark));
        body.put("private", Boolean.TRUE.equals(resolvedPrivateMode));
        if (StringUtils.hasText(resolvedNotifyHook)) {
            body.put("notify_hook", resolvedNotifyHook);
        }
        if (imageMode) {
            body.put("images", resolveSoraImages(request));
        }
        return body;
    }

    private Map<String, Object> buildVeoRequestBody(VideoGenerationRequest request, boolean imageMode) {
        String resolvedAspectRatio = resolveAspectRatio(request.getAspectRatio());
        String resolvedDuration = resolveDuration(request.getDuration(), imageMode);
        boolean resolvedEnhancePrompt = supportsEnhancePrompt()
                && (request.getEnhancePrompt() != null
                ? request.getEnhancePrompt()
                : isDefaultEnhancePromptEnabled());
        boolean resolvedUpsample = supportsUpsample()
                && (request.getEnableUpsample() != null
                ? request.getEnableUpsample()
                : isDefaultUpsampleEnabled());

        validateVeoRequest(request, resolvedAspectRatio, resolvedDuration, imageMode);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", request.getPrompt());
        body.put("model", model);
        if (StringUtils.hasText(resolvedAspectRatio)) {
            body.put("aspect_ratio", resolvedAspectRatio);
        }
        if (StringUtils.hasText(resolvedDuration)) {
            body.put("duration", resolvedDuration);
        }
        if (supportsEnhancePrompt()) {
            body.put("enhance_prompt", resolvedEnhancePrompt);
        }
        if (imageMode) {
            body.put("images", validateAndResolveImages(request));
        } else if (supportsUpsample()) {
            body.put("enable_upsample", resolvedUpsample);
        }
        return body;
    }

    private void validateSoraRequest(String resolvedAspectRatio,
                                     String resolvedDuration,
                                     boolean resolvedHd,
                                     boolean imageMode) {
        validateAspectRatio(resolvedAspectRatio);
        List<String> supportedDurations = imageMode ? getSupportedImageDurations() : getSupportedTextDurations();
        if (!StringUtils.hasText(resolvedDuration)) {
            throw new BusinessException(400, "请选择视频时长");
        }
        if (!supportedDurations.contains(resolvedDuration)) {
            throw new BusinessException(400, "当前模型支持的时长为: " + String.join("/", supportedDurations));
        }
        if (resolvedHd && !supportsHd()) {
            throw new BusinessException(400, "当前模型不支持 HD");
        }
    }

    private void validateVeoRequest(VideoGenerationRequest request,
                                    String resolvedAspectRatio,
                                    String resolvedDuration,
                                    boolean imageMode) {
        validateAspectRatio(resolvedAspectRatio);
        List<String> supportedDurations = imageMode ? getSupportedImageDurations() : getSupportedTextDurations();
        if (!supportedDurations.isEmpty()) {
            if (!StringUtils.hasText(resolvedDuration)) {
                throw new BusinessException(400, "请选择视频时长");
            }
            if (!supportedDurations.contains(resolvedDuration)) {
                throw new BusinessException(400, "当前模型支持的时长为: " + String.join("/", supportedDurations));
            }
        }
        if (Boolean.TRUE.equals(request.getHd())) {
            throw new BusinessException(400, "当前模型不支持 HD 参数");
        }
        if (imageMode) {
            validateImageCount(validateAndResolveImages(request).size());
        }
    }

    private void validateAspectRatio(String resolvedAspectRatio) {
        if (StringUtils.hasText(resolvedAspectRatio)
                && !getSupportedAspectRatios().isEmpty()
                && !getSupportedAspectRatios().contains(resolvedAspectRatio)) {
            throw new BusinessException(400, "aspectRatio 仅支持: " + String.join("/", getSupportedAspectRatios()));
        }
    }

    private void validateImageCount(int imageCount) {
        if (imageCount < getMinImageCount()) {
            throw new BusinessException(400, "当前模型至少需要上传 " + getMinImageCount() + " 张参考图");
        }
        if (getMaxImageCount() > 0 && imageCount > getMaxImageCount()) {
            throw new BusinessException(400, "当前模型最多支持上传 " + getMaxImageCount() + " 张参考图");
        }
    }

    private List<String> validateAndResolveImages(VideoGenerationRequest request) {
        List<String> images = request.resolveSourceImages();
        if (images.isEmpty()) {
            throw new BusinessException(400, "请至少上传一张参考图片");
        }
        validateImageCount(images.size());
        return images;
    }

    private List<String> resolveSoraImages(VideoGenerationRequest request) {
        List<String> images = validateAndResolveImages(request);
        List<String> resolvedImages = new ArrayList<>(images.size());
        int convertedCount = 0;

        for (String image : images) {
            if (!isUrl(image)) {
                resolvedImages.add(image);
                continue;
            }
            try {
                resolvedImages.add(downloadImageAsBase64(image));
                convertedCount++;
            } catch (Exception ex) {
                log.warn("[{}] Sora参考图转base64失败，回退URL方式: {}, 原因: {}",
                        vendorCode, image, ex.getMessage());
                resolvedImages.add(image);
            }
        }

        if (convertedCount > 0) {
            log.info("[{}] Sora图生视频已将 {} 张参考图URL转换为base64后提交", vendorCode, convertedCount);
        }
        return resolvedImages;
    }

    private String downloadImageAsBase64(String imageUrl) {
        ResponseEntity<byte[]> response = restTemplate.exchange(
                imageUrl,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                byte[].class);
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new BusinessException(400, "参考图下载为空: " + imageUrl);
        }
        return Base64.getEncoder().encodeToString(body);
    }

    private String resolveAspectRatio(String requestAspectRatio) {
        String resolved = firstNonBlank(requestAspectRatio, getDefaultAspectRatio());
        if (!StringUtils.hasText(resolved) && !getSupportedAspectRatios().isEmpty()) {
            return getSupportedAspectRatios().get(0);
        }
        return resolved;
    }

    private String resolveDuration(String requestDuration, boolean imageMode) {
        List<String> supportedDurations = imageMode ? getSupportedImageDurations() : getSupportedTextDurations();
        String configuredDefaultDuration = imageMode ? getDefaultImageDuration() : getDefaultTextDuration();
        String resolvedDuration = StringUtils.hasText(requestDuration)
                ? requestDuration.trim()
                : firstNonBlank(configuredDefaultDuration, DEFAULT_VIDEO_DURATION);
        if (supportedDurations.isEmpty()) {
            return resolvedDuration;
        }
        return resolvedDuration;
    }

    private boolean isUrl(String value) {
        return StringUtils.hasText(value)
                && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private boolean resolveSoraHd(Boolean requestHd, String resolvedDuration) {
        boolean value = requestHd != null ? requestHd : isDefaultHdEnabled();
        if ("25".equals(resolvedDuration)) {
            return false;
        }
        return value;
    }

    private boolean isVeoFamily() {
        return "veo".equalsIgnoreCase(profile.family());
    }

    private CapabilityProfile resolveProfile(AiVendorProperties.CompatibleVendorConfig config) {
        CapabilityProfile inferred = inferProfile();
        boolean supportsTextToVideo = overrideBoolean(config.getSupportsTextToVideo(), inferred.textToVideoSupported());
        boolean supportsImageInput = overrideBoolean(config.getSupportsImageInput(), inferred.imageInputSupported());
        boolean supportsHd = overrideBoolean(config.getSupportsHd(), inferred.hdSupported());
        boolean supportsEnhancePrompt = overrideBoolean(config.getSupportsEnhancePrompt(), inferred.enhancePromptSupported());
        boolean supportsUpsample = overrideBoolean(config.getSupportsUpsample(), inferred.upsampleSupported());

        List<String> paramAspectRatios = resolveParamOptions(config, "aspect_ratio", "aspectRatio");
        List<String> paramDurations = resolveParamOptions(config, "duration");
        String paramDefaultAspectRatio = resolveParamDefaultValue(config, "aspect_ratio", "aspectRatio");
        String paramDefaultDuration = resolveParamDefaultValue(config, "duration");

        List<String> supportedAspectRatios = resolveList(
                config.getSupportedAspectRatios(),
                paramAspectRatios.isEmpty() ? inferred.supportedAspectRatios() : paramAspectRatios);
        List<String> supportedTextDurations = supportsTextToVideo
                ? resolveList(
                        config.getSupportedTextDurations(),
                        paramDurations.isEmpty() ? inferred.supportedTextDurations() : paramDurations)
                : List.of();
        List<String> supportedImageDurations = supportsImageInput
                ? resolveList(
                        config.getSupportedImageDurations(),
                        paramDurations.isEmpty() ? inferred.supportedImageDurations() : paramDurations)
                : List.of();

        int minImageCount = supportsImageInput
                ? (config.getMinImageCount() != null ? Math.max(config.getMinImageCount(), 1) : inferred.minImageCount())
                : 0;
        int maxImageCount = supportsImageInput
                ? (config.getMaxImageCount() != null ? config.getMaxImageCount() : inferred.maxImageCount())
                : 0;
        if (supportsImageInput && maxImageCount > 0 && maxImageCount < minImageCount) {
            maxImageCount = minImageCount;
        }

        String defaultAspectRatio = sanitizeDefault(
                firstNonBlank(firstNonBlank(config.getAspectRatio(), paramDefaultAspectRatio), inferred.defaultAspectRatio()),
                supportedAspectRatios);
        String defaultTextDuration = supportsTextToVideo && !supportedTextDurations.isEmpty()
                ? sanitizeDefault(
                        firstNonBlank(firstNonBlank(config.getDuration(), paramDefaultDuration), DEFAULT_VIDEO_DURATION),
                        supportedTextDurations)
                : null;
        String defaultImageDuration = supportsImageInput && !supportedImageDurations.isEmpty()
                ? sanitizeDefault(
                        firstNonBlank(firstNonBlank(config.getDuration(), paramDefaultDuration), DEFAULT_VIDEO_DURATION),
                        supportedImageDurations)
                : null;
        boolean defaultHd = supportsHd && Boolean.TRUE.equals(config.getHd());
        boolean defaultEnhancePrompt = supportsEnhancePrompt && Boolean.TRUE.equals(config.getEnhancePrompt());
        boolean defaultUpsample = supportsTextToVideo && supportsUpsample && Boolean.TRUE.equals(config.getEnableUpsample());

        return new CapabilityProfile(
                inferred.family(),
                supportsTextToVideo,
                supportsImageInput,
                supportsHd,
                supportsEnhancePrompt,
                supportsUpsample,
                supportedAspectRatios,
                supportedTextDurations,
                supportedImageDurations,
                minImageCount,
                maxImageCount,
                defaultAspectRatio,
                defaultTextDuration,
                defaultImageDuration,
                defaultHd,
                defaultEnhancePrompt,
                defaultUpsample
        );
    }

    private CapabilityProfile inferProfile() {
        String normalizedModel = modelId.toLowerCase(Locale.ROOT);
        if (normalizedModel.startsWith("veo")) {
            return inferVeoProfile(normalizedModel);
        }
        return inferSoraProfile(normalizedModel);
    }

    private CapabilityProfile inferSoraProfile(String normalizedModel) {
        boolean proModel = "sora-2-pro".equalsIgnoreCase(normalizedModel);
        List<String> textDurations = List.of("10", "15");
        List<String> imageDurations = List.of("10", "15");
        return new CapabilityProfile(
                "sora",
                true,
                true,
                proModel,
                false,
                false,
                List.of("16:9", "9:16"),
                textDurations,
                imageDurations,
                1,
                10,
                "16:9",
                textDurations.get(0),
                "10",
                false,
                false,
                false
        );
    }

    private CapabilityProfile inferVeoProfile(String normalizedModel) {
        boolean imageInputSupported = normalizedModel.contains("frames")
                || normalizedModel.contains("components")
                || "veo3.1".equals(normalizedModel)
                || "veo3.1-pro".equals(normalizedModel);
        boolean textToVideoSupported = "veo3.1".equals(normalizedModel)
                || "veo3.1-pro".equals(normalizedModel)
                || (!normalizedModel.contains("frames") && !normalizedModel.contains("components"));

        int maxImageCount = 0;
        if (imageInputSupported) {
            if ("veo3-pro-frames".equals(normalizedModel) || "veo3-fast-frames".equals(normalizedModel)) {
                maxImageCount = 1;
            } else if ("veo2-fast-frames".equals(normalizedModel)
                    || "veo3.1".equals(normalizedModel)
                    || "veo3.1-pro".equals(normalizedModel)) {
                maxImageCount = 2;
            } else if ("veo2-fast-components".equals(normalizedModel)
                    || "veo3.1-components".equals(normalizedModel)) {
                maxImageCount = 3;
            } else {
                maxImageCount = 10;
            }
        }

        List<String> durations = List.of("4", "8", "10", "15");

        return new CapabilityProfile(
                "veo",
                textToVideoSupported,
                imageInputSupported,
                false,
                true,
                true,
                List.of("16:9", "9:16"),
                textToVideoSupported ? durations : List.of(),
                imageInputSupported ? durations : List.of(),
                imageInputSupported ? 1 : 0,
                maxImageCount,
                "16:9",
                "8",
                "8",
                false,
                false,
                false
        );
    }

    private String extractTaskId(Map<?, ?> response) {
        if (response == null) {
            throw new BusinessException(1003, "API返回为空");
        }
        String taskId = stringValue(response.get("task_id"));
        if (!StringUtils.hasText(taskId)) {
            throw new BusinessException(1003, "未返回task_id");
        }
        return taskId;
    }

    @SuppressWarnings("unchecked")
    private String extractVideoUrl(Object dataObject) {
        if (!(dataObject instanceof Map<?, ?> data)) {
            return null;
        }
        Object output = data.get("output");
        if (output instanceof String value) {
            return value;
        }
        if (output instanceof List<?> values && !values.isEmpty() && values.get(0) != null) {
            return values.get(0).toString();
        }
        return null;
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private boolean overrideBoolean(Boolean configured, boolean inferred) {
        return configured != null ? configured : inferred;
    }

    private List<String> resolveList(List<String> configured, List<String> inferred) {
        List<String> normalized = normalizeList(configured);
        return normalized.isEmpty() ? inferred : normalized;
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(normalized::add);
        return List.copyOf(normalized);
    }

    private List<String> resolveParamOptions(AiVendorProperties.CompatibleVendorConfig config, String... paramKeys) {
        if (config.getParams() == null || config.getParams().isEmpty()) {
            return List.of();
        }
        for (AiVendorProperties.ModelParamConfig param : config.getParams()) {
            if (param == null || !matchesParamKey(param.getParamKey(), paramKeys)) {
                continue;
            }
            List<String> options = parseOptions(param.getOptions());
            if (!options.isEmpty()) {
                return options;
            }
        }
        return List.of();
    }

    private String resolveParamDefaultValue(AiVendorProperties.CompatibleVendorConfig config, String... paramKeys) {
        if (config.getParams() == null || config.getParams().isEmpty()) {
            return null;
        }
        for (AiVendorProperties.ModelParamConfig param : config.getParams()) {
            if (param == null || !matchesParamKey(param.getParamKey(), paramKeys)) {
                continue;
            }
            if (StringUtils.hasText(param.getDefaultValue())) {
                return param.getDefaultValue().trim();
            }
        }
        return null;
    }

    private boolean matchesParamKey(String actualKey, String... expectedKeys) {
        if (!StringUtils.hasText(actualKey) || expectedKeys == null || expectedKeys.length == 0) {
            return false;
        }
        for (String expectedKey : expectedKeys) {
            if (actualKey.equalsIgnoreCase(expectedKey)) {
                return true;
            }
        }
        return false;
    }

    private List<String> parseOptions(String rawOptions) {
        if (!StringUtils.hasText(rawOptions)) {
            return List.of();
        }
        String normalized = rawOptions.trim();
        if (normalized.startsWith("[")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("]")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String part : normalized.split(",")) {
            String value = part == null ? "" : part.trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            if (StringUtils.hasText(value)) {
                values.add(value.trim());
            }
        }
        return values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private String sanitizeDefault(String configuredValue, List<String> supportedValues) {
        if (!StringUtils.hasText(configuredValue)) {
            return supportedValues.isEmpty() ? null : supportedValues.get(0);
        }
        if (supportedValues.isEmpty() || supportedValues.contains(configuredValue)) {
            return configuredValue;
        }
        return supportedValues.get(0);
    }

    private String buildDisplayName(String rawModelId) {
        String normalized = rawModelId.trim();
        if (normalized.toLowerCase(Locale.ROOT).startsWith("veo")) {
            normalized = "Veo " + normalized.substring(3);
        } else if (normalized.toLowerCase(Locale.ROOT).startsWith("sora-")) {
            normalized = "Sora " + normalized.substring(5);
        }
        String[] parts = normalized.split("-");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            if (part.length() == 1) {
                builder.append(part.toUpperCase(Locale.ROOT));
            } else if (Character.isDigit(part.charAt(0))) {
                builder.append(part);
            } else {
                builder.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record CapabilityProfile(
            String family,
            boolean textToVideoSupported,
            boolean imageInputSupported,
            boolean hdSupported,
            boolean enhancePromptSupported,
            boolean upsampleSupported,
            List<String> supportedAspectRatios,
            List<String> supportedTextDurations,
            List<String> supportedImageDurations,
            int minImageCount,
            int maxImageCount,
            String defaultAspectRatio,
            String defaultTextDuration,
            String defaultImageDuration,
            boolean defaultHd,
            boolean defaultEnhancePrompt,
            boolean defaultUpsample
    ) {
    }
}
