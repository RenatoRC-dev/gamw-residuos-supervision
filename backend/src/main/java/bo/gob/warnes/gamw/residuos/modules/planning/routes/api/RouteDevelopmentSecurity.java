package bo.gob.warnes.gamw.residuos.modules.planning.routes.api;

import java.net.InetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Explicit local POC access, NOT authentication or institutional authorization. Disabled by default. */
@Configuration(proxyBeanMethods = false)
@Profile("route-development")
public class RouteDevelopmentSecurity {
    @Bean
    @Order(1)
    SecurityFilterChain developmentRoutes(HttpSecurity http,
            @Value("${server.address:127.0.0.1}") String address,
            @Value("${gamw.development.web-origin:http://127.0.0.1:5173}") String webOrigin) throws Exception {
        if (!InetAddress.getByName(address).isLoopbackAddress()) {
            throw new IllegalStateException("route-development requires a loopback server.address");
        }
        return http.securityMatcher("/api/routes", "/api/routes/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().access((authentication, context) -> {
                    var request = context.getRequest();
                    boolean local = "127.0.0.1".equals(request.getRemoteAddr()) || "0:0:0:0:0:0:0:1".equals(request.getRemoteAddr())
                            || "::1".equals(request.getRemoteAddr());
                    String origin = request.getHeader("Origin");
                    return new AuthorizationDecision(local && "route-poc".equals(request.getHeader("X-GAMW-Development"))
                            && (origin == null || webOrigin.equals(origin)));
                }))
                // Custom header + origin check above prevent cross-site form submission in this local-only profile.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/routes", "/api/routes/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> response.setStatus(403)))
                .build();
    }
}
