package com.logitrack_atom.api_gateway.security;

import com.logitrack_atom.api_gateway.ApiGatewayApplication;
import com.logitrack_atom.api_gateway.config.GatewayCorsConfig;
import com.logitrack_atom.api_gateway.support.TestRsaKeys;
import com.logitrack_atom.gateway_fixture.TestRoutes;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba CORS y autenticación con HTTP real, Gateway reactivo y proveedor local.
 * Las claves son efímeras; este proveedor no sustituye la aceptación del dominio.
 */
class GatewayCorsTest {
    private static final String ORIGIN = "https://frontend.example.test";
    private static final String PATH = "/applications/apishipment/api/v1/envios/"
            + "8dd75f1a-30f6-4e93-aad4-9fe667112025/estado";
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicReference<String> OBSERVED = new AtomicReference<>();
    private static HttpServer provider;
    private static ConfigurableApplicationContext gateway;
    private static HttpClient client;
    private static String baseUrl;

    /**
     * Inicia únicamente servidores loopback efímeros con la cadena de seguridad real.
     * @throws Exception si falla la preparación HTTP
     */
    @BeforeAll
    static void start() throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/", exchange -> {
            CALLS.incrementAndGet();
            OBSERVED.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"resultado\":\"proveedor-de-prueba\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Location", "/api/v1/envios/42");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            finally { exchange.close(); }
        });
        provider.start();
        try {
            gateway = new SpringApplicationBuilder(ApiGatewayApplication.class, TestRoutes.class)
                    .properties("spring.main.banner-mode=off", "logging.level.root=ERROR")
                    .run("--spring.profiles.active=local", "--server.port=0", "--server.address=127.0.0.1",
                            "--JWT_PUBLIC_KEY_LOCATION=" + TestRsaKeys.publicResource().getURI(),
                            "--logitrack.cors.allowed-origins=" + ORIGIN,
                            "--test.upstream-url=http://127.0.0.1:" + provider.getAddress().getPort());
            baseUrl = "http://127.0.0.1:" + gateway.getEnvironment().getRequiredProperty("local.server.port");
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        } catch (Exception ex) {
            provider.stop(0);
            throw ex;
        }
    }

    /** Libera los recursos propios incluso cuando una aserción falla. */
    @AfterAll
    static void stop() {
        if (client != null) client.close();
        if (gateway != null) gateway.close();
        if (provider != null) provider.stop(0);
    }

    /**
     * El preflight PATCH con cabeceras permitidas no requiere JWT ni toca el proveedor.
     * @throws Exception si falla el transporte HTTP
     */
    @Test
    void preflightPrecedesAuthentication() throws Exception {
        int before = CALLS.get();
        var response = preflight(ORIGIN, "PATCH", "authorization,content-type");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ORIGIN);
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods").orElse("")).contains("PATCH");
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase())
                .contains("authorization", "content-type");
        assertThat(response.headers().allValues("Access-Control-Allow-Credentials")).isEmpty();
        assertThat(String.join(",", response.headers().allValues("Vary"))).contains("Origin");
        assertThat(CALLS.get()).isEqualTo(before);
    }

    /**
     * La cabecera de idempotencia puede atravesar el preflight sin habilitar headers arbitrarios.
     * @throws Exception si falla el transporte del fixture HTTP
     */
    @Test
    void permitsIdempotencyKeyForAsyncAcceptance() throws Exception {
        int before = CALLS.get();
        var response = preflight(ORIGIN, "POST", "authorization,content-type,idempotency-key");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase()).contains("idempotency-key");
        assertThat(CALLS.get()).isEqualTo(before);
    }

    /**
     * Un origen no declarado no recibe permiso CORS ni llega al proveedor.
     * @param origin origen ajeno o no representable
     * @throws Exception si falla el transporte HTTP
     */
    @ParameterizedTest
    @ValueSource(strings = {"https://ajeno.example.test", "null", "https://frontend.example.test.evil"})
    void rejectsForeignOrigins(String origin) throws Exception {
        int before = CALLS.get();
        var response = preflight(origin, "PATCH", "authorization,content-type");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).isEmpty();
        assertThat(CALLS.get()).isEqualTo(before);
    }

    /**
     * No se conceden métodos de negocio inexistentes ni encabezados arbitrarios.
     * @throws Exception si falla el transporte HTTP
     */
    @Test
    void rejectsUndeclaredMethodAndHeader() throws Exception {
        int before = CALLS.get();
        assertThat(preflight(ORIGIN, "DELETE", "authorization").statusCode()).isEqualTo(403);
        assertThat(preflight(ORIGIN, "PATCH", "x-admin").statusCode()).isEqualTo(403);
        assertThat(CALLS.get()).isEqualTo(before);
    }

    /**
     * CORS deja leer el 401 al frontend permitido, sin abrir el acceso de negocio.
     * @throws Exception si falla el transporte HTTP
     */
    @Test
    void anonymousBusinessStillRequiresJwt() throws Exception {
        int before = CALLS.get();
        var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + PATH))
                .header("Origin", ORIGIN).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ORIGIN);
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertThat(CALLS.get()).isEqualTo(before);
    }

    /**
     * PATCH autenticado cruza Gateway con método, cuerpo y prefijo interno correctos.
     * @throws Exception si falla el transporte HTTP
     */
    @Test
    void authenticatedPatchHasOneCorsPolicy() throws Exception {
        String payload = "{\"estado\":\"EN_RUTA\"}";
        var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + PATH))
                .header("Origin", ORIGIN).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + TestRsaKeys.token(UUID.randomUUID(), "ADMIN"))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(OBSERVED.get()).isEqualTo("PATCH " + PATH.substring("/applications/apishipment".length()) + " " + payload);
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ORIGIN);
        assertThat(response.headers().allValues("Access-Control-Expose-Headers")).containsExactly("Location");
        assertThat(response.headers().allValues("Access-Control-Allow-Credentials")).isEmpty();
    }

    /** Una configuración ausente o vacía deniega CORS, conservando llamadas sin Origin. */
    @Test
    void defaultPolicyIsClosed() {
        for (String property : List.of("", "logitrack.cors.allowed-origins=")) {
            var runner = new ApplicationContextRunner().withUserConfiguration(GatewayCorsConfig.class);
            if (!property.isEmpty()) runner = runner.withPropertyValues(property);
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(GatewayCorsConfig.Origins.class).allowedOrigins()).isEmpty();
                var source = context.getBean(org.springframework.web.cors.reactive.CorsConfigurationSource.class);
                var exchange = org.springframework.mock.web.server.MockServerWebExchange.from(
                        org.springframework.mock.http.server.reactive.MockServerHttpRequest.get(PATH));
                assertThat(source.getCorsConfiguration(exchange).checkOrigin(ORIGIN)).isNull();
            });
        }
    }

    /**
     * La configuración falla ante comodines, rutas, credenciales o esquemas ajenos.
     * @param origin origen inválido en la configuración
     */
    @ParameterizedTest
    @ValueSource(strings = {"*", "null", "https://*.example.test", "https://example.test/", "https://user@example.test",
            "https://example.test?a=1", "https://example.test#x", "file:///tmp/frontend", "http://localhost:0"})
    void rejectsUnsafeConfiguration(String origin) {
        assertThatThrownBy(() -> new GatewayCorsConfig.Origins(List.of(origin)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Envía el preflight que realizaría un navegador para una operación protegida.
     * @param origin origen de la página
     * @param method método que se pretende ejecutar
     * @param headers cabeceras solicitadas por la página
     * @return respuesta real del Gateway
     * @throws Exception si falla el transporte HTTP
     */
    private static HttpResponse<String> preflight(String origin, String method, String headers) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + PATH)).timeout(Duration.ofSeconds(5))
                .header("Origin", origin).header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", headers).method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
