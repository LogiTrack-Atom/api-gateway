package com.logitrack_atom.api_gateway;

import com.logitrack_atom.api_gateway.config.ConfigMarkerProperties;
import com.logitrack_atom.api_gateway.support.TestRsaKeys;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba arranque reactivo, consumo remoto obligatorio y superficie HTTP inicial.
 *
 * <p>El fixture implementa únicamente el protocolo de lectura de Config Server.
 * No acredita registro real en Eureka ni integración con el backend Git del equipo.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
class GatewayBootstrapTest {
    /** La salud local no publica detalles y la base no expone rutas de negocio o diagnóstico. */
    @Test
    void localHealthDoesNotExposeDiagnosticsOrBusinessRoutes() throws Exception {
        try (var context = application().run("--spring.profiles.active=local", "--server.port=0", "--server.address=127.0.0.1")) {
            assertThat(context.getBean(ConfigMarkerProperties.class).marker()).isEqualTo("api-gateway-local");
            assertThat(context.getEnvironment().getProperty("eureka.client.enabled", Boolean.class)).isFalse();
            var health = get(context, "/actuator/health");
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("\"status\":\"UP\"")
                    .doesNotContain("components", "details", "marker", "password");
            for (String path : List.of("/actuator/env", "/actuator/configprops", "/actuator/gateway/routes",
                    "/applications/apisecurity/api/v1/auth/login", "/applications/apishipment/api/v1/envios")) {
                assertThat(get(context, path).statusCode()).as(path).isEqualTo(401);
            }
            assertThat(context.getClass().getName()).contains("Reactive");
        }
    }

    /** Dev y prod solicitan la aplicación, perfil y label acordados y enlazan la fuente remota. */
    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void consumesRemoteConfiguration(String profile) throws Exception {
        try (var fixture = new ConfigFixture(profile, true);
             var context = remote(profile, fixture.url())) {
            assertThat(context.getBean(ConfigMarkerProperties.class).marker()).isEqualTo("api-gateway-" + profile);
            assertThat(context.getEnvironment().getPropertySources().stream()
                    .anyMatch(source -> source.getName().contains("configserver:"))).isTrue();
            assertThat(fixture.requests).contains("/api-gateway/" + profile + "/develop");
            assertThat(context.containsBean("compositeCompatibilityVerifier")).isTrue();
            assertThat(get(context, "/actuator/health").statusCode()).isEqualTo(200);
        }
    }

    /** Una respuesta HTTP válida sin las propiedades de la aplicación no habilita el arranque. */
    @Test
    void missingRemoteMarkerStopsStartup() throws Exception {
        try (var fixture = new ConfigFixture("dev", false)) {
            assertThatThrownBy(() -> {
                try (var ignored = remote("dev", fixture.url())) {
                    throw new AssertionError("No debe iniciar sin marcador remoto.");
                }
            }).hasStackTraceContaining("Falta el marcador de configuración de api-gateway");
        }
    }

    /** La importación obligatoria impide iniciar prod si Config Server no está disponible. */
    @Test
    void unavailableConfigServerStopsProductionStartup() throws Exception {
        String unavailable;
        try (var fixture = new ConfigFixture("prod", true)) {
            unavailable = fixture.url();
        }
        assertThatThrownBy(() -> {
            try (var ignored = remote("prod", unavailable)) {
                throw new AssertionError("No debe iniciar sin Config Server.");
            }
        }).hasStackTraceContaining("Could not locate PropertySource");
    }

    /**
     * Construye el contexto sin desactivar la comprobación de compatibilidad de Spring Cloud.
     * @return constructor con logging reducido en las pruebas
     */
    private static SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(ApiGatewayApplication.class)
                .properties(java.util.Map.of("spring.main.banner-mode", "off", "logging.level.root", "ERROR",
                        "JWT_PUBLIC_KEY_LOCATION", "file:" + TestRsaKeys.publicResource().getPath()));
    }

    /**
     * Arranca el cliente con un servidor de configuración controlado por la prueba.
     * @param profile ambiente que se solicita
     * @param url origen local del fixture
     * @return contexto cuyo cierre corresponde al llamador
     */
    private static ConfigurableApplicationContext remote(String profile, String url) {
        return application().run("--spring.profiles.active=" + profile,
                "--CONFIG_SERVER_URL=" + url, "--CONFIG_LABEL=develop", "--server.port=0",
                "--server.address=127.0.0.1");
    }

    /**
     * Consulta el servidor reactivo mediante un cliente HTTP ajeno al contexto de Spring.
     * @param context aplicación iniciada en puerto efímero
     * @param path ruta HTTP que se consulta
     * @return estado y cuerpo recibidos
     * @throws Exception si falla el transporte de la prueba
     */
    private static HttpResponse<String> get(ConfigurableApplicationContext context, String path) throws Exception {
        String port = context.getEnvironment().getRequiredProperty("local.server.port");
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    /** Fixture HTTP descartable del protocolo Environment de Config Server. */
    private static final class ConfigFixture implements AutoCloseable {
        private final HttpServer server;
        private final List<String> requests = new CopyOnWriteArrayList<>();

        /**
         * Publica propiedades sin depender de Git, Eureka o una base de datos.
         * @param profile ambiente de prueba
         * @param includeMarker indica si se entrega el marcador obligatorio
         * @throws IOException si no se puede reservar un puerto local
         */
        private ConfigFixture(String profile, boolean includeMarker) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                requests.add(exchange.getRequestURI().getPath());
                String marker = includeMarker ? ",\"logitrack.config.marker\":\"api-gateway-" + profile + "\"" : "";
                String body = "{\"name\":\"api-gateway\",\"profiles\":[\"" + profile
                        + "\"],\"label\":\"develop\",\"version\":\"fixture-sprint03\",\"propertySources\":[{"
                        + "\"name\":\"fixture/api-gateway-" + profile + ".yml\",\"source\":{"
                        + "\"eureka.client.enabled\":false,\"spring.cloud.discovery.enabled\":false,"
                        + "\"logitrack.security.issuer\":\"logitrack-security\","
                        + "\"logitrack.security.audience\":\"logitrack-api\","
                        + "\"logitrack.security.access-token-ttl\":\"15m\","
                        + "\"logitrack.security.clock-skew\":\"60s\","
                        + "\"logitrack.security.public-key-location\":\"${JWT_PUBLIC_KEY_LOCATION}\"" + marker + "}}]}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        /**
         * Identifica el servidor para el cliente que carga configuración.
         * @return origen HTTP de loopback con puerto efímero
         */
        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        /** Detiene el servidor y libera su puerto al finalizar cada caso. */
        @Override
        public void close() {
            server.stop(0);
        }
    }
}
