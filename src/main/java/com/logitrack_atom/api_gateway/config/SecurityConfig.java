package com.logitrack_atom.api_gateway.config;

import com.logitrack_atom.api_gateway.security.SecurityErrorWriter;
import com.logitrack_atom.api_gateway.security.StrictBearerTokenConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

/**
 * Autoriza las operaciones públicas de LogiTrack por método, ruta y rol.
 *
 * <p>El perímetro es stateless y deniega cualquier operación no declarada. Los
 * servicios conservan la autorización de pertenencia y las reglas de negocio.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
@Configuration
public class SecurityConfig {
    private static final String SHIPMENT = "/applications/apishipment/api/v1";
    private static final String COURIER = "/applications/apicourier/api/v1";
    private static final String SECURITY = "/applications/apisecurity/api/v1";

    /** Crea la configuración de seguridad del perímetro. */
    public SecurityConfig() { }

    /**
     * Construye la cadena reactiva sin sesiones, formularios ni introspección HTTP.
     * @param http constructor de filtros
     * @param decoder verificador criptográfico local
     * @param converter conversor de roles validados
     * @param errors escritor de ApiError
     * @param corsSource política CORS explícita, aplicada antes de autenticar
     * @return filtros de autenticación y autorización de Gateway
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder decoder,
            ReactiveJwtAuthenticationConverterAdapter converter, SecurityErrorWriter errors,
            CorsConfigurationSource corsSource) {
        return http.cors(cors -> cors.configurationSource(corsSource))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.POST, SECURITY + "/auth/login", SECURITY + "/auth/register",
                                SECURITY + "/auth/session", SECURITY + "/auth/session/refresh", SECURITY + "/auth/session/logout").permitAll()
                        .pathMatchers(HttpMethod.GET, SECURITY + "/auth/me").hasAnyRole("ADMIN", "CLIENTE", "REPARTIDOR")
                        .pathMatchers(HttpMethod.POST, SECURITY + "/usuarios").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, SECURITY + "/usuarios/{id}").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, SHIPMENT + "/envios", SHIPMENT + "/envios/{id}",
                                SHIPMENT + "/envios/seguimiento/{codigo}", SHIPMENT + "/envios/{id}/historial")
                            .hasAnyRole("ADMIN", "CLIENTE", "REPARTIDOR")
                        .pathMatchers(HttpMethod.POST, SHIPMENT + "/envios/{id}/solicitudes-asignacion").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, SHIPMENT + "/operaciones-asignacion/{id}").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, SHIPMENT + "/envios").hasAnyRole("ADMIN", "CLIENTE")
                        .pathMatchers(HttpMethod.PATCH, SHIPMENT + "/envios/{id}/estado").hasAnyRole("ADMIN", "REPARTIDOR")
                        .pathMatchers(HttpMethod.PATCH, SHIPMENT + "/envios/{id}/cancelacion").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, SHIPMENT + "/envios/{id}/repartidor", SHIPMENT + "/clientes/{id}/usuario",
                                SHIPMENT + "/asociaciones/repartidores/{id}").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, COURIER + "/repartidores", COURIER + "/repartidores/{id}/usuario",
                                COURIER + "/admin/configuracion").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, COURIER + "/repartidores/{id}", COURIER + "/repartidores/{id}/disponibilidad")
                            .hasAnyRole("ADMIN", "REPARTIDOR")
                        .pathMatchers(HttpMethod.POST, COURIER + "/repartidores").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, COURIER + "/repartidores/{id}", COURIER + "/repartidores/{id}/usuario").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PATCH, COURIER + "/repartidores/{id}/estado").hasRole("ADMIN")
                        .anyExchange().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((exchange, ex) -> errors.unauthorized(exchange))
                        .accessDeniedHandler((exchange, ex) -> errors.forbidden(exchange)))
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenConverter(new StrictBearerTokenConverter())
                        .authenticationEntryPoint((exchange, ex) -> errors.unauthorized(exchange))
                        .accessDeniedHandler((exchange, ex) -> errors.forbidden(exchange))
                        .jwt(jwt -> jwt.jwtDecoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }
}
