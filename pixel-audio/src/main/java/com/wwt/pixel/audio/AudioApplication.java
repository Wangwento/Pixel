package com.wwt.pixel.audio;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 音频服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.wwt.pixel")
@EnableDiscoveryClient
@MapperScan("com.wwt.pixel.audio.mapper")
public class AudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(AudioApplication.class, args);
    }
}
