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
                        .requestMatchers(HttpMethod.GET, "/api/personas", "/api/personas/*", "/api/vehiculos", "/api/vehiculos/*", "/api/empresas-operadoras", "/api/empresas-operadoras/*",
                            "/api/empresas-operadoras/*/periodos", "/api/empresas-operadoras/*/personal", "/api/empresas-operadoras/*/vehiculos",
                            "/api/rutas", "/api/rutas/*", "/api/rutas/*/versiones", "/api/rutas/*/versiones/*", "/api/jornadas", "/api/jornadas/*", "/api/jornadas/*/asignaciones", "/api/periodos-servicio").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/personas", "/api/vehiculos", "/api/vehiculos/marcas", "/api/vehiculos/modelos", "/api/vehiculos/tipos", "/api/empresas-operadoras",
                            "/api/empresas-operadoras/*/periodos", "/api/empresas-operadoras/*/personal", "/api/empresas-operadoras/*/vehiculos", "/api/rutas", "/api/rutas/*/versiones",
                            "/api/jornadas", "/api/jornadas/*/asignaciones", "/api/jornadas/*/reasignaciones", "/api/jornadas/*/reprogramaciones").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/personas/*", "/api/vehiculos/*", "/api/empresas-operadoras/*", "/api/empresas-operadoras/*/periodos/*", "/api/empresas-operadoras/*/personal/*", "/api/empresas-operadoras/*/vehiculos/*").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/personas/*/estado", "/api/vehiculos/*/estado", "/api/empresas-operadoras/*/estado", "/api/empresas-operadoras/*/periodos/*/estado").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/empresa/periodos-servicio", "/api/empresa/periodos/*/referencias", "/api/empresa/jornadas/*/recursos").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/empresa/periodos/*/jornadas", "/api/empresa/jornadas/*/asignaciones", "/api/empresa/jornadas/*/reasignaciones").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/reclamos-institucionales", "/api/reclamos-institucionales/opciones", "/api/reclamos-institucionales/*",
                            "/api/reclamos-institucionales/*/anexos/*", "/api/puntos-criticos", "/api/puntos-criticos/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/reclamos-institucionales", "/api/reclamos-institucionales/*/revisiones", "/api/reclamos-institucionales/*/relaciones",
                            "/api/reclamos-institucionales/*/anexos", "/api/puntos-criticos", "/api/puntos-criticos/*/observaciones", "/api/puntos-criticos/*/validacion").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/administracion/identidad/usuarios", "/api/administracion/identidad/usuarios/*",
                            "/api/administracion/identidad/personas", "/api/administracion/identidad/opciones", "/api/administracion/identidad/usuarios/*/permisos", "/api/administracion/identidad/usuarios/*/actuaciones").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/administracion/identidad/usuarios", "/api/administracion/identidad/usuarios/*/permisos", "/api/administracion/identidad/usuarios/*/permisos/*/revocacion").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/administracion/identidad/usuarios/*").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/administracion/identidad/usuarios/*/estado").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/empresa/jornadas", "/api/empresa/jornadas/*", "/api/mis-jornadas/*/ejecucion").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/empresa/jornadas/*/reprogramaciones").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/empresa/correctivas", "/api/empresa/correctivas/*", "/api/empresa/correctivas/*/evidencias/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/empresa/correctivas/*/respuestas").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/jornadas/*/incidencias-propias", "/api/correctivas/jornadas", "/api/correctivas/jornadas/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/jornadas/*/incidencias", "/api/correctivas/jornadas/*/acciones", "/api/correctivas/acciones/*/verificaciones").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/monitoreo/jornadas").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/fiscalizacion/jornadas", "/api/fiscalizacion/jornadas/*",
                            "/api/fiscalizacion/jornadas/*/gps", "/api/fiscalizacion/jornadas/*/evidencias/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/fiscalizacion/jornadas/*/inspecciones", "/api/fiscalizacion/jornadas/*/resultado").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/referencias-espaciales", "/api/referencias-espaciales/versiones-ruta",
                            "/api/revision-gps/jornadas/*/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/referencias-espaciales", "/api/referencias-espaciales/*/versiones",
                            "/api/referencias-espaciales/*/cierre").authenticated()
                        // La fachada comprueba concesión municipal vigente antes de leer datos.
                        .requestMatchers(HttpMethod.GET, "/api/trayectorias/jornadas", "/api/trayectorias/jornadas/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/jornadas/*/ejecucion/inicio", "/api/jornadas/*/ejecucion/fin").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/jornadas/*/ejecucion/posiciones-gps").authenticated()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/autenticacion/login")
                    // Esta cadena no usa cookies/sesiones, HTTP Basic ni formularios como autenticación.
                    // La presencia del header solo excluye CSRF: el resource server valida siempre el JWT.
                    .ignoringRequestMatchers(request -> request.getRequestURI().startsWith(request.getContextPath()+"/api/")
                        && request.getHeader("Authorization") != null
                        && request.getHeader("Authorization").matches("(?i)^Bearer [^\\s]+$")))
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
