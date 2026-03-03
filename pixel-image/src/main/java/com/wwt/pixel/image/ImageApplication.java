package com.wwt.pixel.image;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 图片生成服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.wwt.pixel")
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@MapperScan("com.wwt.pixel.image.mapper")
public class ImageApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageApplication.class, args);
    }
}