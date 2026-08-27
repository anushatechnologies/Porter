package com.anushaporter.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Value("${file.upload-dir:uploads/}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/drivers/**", "/api/driver/**", "/api/orders/**", "/api/admin/**", "/api/upload")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/addons/**",
                        "/api/customer/addons/**",
                        "/api/services/**",
                        "/api/customer/services/**",
                        "/api/categories/**",
                        "/api/pricing/**",
                        "/api/pm/app-settings",
                        "/api/pm/bookings/**",
                        "/api/drivers/verify-face",
                        "/api/driver/verify-face",
                        "/api/verify-face",
                        "/api/upload/**",
                        "/api/driver/documents/upload",
                        "/api/drivers/documents/upload",
                        "/uploads/**"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String normalizedDir = uploadDir.replace("\\", "/");
        if (!normalizedDir.endsWith("/")) {
            normalizedDir = normalizedDir + "/";
        }

        // Ensure directory exists on startup
        File dir = new File(normalizedDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Map the public web path '/uploads/**' to the physical disk directory
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + normalizedDir)
                .setCachePeriod(3600);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/uploads/**")
                .allowedOrigins("*")
                .allowedMethods("GET")
                .maxAge(3600);
    }
}
