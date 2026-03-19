package com.wwt.pixel.image.ai.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.image.ai.MultiVendorImageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class NacosConfigListener {

    private final MultiVendorImageService multiVendorImageService;
    private final AiVendorProperties properties;
    private final ObjectMapper objectMapper;

    @Value("${service.category:image}")
    private String serviceCategory;

    @Value("${spring.cloud.nacos.config.server-addr}")
    private String serverAddr;

    @Value("${spring.cloud.nacos.config.namespace:}")
    private String namespace;

    private static final String GROUP = "DEFAULT_GROUP";
    private ConfigService configService;

    private String getDataId() {
        return "ai-" + serviceCategory + "-config.json";
    }

    @PostConstruct
    public void init() {
        try {
            // 手动创建 ConfigService
            Properties nacosProperties = new Properties();
            nacosProperties.put("serverAddr", serverAddr);
            if (namespace != null && !namespace.isEmpty()) {
                nacosProperties.put("namespace", namespace);
            }
            configService = NacosFactory.createConfigService(nacosProperties);

            String dataId = getDataId();

            // 先主动拉取一次配置
            try {
                String config = configService.getConfig(dataId, GROUP, 5000);
                if (config != null && !config.isEmpty()) {
                    log.info("启动时加载Nacos配置: {}", dataId);
                    List<AiVendorProperties.CompatibleVendorConfig> vendors =
                        objectMapper.readValue(config, new TypeReference<>() {});
                    multiVendorImageService.refreshVendors(vendors);
                    log.info("成功加载 {} 个{}类型的模型配置", vendors.size(), serviceCategory);
                }
            } catch (Exception e) {
                log.warn("启动时加载Nacos配置失败: {}", e.getMessage());
            }

            // 注册监听器
            configService.addListener(dataId, GROUP, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("检测到Nacos配置变更: {}，刷新AI厂商列表", dataId);
                    try {
                        List<AiVendorProperties.CompatibleVendorConfig> vendors =
                            objectMapper.readValue(configInfo, new TypeReference<>() {});
                        multiVendorImageService.refreshVendors(vendors);
                        log.info("成功加载 {} 个{}类型的模型配置", vendors.size(), serviceCategory);
                    } catch (Exception e) {
                        log.error("解析Nacos配置失败", e);
                    }
                }
            });
            log.info("已注册Nacos配置监听器: {}", dataId);
        } catch (Exception e) {
            log.error("注册Nacos监听器失败", e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("应用启动完成，加载AI厂商配置，服务类型: {}，监听配置文件: {}", serviceCategory, getDataId());
        refreshVendors();
    }

    private void refreshVendors() {
        try {
            List<AiVendorProperties.CompatibleVendorConfig> vendors = properties.getVendors();
            if (vendors != null && !vendors.isEmpty()) {
                multiVendorImageService.refreshVendors(vendors);
            }
        } catch (Exception e) {
            log.error("初始化AI厂商配置失败", e);
        }
    }
}