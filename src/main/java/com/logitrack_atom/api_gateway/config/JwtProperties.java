package com.logitrack_atom.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import java.time.Duration;

/**
 * Define el contrato JWT y la ubicación de su clave pública externa.
 *
 * <p>El material criptográfico no se recibe como texto de configuración.
 * Los límites impiden extender accidentalmente la vida de los tokens del sprint.</p>
 *
 * @param issuer emisor esperado
 * @param audience audiencia compartida por las APIs
 * @param accessTokenTtl duración positiva, entera y de hasta quince minutos
 * @param clockSkew tolerancia de reloj entera, entre cero y sesenta segundos
 * @param publicKeyLocation archivo PEM X.509 externo
 * @author MaxDz
 * @version 0.3.0
 */
@ConfigurationProperties("logitrack.security")
public record JwtProperties(String issuer, String audience, Duration accessTokenTtl, Duration clockSkew,
                            Resource publicKeyLocation) {
    /**
     * Rechaza configuraciones incompletas o tiempos fuera del contrato.
     * @param issuer emisor configurado
     * @param audience audiencia configurada
     * @param accessTokenTtl duración de acceso
     * @param clockSkew tolerancia de reloj
     * @param publicKeyLocation ubicación de la clave pública
     * @throws IllegalArgumentException si falta un valor o sus límites son inválidos
     */
    public JwtProperties {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || publicKeyLocation == null) {
            throw new IllegalArgumentException("La configuración JWT está incompleta.");
        }
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()
                || accessTokenTtl.compareTo(Duration.ofMinutes(15)) > 0 || accessTokenTtl.getNano() != 0) {
            throw new IllegalArgumentException("La duración JWT debe estar entre 1 y 900 segundos enteros.");
        }
        if (clockSkew == null || clockSkew.isNegative()
                || clockSkew.compareTo(Duration.ofSeconds(60)) > 0 || clockSkew.getNano() != 0) {
            throw new IllegalArgumentException("La tolerancia JWT debe estar entre 0 y 60 segundos enteros.");
        }
    }

    /**
     * Describe el contrato sin inspeccionar el material de las claves.
     * @return descripción sin credenciales ni archivos de claves
     */
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", audience=" + audience
                + ", accessTokenTtl=" + accessTokenTtl + ", clockSkew=" + clockSkew + "]";
    }
}
