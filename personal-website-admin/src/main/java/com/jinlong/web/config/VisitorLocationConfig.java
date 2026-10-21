package com.jinlong.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration(proxyBeanMethods = false)
@PropertySource(value = "file:./.local/location.properties", ignoreResourceNotFound = true, encoding = "UTF-8")
public class VisitorLocationConfig {
}
