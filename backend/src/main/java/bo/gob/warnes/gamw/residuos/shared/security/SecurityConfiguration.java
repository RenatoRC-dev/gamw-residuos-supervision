package bo.gob.warnes.gamw.residuos.shared.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración HTTP stateless. Todo endpoint de negocio no autorizado explícitamente
 * permanece cerrado (denyAll). Ningún token válido concede acceso general.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    PasswordEncoder codificadorContrasenas() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            ObjectProvider<JwtDecoder> decodificadorJwt,
            ObjectProvider<ConvertidorJwtUsuario> convertidorJwt) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/technical/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/autenticacion/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/autenticacion/actual").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/mis-jornadas").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/jornadas/*/ejecucion/inicio", "/api/jornadas/*/ejecucion/fin").authenticated()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/autenticacion/login"))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/problem+json");
                            response.getWriter().write(
                                    "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/problem+json");
                            response.getWriter().write(
                                    "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403}");
                        }));

        // En desarrollo-local y en el perfil técnico smoke no existe el stack JWT:
        // esta configuración arranca igual y todo endpoint no público sigue cerrado por denyAll.
        JwtDecoder decodificador = decodificadorJwt.getIfAvailable();
        if (decodificador != null) {
            ConvertidorJwtUsuario convertidor = convertidorJwt.getObject();
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                    .decoder(decodificador)
                    .jwtAuthenticationConverter(convertidor)));
        }
        return http.build();
    }
}
