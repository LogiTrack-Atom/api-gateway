package com.logitrack_atom.api_gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Reenvía únicamente el Bearer validado por Spring Security a las rutas de negocio.
 *
 * <p>Retira encabezados de identidad suministrados por el cliente. No interpreta
 * claims ni sustituye las verificaciones locales de los servicios de destino.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
@Component
public class ValidatedBearerGlobalFilter implements GlobalFilter, Ordered {
    /** Crea el filtro de cabeceras de las rutas declaradas. */
    public ValidatedBearerGlobalFilter() { }

    /**
     * Limpia la petición y restaura Authorization solo desde una autenticación JWT válida.
     * @param exchange intercambio autorizado por la cadena de seguridad
     * @param chain filtros de la ruta seleccionada
     * @return ejecución única del resto de la cadena
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(HttpHeaders.AUTHORIZATION);
            headers.remove("X-User-Id");
            headers.remove("X-User-Roles");
            headers.remove("X-Authenticated-User");
            headers.remove("X-Authenticated-Roles");
        }).build();
        ServerWebExchange clean = exchange.mutate().request(request).build();
        return exchange.getPrincipal().map(principal -> {
            if (principal instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
                var authenticated = request.mutate().headers(headers -> headers.setBearerAuth(jwt.getToken().getTokenValue())).build();
                return clean.mutate().request(authenticated).build();
            }
            return clean;
        }).defaultIfEmpty(clean).flatMap(chain::filter);
    }

    /**
     * Ejecuta la limpieza antes del envío HTTP de la ruta.
     * @return prioridad de preprocesamiento
     */
    @Override
    public int getOrder() { return -100; }
}
