package org.ping_me.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Admin 8/3/2025
 **/
@Configuration
public class SecurityConfiguration {

    private static final String[] WHITELIST = {
            // API DOCS
            "/swagger-ui/**",
            "/v3/api-docs/**",

            // WebSocket
            // Chú ý: Nếu trong application.properties bạn có cấu hình server.servlet.context-path=/core-service
            // thì ở đây Spring Security sẽ tự động bỏ qua chữ /core-service. Bạn chỉ cần ghi là "/ws/**"
            "/core-service/ws/**", // Tạm thời mình giữ nguyên theo code của bạn

            // Health check
            "/actuator/health",
            "/actuator/health/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity httpSecurity,
            CustomAuthenticationEntryPoint customAuthenticationEntryPoint
    ) throws Exception {
        httpSecurity
                // 1. TẮT CORS (Gateway đã thầu vụ này rồi)
                .cors(AbstractHttpConfigurer::disable)

                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WHITELIST).permitAll()
                        .anyRequest().authenticated()
                )

                // 2. Cấu hình JWT mặc định (Xóa cái SkipPathBearer đi là hết lỗi)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                );

        return httpSecurity.build();
    }
}
