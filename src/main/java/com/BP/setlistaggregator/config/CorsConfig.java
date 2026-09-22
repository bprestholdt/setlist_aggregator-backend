package com.BP.setlistaggregator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        //patterns so Vercel's per-deployment preview links work too, not just the main domain
                        .allowedOriginPatterns(
                                "http://localhost:3000",
                                "https://setlist-aggregator-frontend.vercel.app",
                                "https://setlist-aggregator-frontend-*-bprestholdts-projects.vercel.app"
                        )
                        //the API only has GET endpoints and the frontend doesn't send cookies
                        .allowedMethods("GET", "OPTIONS")
                        .allowedHeaders("*")
                        .maxAge(3600);
            }
        };
    }
}
