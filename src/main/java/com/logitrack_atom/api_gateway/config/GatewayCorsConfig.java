package com.logitrack_atom.api_gateway.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Define una única política CORS para las rutas públicas de Gateway.
 *
 * <p>La cadena de Spring Security procesa esta fuente antes de autenticar. No
 * instala un segundo filtro ni habilita cookies o acceso anónimo de negocio.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayCorsConfig.Origins.class)
public class GatewayCorsConfig {
    /** Crea la configuración de intercambio entre orígenes. */
    public GatewayCorsConfig() { }

    /**
     * Construye la política para orígenes explícitos; una lista vacía deniega CORS.
     * @param origins orígenes externos validados al iniciar
     * @return fuente reactiva consumida por la cadena de seguridad
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(Origins origins) {
        CorsConfiguration policy = new CorsConfiguration();
        policy.setAllowedOrigins(origins.allowedOrigins());
        policy.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
        policy.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        policy.setExposedHeaders(List.of("Location"));
        policy.setAllowCredentials(false);
        policy.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/applications/**", policy);
        return source;
    }

    /**
     * Orígenes permitidos sin rutas, credenciales, comodines o patrones de host.
     * @param allowedOrigins lista de orígenes HTTP/HTTPS; ausente significa ninguno
     */
    @ConfigurationProperties("logitrack.cors")
    public record Origins(List<String> allowedOrigins) {
        /**
         * Valida la lista y conserva una copia inmutable sin entradas duplicadas.
         * @param allowedOrigins valores enlazados desde la configuración externa
         * @throws IllegalArgumentException si un valor no representa un origen exacto
         */
        public Origins {
            allowedOrigins = allowedOrigins == null ? List.of()
                    : allowedOrigins.stream().map(Origins::validate).distinct().toList();
        }

        /**
         * Rechaza valores que amplíen la política o nunca sean un Origin válido.
         * @param value origen configurado
         * @return origen sin espacios exteriores
         * @throws IllegalArgumentException si el origen está mal formado
         */
        private static String validate(String value) {
            if (value == null || value.isBlank() || value.contains("*")) {
                throw new IllegalArgumentException("CORS requiere orígenes HTTP/HTTPS explícitos.");
            }
            String origin = value.strip();
            URI uri = URI.create(origin);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("CORS requiere un origen sin ruta, usuario, query o fragmento.");
            }
            return origin;
        }
    }
}
