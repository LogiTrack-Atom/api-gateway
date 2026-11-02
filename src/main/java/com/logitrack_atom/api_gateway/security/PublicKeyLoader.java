package com.logitrack_atom.api_gateway.security;

import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.stereotype.Component;
import java.security.interfaces.RSAPublicKey;

/**
 * Carga exclusivamente una clave pública RSA externa para verificar tokens.
 *
 * <p>No consulta URLs, no genera claves de respaldo y no admite material privado.
 * Una clave ausente o incompatible impide arrancar el servicio.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
@Component
public class PublicKeyLoader {
    /** Crea el cargador de claves públicas locales. */
    public PublicKeyLoader() { }

    /**
     * Lee un archivo PEM X.509 RSA de al menos 2048 bits.
     * @param location archivo local externo
     * @return clave pública comprobada
     * @throws IllegalStateException si no es un archivo público RSA válido
     */
    public RSAPublicKey cargar(Resource location) {
        try {
            if (location == null || !"file".equals(location.getURI().getScheme())) {
                throw new IllegalArgumentException();
            }
            try (var input = location.getInputStream()) {
                RSAPublicKey key = RsaKeyConverters.x509().convert(input);
                if (key == null || key.getModulus().bitLength() < 2048) {
                    throw new IllegalArgumentException();
                }
                return key;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo cargar la clave pública RSA local: verificar formato y permisos.");
        }
    }
}
