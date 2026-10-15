package com.jinlong.framework.config;

import com.jinlong.common.core.domain.model.LoginUser;
import com.jinlong.framework.security.filter.JwtAuthenticationTokenFilter;
import com.jinlong.framework.security.handle.SecurityResponse;
import com.jinlong.framework.web.service.LoginLogService;
import com.jinlong.framework.web.service.RequestUtils;
import com.jinlong.framework.web.service.TokenService;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, BCryptPasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.auth.allowed-origins:}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TokenService tokens, JsonMapper mapper,
            LoginLogService loginLogService, CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> SecurityResponse.write(response, mapper, 401, "请先登录"))
                        .accessDeniedHandler((request, response, exception) -> SecurityResponse.write(response, mapper, 403, "没有访问权限")))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/captchaImage").permitAll()
                        .requestMatchers(HttpMethod.GET, "/files/avatars/*").permitAll()
                        .anyRequest().authenticated())
                .logout(logout -> logout.logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/logout"))
                        .logoutSuccessHandler((request, response, authentication) -> {
                            try {
                                LoginUser user = tokens.getLoginUser(request);
                                if (user != null) {
                                    tokens.delLoginUser(user.getToken());
                                    loginLogService.record(user.getUsername(), "0", "退出成功", RequestUtils.getIpAddr());
                                }
                                SecurityResponse.write(response, mapper, 200, "退出成功");
                            } catch (DataAccessException exception) {
                                SecurityResponse.write(response, mapper, 503, "认证服务暂不可用");
                            }
                        }))
                .addFilterBefore(new JwtAuthenticationTokenFilter(tokens, mapper), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
