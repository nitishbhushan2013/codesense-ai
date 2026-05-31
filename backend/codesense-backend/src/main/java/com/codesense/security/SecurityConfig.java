package com.codesense.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtFilter jwtFilter;
  private final OAuth2SuccessHandler oAuth2SuccessHandler;

  @Value("${FRONTEND_URL:http://localhost:3000}")
  private String frontendUrl;

  public SecurityConfig(JwtFilter jwtFilter, OAuth2SuccessHandler oAuth2SuccessHandler) {
    this.jwtFilter = jwtFilter;
    this.oAuth2SuccessHandler = oAuth2SuccessHandler;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(frontendUrl));
    config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/api/auth/**")
                    .permitAll()
                    .requestMatchers("/login/oauth2/**")
                    .permitAll()
                    .requestMatchers("/oauth2/**")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/reviews")
                    .permitAll()
                    .requestMatchers("/api/reviews/*/chat")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .oauth2Login(oauth2 -> oauth2.successHandler(oAuth2SuccessHandler))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
