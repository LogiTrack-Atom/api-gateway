package com.logitrack_atom.api_gateway.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Extrae un único Bearer desde Authorization sin aceptar credenciales ambiguas.
 *
 * <p>No admite tokens en query strings ni autentica esquemas alternativos.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
public final class StrictBearerTokenConverter implements ServerAuthenticationConverter {
    private final ServerBearerTokenAuthenticationConverter delegate = new ServerBearerTokenAuthenticationConverter();

    /** Crea el extractor con tokens de query deshabilitados explícitamente. */
    public StrictBearerTokenConverter() {
        delegate.setAllowUriQueryParameter(false);
    }

    /**
     * Rechaza encabezados repetidos antes de delegar la extracción estándar.
     * @param exchange petición recibida
     * @return credencial Bearer o ausencia de autenticación
     */
    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        var headers = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (headers != null && headers.size() != 1) {
            return Mono.error(new OAuth2AuthenticationException(
                    BearerTokenErrors.invalidToken("Cabecera Authorization ambigua")));
        }
        return delegate.convert(exchange);
    }
}
