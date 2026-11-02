package com.logitrack_atom.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identifica la configuración cargada mediante un valor no sensible.
 *
 * <p>Dev y prod reciben el marcador desde Config Server. El perfil local lo
 * declara explícitamente para permitir comprobaciones sin infraestructura.</p>
 *
 * @param marker identificador de aplicación y ambiente, obligatorio
 * @author MaxDz
 * @version 0.3.0
 */
@ConfigurationProperties("logitrack.config")
public record ConfigMarkerProperties(String marker) {
    /**
     * Rechaza configuraciones sin evidencia de su origen esperado.
     * @param marker marcador suministrado por la fuente de configuración
     * @throws IllegalArgumentException si el marcador es nulo o vacío
     */
    public ConfigMarkerProperties {
        if (marker == null || marker.isBlank()) {
            throw new IllegalArgumentException("Falta el marcador de configuración de api-gateway.");
        }
    }
}
