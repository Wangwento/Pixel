package com.wwt.pixel.video;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 视频生成服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.wwt.pixel")
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.wwt.pixel.video.mapper")
public class VideoApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoApplication.class, args);
    }
}
