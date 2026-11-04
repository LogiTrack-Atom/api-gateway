package com.logitrack_atom.gateway_fixture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * Proporciona descubrimiento controlado para probar rutas lb:// con transporte real.
 * @author MaxDz
 * @version 0.3.0
 */
@TestConfiguration(proxyBeanMethods = false)
public class RoutingDiscovery {
    /** Crea la configuración de prueba. */
    public RoutingDiscovery() { }

    /**
     * Publica tres nombres lógicos sobre un servidor HTTP efímero.
     * @param port puerto del proveedor controlado
     * @return descubrimiento de pruebas, ajeno al JAR de producción
     */
    @Bean
    public DiscoveryClient controlledDiscoveryClient(@Value("${test.provider-port}") int port) {
        return new DiscoveryClient() {
            @Override public String description() { return "Descubrimiento de pruebas de rutas"; }
            @Override public List<String> getServices() {
                return List.of("SHIPMENT-SERVICE", "COURIER-SERVICE", "SECURITY-SERVICE");
            }
            @Override public List<ServiceInstance> getInstances(String serviceId) {
                return getServices().contains(serviceId.toUpperCase(java.util.Locale.ROOT))
                        ? List.of(new DefaultServiceInstance(serviceId + "-test", serviceId, "127.0.0.1", port, false))
                        : List.of();
            }
        };
    }

    /**
     * Publica las mismas instancias para la selección reactiva del Gateway.
     * @param discovery catálogo controlado del fixture
     * @return descubrimiento reactivo de pruebas
     */
    @Bean
    public ReactiveDiscoveryClient controlledReactiveDiscoveryClient(
            @Qualifier("controlledDiscoveryClient") DiscoveryClient discovery) {
        return new ReactiveDiscoveryClient() {
            @Override public String description() { return discovery.description(); }
            @Override public Flux<String> getServices() { return Flux.fromIterable(discovery.getServices()); }
            @Override public Flux<ServiceInstance> getInstances(String serviceId) {
                return Flux.fromIterable(discovery.getInstances(serviceId));
            }
        };
    }
}
