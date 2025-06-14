package hr.algebra.nrako.photoapp_backend.configuration;

import hr.algebra.nrako.photoapp_backend.filter.FirebaseAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableWebSecurity
@AllArgsConstructor
@EnableMethodSecurity
public class SecurityConfig implements WebMvcConfigurer {

    public static final String API_PHOTOS_ID = "/api/photos/{id}";
    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;
    // Ažuriraj konstruktor!


    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("*") // Dopusti sve metode (GET, POST, PUT, DELETE, OPTIONS)
                .allowedHeaders("*") // Dopusti sva zaglavlja
                .allowCredentials(true);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/auth/login", "/auth/register").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/photos/upload", "/api/photos/user").hasAnyRole("ADMIN","REGISTERED")
                        .requestMatchers("/user-package/**").hasAnyRole("REGISTERED", "ADMIN")
                        .requestMatchers(API_PHOTOS_ID, "/api/photos/last10").permitAll()
                        .requestMatchers("/api/photos/user/{uid}").hasAnyRole("REGISTERED", "ADMIN")
                        .requestMatchers("/auth/update").hasRole("ADMIN")
                        .requestMatchers("/api/photos/user/{uid}").hasRole("ADMIN")
                        .anyRequest().authenticated() // Zahtijevaj autentikaciju za SVE ostale endpoint-e
                )
                .addFilterBefore(firebaseAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling.authenticationEntryPoint(authenticationEntryPoint())
                                .accessDeniedHandler(accessDeniedHandler()))
                .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configure(http));

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            String path = request.getRequestURI();
            System.out.println("NEAUTENTIFICIRAN zahtjev na: " + path + " | Greška: " + authException.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Nisi autentificiran");
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            String path = request.getRequestURI();
            System.out.println("ZABRANJEN pristup na: " + path + " | Razlog: " + accessDeniedException.getMessage());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Nemaš pristup");
        };
    }
}