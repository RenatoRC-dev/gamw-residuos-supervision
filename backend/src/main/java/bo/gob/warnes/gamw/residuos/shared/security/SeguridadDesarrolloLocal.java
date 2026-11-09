package bo.gob.warnes.gamw.residuos.shared.security;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;
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
@Profile("desarrollo-local")
public class SeguridadDesarrolloLocal {
    @Bean
    @Order(1)
    SecurityFilterChain filtroDesarrolloLocal(HttpSecurity http,
            @Value("${server.address:127.0.0.1}") String address,
            @Value("${gamw.development.web-origin:http://127.0.0.1:5173}") String webOrigin) throws Exception {
        if (!InetAddress.getByName(address).isLoopbackAddress()) {
            throw new IllegalStateException("desarrollo-local requiere server.address de loopback");
        }
        var origin = URI.create(webOrigin);
        if (!("http".equals(origin.getScheme()) || "https".equals(origin.getScheme()))
                || !Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(origin.getHost() == null ? "" : origin.getHost())
                || origin.getRawUserInfo() != null || origin.getRawQuery() != null || origin.getRawFragment() != null
                || (origin.getRawPath() != null && !origin.getRawPath().isEmpty())) {
            throw new IllegalStateException("El origen web de desarrollo debe ser un origen local sin ruta.");
        }
        String[] endpoints = {"/api/rutas", "/api/rutas/**", "/api/personas", "/api/personas/**",
                "/api/empresas-operadoras", "/api/empresas-operadoras/**", "/api/vehiculos", "/api/vehiculos/**",
                "/api/jornadas", "/api/jornadas/**", "/api/periodos-servicio", "/api/periodos-servicio/**"};
        return http.securityMatcher(endpoints)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().access((authentication, context) -> {
                    var request = context.getRequest();
                    boolean local = "127.0.0.1".equals(request.getRemoteAddr()) || "0:0:0:0:0:0:0:1".equals(request.getRemoteAddr())
                            || "::1".equals(request.getRemoteAddr());
                    String requestOrigin = request.getHeader("Origin");
                    boolean permitido=local && "gamw-desarrollo".equals(request.getHeader("X-GAMW-Development"))
                            && (requestOrigin == null || webOrigin.equals(requestOrigin));
                    if(permitido) request.setAttribute("gamw.acceso-local-validado",Boolean.TRUE);
                    return new AuthorizationDecision(permitido);
                }))
                // Custom header + origin check above prevent cross-site form submission in this local-only profile.
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpoints))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> response.setStatus(403)))
                .build();
    }
}
