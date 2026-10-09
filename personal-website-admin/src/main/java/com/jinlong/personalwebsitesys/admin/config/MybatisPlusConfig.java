package com.jinlong.personalwebsitesys.admin.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.jinlong.personalwebsitesys.admin.mapper")
public class MybatisPlusConfig {}
