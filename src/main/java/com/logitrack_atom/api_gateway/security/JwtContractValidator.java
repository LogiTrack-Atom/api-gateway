package com.logitrack_atom.api_gateway.security;

import com.logitrack_atom.api_gateway.config.JwtProperties;
import com.logitrack_atom.api_gateway.enums.Rol;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Exige identidad, roles y tiempos completos además de la firma y el emisor.
 *
 * <p>La verificación no consulta usuarios ni depende de HTTP. Expiración y nbf
 * se evalúan también mediante el validador temporal estándar de Spring Security.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
public class JwtContractValidator implements OAuth2TokenValidator<Jwt> {
    private final JwtProperties properties;
    private final Clock clock;

    /**
     * Recibe el contrato y un reloj inyectable para pruebas deterministas.
     * @param properties parámetros del contrato
     * @param clock reloj UTC
     */
    public JwtContractValidator(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Comprueba audiencia, UUID canónico, roles conocidos, iat y exp obligatorios.
     * @param token token previamente decodificado
     * @return éxito o un rechazo genérico sin contenido del token
     */
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            String subject = token.getSubject();
            if (subject == null || !UUID.fromString(subject).toString().equalsIgnoreCase(subject)
                    || token.getAudience() == null || !token.getAudience().contains(properties.audience())) {
                return invalid();
            }
            Object roles = token.getClaims().get("roles");
            if (!(roles instanceof List<?> values) || values.isEmpty()) return invalid();
            for (Object value : values) {
                if (!(value instanceof String role)) return invalid();
                Rol.valueOf(role);
            }
            var issuedAt = token.getIssuedAt();
            var expiresAt = token.getExpiresAt();
            if (issuedAt == null || expiresAt == null
                    || issuedAt.isAfter(clock.instant().plus(properties.clockSkew()))
                    || !expiresAt.isAfter(issuedAt)
                    || Duration.between(issuedAt, expiresAt).compareTo(properties.accessTokenTtl()) > 0) {
                return invalid();
            }
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException ex) {
            return invalid();
        }
    }

    /**
     * Evita incluir valores del token en los mensajes del validador.
     * @return rechazo uniforme
     */
    private OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token no válido.", null));
    }
}
