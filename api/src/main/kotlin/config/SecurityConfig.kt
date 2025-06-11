package com.harshsbajwa.stockifai.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig {
    @Value("\${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
    private lateinit var allowedOrigins: String

    @Value("\${app.security.jwt.enabled:false}")
    private var jwtEnabled: Boolean = false

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val httpSecurity =
            http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests { authorize ->
                    authorize
                        .requestMatchers(
                            "/actuator/health/**",
                            "/actuator/prometheus",
                            "/actuator/info",
                            "/actuator/metrics",
                        ).permitAll()
                        .requestMatchers("/api/v1/health")
                        .permitAll()
                        .apply {
                            if (jwtEnabled) {
                                requestMatchers("/api/v1/**").authenticated()
                            } else {
                                requestMatchers("/api/v1/**").permitAll()
                            }
                        }.anyRequest()
                        .authenticated()
                }.sessionManagement { session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                }.csrf { csrf -> csrf.disable() }

        if (jwtEnabled) {
            httpSecurity.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    // JWT configuration will be handled via application properties
                }
            }
        }

        return httpSecurity.build()
    }

    @Bean
    @Profile("!k3s-production")
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = allowedOrigins.split(",").map { it.trim() }
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        configuration.maxAge = 3600
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Configuration
    @Profile("k3s-production")
    class ProductionSecurityConfig {
        @Bean
        fun corsConfigurationSource(): CorsConfigurationSource {
            val configuration = CorsConfiguration()
            configuration.allowedOriginPatterns = listOf("*")
            configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            configuration.allowedHeaders = listOf("*")
            configuration.allowCredentials = true
            configuration.maxAge = 3600
            val source = UrlBasedCorsConfigurationSource()
            source.registerCorsConfiguration("/**", configuration)
            return source
        }
    }
}
