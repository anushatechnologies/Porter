package com.anushaporter.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/drivers/**", "/api/driver/**", "/api/orders/**", "/api/admin/**", "/api/upload")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/drivers/verify-face",
                        "/api/driver/verify-face",
                        "/api/verify-face",
                        "/api/upload/**",
                        "/api/driver/documents/upload",
                        "/api/drivers/documents/upload"
                );
    }
}
