package com.dcn.securefiletransfer.config;

import com.dcn.securefiletransfer.security.jwt.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security Configuration for REST APIs.
 * 
 * Networking Viva Explanation:
 * - Configures stateless REST security.
 * - Enables CORS allowing multi-laptop access across the local Wi-Fi / LAN subnet.
 * - Disables CSRF for stateless bearer token APIs.
 * - Allows public diagnostic access to /api/health, /api/auth/demo-token, and /api/devices/**
 *   so connecting peer laptops can discover and ping nodes.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write(
                                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication Required: Valid Supabase JWT token must be provided in Authorization header.\",\"path\":\"" + request.getRequestURI() + "\"}"
                            );
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Public diagnostic ping & demo token generator
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/demo-token").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Allow peer device mesh discovery and status pinging across LAN
                        .requestMatchers("/api/devices/**").permitAll()
                        // Protected DCN REST endpoints
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/api/transfers/**").authenticated()
                        .requestMatchers("/api/integrity/**").authenticated()
                        .requestMatchers("/api/tampering/**").authenticated()
                        .requestMatchers("/api/security/**").authenticated()
                        .requestMatchers("/api/analytics/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
