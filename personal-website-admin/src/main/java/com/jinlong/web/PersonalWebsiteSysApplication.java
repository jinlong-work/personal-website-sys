package com.jinlong.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication(scanBasePackages = "com.jinlong")
@MapperScan("com.jinlong.system.mapper")
public class PersonalWebsiteSysApplication {
    public static void main(String[] args) {
        SpringApplication.run(PersonalWebsiteSysApplication.class, args);
        System.out.println("系统启动成功!!!!!");
    }
}
