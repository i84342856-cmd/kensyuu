package com.example.moattravel.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // "/storage/**" というURLでアクセスが来たら
        // プロジェクト内の実際のフォルダ（src/.../storage）を直接見に行くように命令する
        registry.addResourceHandler("/storage/**")
                .addResourceLocations("file:src/main/resources/static/storage/");
    }
}