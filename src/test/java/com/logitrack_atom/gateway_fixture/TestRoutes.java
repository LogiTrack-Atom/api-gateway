package com.logitrack_atom.gateway_fixture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

/**
 * Define rutas de observación fuera del paquete de escaneo de la aplicación.
 *
 * <p>Este fixture se carga explícitamente y no se incluye en el JAR de producción.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestRoutes {
    /** Crea la configuración del fixture. */
    public TestRoutes() { }

    /**
     * Dirige los prefijos de prueba al servidor HTTP controlado por la suite.
     * @param builder constructor de rutas
     * @param uri destino de loopback con puerto efímero
     * @return rutas disponibles únicamente en las pruebas de seguridad
     */
    @Bean
    public RouteLocator fixtureRoutes(RouteLocatorBuilder builder, @Value("${test.upstream-url}") String uri) {
        return builder.routes()
                .route("shipment-fixture", route -> route.path("/applications/apishipment/**").filters(filter -> filter.stripPrefix(2)).uri(uri))
                .route("courier-fixture", route -> route.path("/applications/apicourier/**").filters(filter -> filter.stripPrefix(2)).uri(uri))
                .route("security-fixture", route -> route.path("/applications/apisecurity/**").filters(filter -> filter.stripPrefix(2)).uri(uri))
                .build();
    }
}
