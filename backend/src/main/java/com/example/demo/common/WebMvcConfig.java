package com.example.demo.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置
 * 注册拦截器，配置放行路径
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",          // 登录注册放行
                        "/api/captcha",          // 验证码放行
                        "/api/health",           // 健康检查放行
                        "/api/merchants",        // 商家列表放行
                        "/api/merchants/**",     // 商家详情放行
                        "/api/categories",       // 分类列表放行
                        "/api/products/**",      // 商品详情放行
                        "/api/search",           // 搜索放行
                        "/api/recommend",        // 推荐放行
                        "/api-docs/**",          // 接口文档放行
                        "/swagger-ui/**"         // Swagger UI 放行
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadRoot = Paths.get("uploads").toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadRoot.toUri().toString());
    }
}
