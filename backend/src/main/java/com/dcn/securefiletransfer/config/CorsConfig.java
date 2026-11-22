package com.dcn.securefiletransfer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross-Origin Resource Sharing (CORS) Configuration.
 * 
 * Networking Concept:
 * When Next.js runs on http://localhost:3000 and calls Spring Boot on http://localhost:8080,
 * the web browser enforces the Same-Origin Policy (SOP).
 * This configuration sends the necessary 'Access-Control-Allow-Origin' headers
 * back to the browser so frontend requests are permitted.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                        "http://localhost:3001",
                        "http://192.168.*.*",
                        "http://10.*.*.*"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
