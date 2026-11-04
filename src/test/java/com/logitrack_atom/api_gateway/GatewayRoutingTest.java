package com.logitrack_atom.api_gateway;

import com.logitrack_atom.api_gateway.support.TestRsaKeys;
import com.logitrack_atom.gateway_fixture.RoutingDiscovery;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica rutas declaradas en propiedades, LoadBalancer y StripPrefix con HTTP real.
 * El descubrimiento es controlado; Eureka real se acredita en la aceptación Docker.
 * @author MaxDz
 * @version 0.3.0
 */
class GatewayRoutingTest {
    /** Preserva método, query, cuerpo y JWT al retirar exclusivamente dos segmentos. */
    @Test
    void enrutaTresServiciosPorNombre() throws Exception {
        AtomicReference<String> observed = new AtomicReference<>();
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/", exchange -> {
            observed.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    + " " + exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"upstream\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            finally { exchange.close(); }
        });
        provider.start();
        List<String> args = new ArrayList<>(List.of("--spring.profiles.active=local", "--server.port=0",
                "--server.address=127.0.0.1", "--spring.cloud.discovery.enabled=true",
                "--spring.cloud.loadbalancer.cache.enabled=false",
                "--JWT_PUBLIC_KEY_LOCATION=" + TestRsaKeys.publicResource().getURI(),
                "--test.provider-port=" + provider.getAddress().getPort()));
        String[] services = {"security", "courier", "shipment"};
        for (int i = 0; i < services.length; i++) {
            String root = "--spring.cloud.gateway.server.webflux.routes[" + i + "]";
            args.add(root + ".id=" + services[i] + "-service");
            args.add(root + ".uri=lb://" + services[i].toUpperCase(java.util.Locale.ROOT) + "-SERVICE");
            args.add(root + ".predicates[0]=Path=/applications/api" + services[i] + "/**");
            args.add(root + ".filters[0]=StripPrefix=2");
        }
        try (var context = new SpringApplicationBuilder(ApiGatewayApplication.class, RoutingDiscovery.class)
                .properties("spring.main.banner-mode=off", "logging.level.root=ERROR").run(args.toArray(String[]::new));
             var client = HttpClient.newHttpClient()) {
            assertThat(context.getBean(RouteDefinitionLocator.class).getRouteDefinitions().collectList()
                    .block(Duration.ofSeconds(5))).hasSize(3);
            String base = "http://127.0.0.1:" + context.getEnvironment().getRequiredProperty("local.server.port");
            String token = TestRsaKeys.token(UUID.randomUUID(), "ADMIN");
            for (String service : services) {
                String path = service.equals("security") ? "/api/v1/auth/login"
                        : service.equals("courier") ? "/api/v1/repartidores" : "/api/v1/envios";
                var response = client.send(HttpRequest.newBuilder(URI.create(base + "/applications/api" + service + path + "?demo=1"))
                        .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"demo\":true}")).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(response.body()).contains("upstream");
                assertThat(observed.get()).startsWith("POST " + path + "?demo=1 {\"demo\":true}");
                if (!service.equals("security")) {
                    assertThat(observed.get()).endsWith("Bearer " + token);
                }
            }
        } finally {
            provider.stop(0);
        }
    }
}
