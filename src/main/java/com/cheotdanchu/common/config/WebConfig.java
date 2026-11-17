package com.cheotdanchu.common.config;

import jakarta.servlet.MultipartConfigElement;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final PhotoStorage photoStorage;

    /** 프론트 주소(쉼표로 여러 개). 배포에서는 환경변수 APP_CORS_ORIGINS 로 Vercel 주소를 넣는다 */
    @Value("${app.cors-origins:http://localhost:5173,http://localhost:3000}")
    private String[] corsOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(corsOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }

    /** 저장한 인증 사진을 /uploads/파일명 으로 보여준다 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(PhotoStorage.URL_PREFIX + "**")
                .addResourceLocations(photoStorage.dir().toUri().toString());
    }

    /** 기본 업로드 한도(1MB)는 휴대폰 사진보다 작아서 늘린다. application.yaml은 각자 로컬 파일이라 코드에 둔다 */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(10));
        factory.setMaxRequestSize(DataSize.ofMegabytes(30));
        return factory.createMultipartConfig();
    }
}
