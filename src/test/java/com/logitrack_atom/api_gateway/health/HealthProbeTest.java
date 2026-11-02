package com.logitrack_atom.api_gateway.health;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que la sonda del contenedor distingue salud, redirecciones e indisponibilidad.
 * @author MaxDz
 * @version 0.3.0
 */
class HealthProbeTest {
    /**
     * Solo HTTP 200 representa salud y las redirecciones no se siguen.
     * @param status respuesta simulada del endpoint local
     * @throws Exception si no se puede iniciar el fixture HTTP
     */
    @ParameterizedTest
    @ValueSource(ints = {200, 302, 503})
    void onlyAcceptsHealthyResponse(int status) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/actuator/health", exchange -> {
            if (status == 302) exchange.getResponseHeaders().set("Location", "/healthy");
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.createContext("/healthy", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/actuator/health");
            assertThat(HealthProbe.healthy(endpoint)).isEqualTo(status == 200);
        } finally {
            server.stop(0);
        }
    }
}
