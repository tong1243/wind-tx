package com.wut.screenmsgtx.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static com.wut.screencommontx.Static.MsgModuleStatic.*;

@Configuration
public class WebCorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(CORS_MAPPING)
                .allowCredentials(true)
                .allowedHeaders(CORS_HEADERS)
                .maxAge(CORS_MAX_AGE)
                .allowedMethods(CORS_METHODS)
                .allowedOriginPatterns(CORS_ORIGIN_PATTERNS);
    }

}
