package com.example.blog.config;

import com.example.blog.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置
 * <p>注意：JwtAuthenticationFilter 必须通过方法参数注入而非构造器注入，
 * 否则会形成 SecurityConfig → Filter → UserDetailsService → UserService
 * → PasswordEncoder → SecurityConfig 的循环依赖</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                // 启用跨域（配合 CorsConfig），放行预检请求
                .cors(Customizer.withDefaults())
                // 无状态认证，不使用 Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 注册、登录接口匿名可访问
                        .requestMatchers("/api/auth/**").permitAll()
                        // 「我的文章」需要登录：必须声明在下面的通配放行之前，
                        // Spring Security 按声明顺序首次匹配生效
                        .requestMatchers(HttpMethod.GET, "/api/articles/my").authenticated()
                        // 游客可浏览：文章列表 / 搜索 / 详情、评论树（仅 GET）
                        .requestMatchers(HttpMethod.GET, "/api/articles",
                                "/api/articles/search", "/api/articles/*", "/api/comments").permitAll()
                        // 其余一律需要认证：发文章/评论/点赞/收藏等写操作、个人中心等
                        .anyRequest().authenticated())
                // 未认证时返回 401 JSON：默认行为是空 body 的 403，
                // 前端无法据此区分「未登录」和「无权限」
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    // 统一返回结构交给 ObjectMapper 序列化，避免手写字符串漂移
                    response.getWriter().write(objectMapper.writeValueAsString(
                            Result.error(401, "未登录或登录已过期")));
                }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
