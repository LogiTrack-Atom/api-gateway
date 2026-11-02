package com.logitrack_atom.api_gateway.config;

import com.logitrack_atom.api_gateway.security.JwtContractValidator;
import com.logitrack_atom.api_gateway.security.PublicKeyLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;

/**
 * Configura verificación reactiva RS256 local con el contrato de identidad de LogiTrack.
 *
 * <p>No consulta discovery OAuth, usuarios ni claves remotas durante la validación.
 * El Gateway recibe exclusivamente la clave pública externa del emisor.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
    /** Crea la configuración criptográfica de Gateway. */
    public JwtConfig() { }

    /**
     * Proporciona el reloj UTC utilizado por los validadores temporales.
     * @return reloj del proceso
     */
    @Bean
    public Clock jwtClock() { return Clock.systemUTC(); }

    /**
     * Carga la clave pública antes de aceptar peticiones autenticadas.
     * @param properties ubicación de la clave pública
     * @param loader lector de archivos PEM locales
     * @return clave RSA pública comprobada
     */
    @Bean
    public RSAPublicKey jwtPublicKey(JwtProperties properties, PublicKeyLoader loader) {
        return loader.cargar(properties.publicKeyLocation());
    }

    /**
     * Valida firma, emisor, audiencia, identidad, roles y tiempos sin HTTP adicional.
     * @param key clave pública del emisor
     * @param properties parámetros del contrato
     * @param clock reloj de validación
     * @return decoder reactivo que admite exclusivamente RS256
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(RSAPublicKey key, JwtProperties properties, Clock clock) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(key)
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        JwtTimestampValidator timestamp = new JwtTimestampValidator(properties.clockSkew());
        timestamp.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp,
                new JwtIssuerValidator(properties.issuer()), new JwtContractValidator(properties, clock)));
        return decoder;
    }

    /**
     * Convierte el claim roles en autoridades de negocio de Spring Security.
     * @return adaptador reactivo que utiliza prefijo ROLE_ sin interpretar scopes
     */
    @Bean
    public ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
