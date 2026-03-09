package kr.or.ddit.comm.conf;

import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;

import jakarta.servlet.DispatcherType;
import kr.or.ddit.security.CustomJwtAuthenticationConverter;
import kr.or.ddit.security.handler.CustomAuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SpringSecurityJavaConfig {

    @Autowired
    private CorsConfigurationSource corsConfigSource;

    @Autowired
    private CustomAuthenticationSuccessHandler successHandler;

    @Autowired
    private CustomJwtAuthenticationConverter customJwtAuthenticationConverter;

    private final String[] whitelist = {
        "/dist/**",
        "/css/**",
        "/js/**",
        "/html/**",
        "/error/**",
        "/images/**",
        "/swagger**",
        "/login",
        "/signup",
        "/invite/accept",
        "/actuator/health",
        "/actuator/health/**",
        "/public/**",
        "/common/auth",
        "/actuator/health",
        "/actuator/health/**"
    };

    private final DispatcherType[] dispatcherWhitelist = {
        DispatcherType.FORWARD,
        DispatcherType.INCLUDE,
        DispatcherType.ERROR
    };

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${jwt.sign-key}") String secretKey) {
        byte[] keyBytes = secretKey.getBytes();
        SecretKeySpec key = new SecretKeySpec(keyBytes, JWSAlgorithm.HS256.getName());
        return NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    public BearerTokenResolver cookieBearerTokenResolver() {
        return new kr.or.ddit.security.filter.CookieBearerTokenResolver("access_token");
    }

    @Bean
    @Order(1)
    public SecurityFilterChain restSecurityFilterChain(HttpSecurity http, BearerTokenResolver cookieBearerTokenResolver) throws Exception {
        http
            .securityMatcher(
                "/rest/**",
                "/chat/**",
                "/mail/counts",
                "/mail/listData/**",
                "/mail/toggle-importance/**",
                "/mail/deleteSelected",
                "/mail/deleteAll",
                "/mail/restoreSelected",
                "/starworks-groupware-websocket",
                "/starworks-groupware-websocket/**"
            )
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigSource))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/rest/contracts/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED))
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(customJwtAuthenticationConverter))
                .bearerTokenResolver(cookieBearerTokenResolver)
            );
        return http.build();
    }

    @Bean
    public SecurityContextRepository contextRepository() {
        return new DelegatingSecurityContextRepository(
            new HttpSessionSecurityContextRepository(),
            new RequestAttributeSecurityContextRepository()
        );
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatchers(matcher -> matcher.anyRequest())
            .cors(cors -> cors.configurationSource(corsConfigSource))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                .dispatcherTypeMatchers(dispatcherWhitelist).permitAll()
                .requestMatchers(whitelist).permitAll()
                .requestMatchers("/admin/**").hasAnyAuthority("ADMIN", "ROLE_ADMIN")
                .anyRequest().authenticated()
            )
            .securityContext(sc -> sc.securityContextRepository(contextRepository()))
            .formLogin(login -> login
                .loginPage("/login")
                .successHandler(successHandler)
                .permitAll()
            )
            .exceptionHandling(exception -> exception.accessDeniedPage("/access-denied"))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .deleteCookies("access_token")
                .logoutSuccessHandler((request, response, authentication) -> {
                    String accept = request.getHeader("accept");
                    if (accept != null && accept.contains("json")) {
                        new ObjectMapper().writeValue(response.getWriter(), Map.of("success", true));
                    } else {
                        response.sendRedirect("/login");
                    }
                })
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
