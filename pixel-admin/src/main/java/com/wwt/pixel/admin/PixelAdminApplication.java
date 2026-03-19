package com.wwt.pixel.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.wwt.pixel.admin", "com.wwt.pixel.common"})
@MapperScan("com.wwt.pixel.admin.mapper")
@EnableFeignClients
public class PixelAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(PixelAdminApplication.class, args);
    }
}
