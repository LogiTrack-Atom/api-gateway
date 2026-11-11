package com.logitrack_atom.api_gateway.config;

import java.time.Duration;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Acota la caché DNS del cliente de enrutamiento para seguir los cambios de IP de Compose. */
@Configuration(proxyBeanMethods = false)
class GatewayDnsConfig {

    /**
     * Conserva por hasta cinco segundos las resoluciones positivas de los destinos.
     * Una ausencia temporal durante el reinicio de un servicio no se guarda en caché.
     *
     * @return personalización del cliente HTTP de Gateway
     */
    @Bean
    HttpClientCustomizer gatewayDnsCache() {
        return client -> client.resolver(dns -> dns
                .cacheMaxTimeToLive(Duration.ofSeconds(5))
                .cacheMinTimeToLive(Duration.ZERO)
                .cacheNegativeTimeToLive(Duration.ZERO));
    }
}
