package com.lj.zby.util;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
public class MyConfiguration {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurerAdapter() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // 设置了可以被跨域访问的路径和可以被哪些主机跨域访问
//                registry.addMapping("/api/**").allowedOrigins("http://localhost:8081", "http://localhost:8082");
                registry.addMapping("/**");
            }
        };
    }
}