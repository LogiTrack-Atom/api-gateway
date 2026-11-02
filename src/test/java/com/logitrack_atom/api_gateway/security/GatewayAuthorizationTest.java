package com.logitrack_atom.api_gateway.security;

import com.logitrack_atom.api_gateway.ApiGatewayApplication;
import com.logitrack_atom.api_gateway.support.TestRsaKeys;
import com.logitrack_atom.gateway_fixture.TestRoutes;
import com.nimbusds.jwt.JWTClaimsSet;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica autenticación y autorización con HTTP real, JWT firmados y un destino controlado.
 *
 * <p>Las rutas de prueba no reemplazan las definiciones lb:// de la issue de ruteo.
 * La pertenencia sigue siendo responsabilidad del servicio de destino.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
class GatewayAuthorizationTest {
    private static final String SHIPMENT = "/applications/apishipment/api/v1";
    private static final String COURIER = "/applications/apicourier/api/v1";
    private static final String SECURITY = "/applications/apisecurity/api/v1";
    private static final String RESOURCE = "8dd75f1a-30f6-4e93-aad4-9fe667112025";
    private static final UUID USER = UUID.fromString("9ed7751a-30f6-4e93-aad4-9fe667112025");
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicReference<String> AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> SPOOFED = new AtomicReference<>();
    private static final AtomicInteger AUTH_HEADER_COUNT = new AtomicInteger();
    private static HttpServer upstream;
    private static ConfigurableApplicationContext application;
    private static HttpClient client;
    private static String baseUrl;

    /** Inicia un Gateway reactivo real y un destino local sin credenciales ni datos externos. */
    @BeforeAll
    static void start() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            CALLS.incrementAndGet();
            var headers = exchange.getRequestHeaders();
            AUTHORIZATION.set(headers.getFirst("Authorization"));
            var authorization = headers.get("Authorization");
            AUTH_HEADER_COUNT.set(authorization == null ? 0 : authorization.size());
            SPOOFED.set(Stream.of("X-User-Id", "X-User-Roles", "X-Authenticated-User", "X-Authenticated-Roles")
                    .map(headers::getFirst).filter(value -> value != null).findFirst().orElse(null));
            byte[] response = "{\"resultado\":\"destino-de-prueba\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
            finally { exchange.close(); }
        });
        upstream.start();
        try {
            application = new SpringApplicationBuilder(ApiGatewayApplication.class, TestRoutes.class)
                    .properties("spring.main.banner-mode=off", "logging.level.root=ERROR")
                    .run("--spring.profiles.active=local", "--server.port=0", "--server.address=127.0.0.1",
                            "--JWT_PUBLIC_KEY_LOCATION=" + TestRsaKeys.publicResource().getURI(),
                            "--test.upstream-url=http://127.0.0.1:" + upstream.getAddress().getPort());
            baseUrl = "http://127.0.0.1:" + application.getEnvironment().getRequiredProperty("local.server.port");
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        } catch (Exception ex) {
            upstream.stop(0);
            throw ex;
        }
    }

    /** Libera ambos servidores y sus conexiones al terminar la suite. */
    @AfterAll
    static void stop() {
        if (client != null) client.close();
        if (application != null) application.close();
        if (upstream != null) upstream.stop(0);
    }

    /**
     * Cada combinación método/ruta/rol coincide con el contrato del perímetro.
     * @param method método solicitado
     * @param path ruta pública completa
     * @param role rol firmado de la cuenta sintética
     * @param expected código esperado
     * @throws Exception si falla el transporte del fixture
     */
    @ParameterizedTest(name = "{0} {1} como {2}: {3}")
    @MethodSource("roleMatrix")
    void enforcesRoleMatrix(String method, String path, String role, int expected) throws Exception {
        int before = CALLS.get();
        var response = send(method, path, "Bearer " + TestRsaKeys.token(USER, role));
        assertThat(response.statusCode()).isEqualTo(expected);
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        if (expected == 403) {
            error(response, 403);
            assertThat(CALLS.get()).isEqualTo(before);
        } else {
            assertThat(CALLS.get()).isEqualTo(before + 1);
        }
    }

    /**
     * Construye la matriz completa, incluyendo identidades y diagnóstico administrativo.
     * @return combinaciones de endpoints y roles conocidos
     */
    static Stream<Arguments> roleMatrix() {
        Set<String> all = Set.of("ADMIN", "CLIENTE", "REPARTIDOR");
        Set<String> admin = Set.of("ADMIN");
        Set<String> customer = Set.of("ADMIN", "CLIENTE");
        Set<String> driver = Set.of("ADMIN", "REPARTIDOR");
        return Stream.of(
                rule("GET", SECURITY + "/auth/me", all),
                rule("POST", SECURITY + "/usuarios", admin),
                rule("GET", SECURITY + "/usuarios/" + RESOURCE, admin),
                rule("GET", SHIPMENT + "/envios", all),
                rule("GET", SHIPMENT + "/envios/" + RESOURCE, all),
                rule("GET", SHIPMENT + "/envios/seguimiento/LT-PRUEBA", all),
                rule("GET", SHIPMENT + "/envios/" + RESOURCE + "/historial", all),
                rule("POST", SHIPMENT + "/envios", customer),
                rule("PATCH", SHIPMENT + "/envios/" + RESOURCE + "/estado", driver),
                rule("PATCH", SHIPMENT + "/envios/" + RESOURCE + "/cancelacion", admin),
                rule("PUT", SHIPMENT + "/envios/" + RESOURCE + "/repartidor", admin),
                rule("PUT", SHIPMENT + "/clientes/" + RESOURCE + "/usuario", admin),
                rule("PUT", SHIPMENT + "/asociaciones/repartidores/" + RESOURCE, admin),
                rule("GET", COURIER + "/repartidores", admin),
                rule("GET", COURIER + "/repartidores/" + RESOURCE + "/usuario", admin),
                rule("GET", COURIER + "/repartidores/" + RESOURCE, driver),
                rule("GET", COURIER + "/repartidores/" + RESOURCE + "/disponibilidad", driver),
                rule("POST", COURIER + "/repartidores", admin),
                rule("PUT", COURIER + "/repartidores/" + RESOURCE, admin),
                rule("PUT", COURIER + "/repartidores/" + RESOURCE + "/usuario", admin),
                rule("PATCH", COURIER + "/repartidores/" + RESOURCE + "/estado", admin),
                rule("GET", COURIER + "/admin/configuracion", admin))
                .flatMap(rule -> all.stream().map(role ->
                        Arguments.of(rule.method(), rule.path(), role, rule.allowed().contains(role) ? 200 : 403)));
    }

    /**
     * El registro y login admiten acceso público solamente mediante POST.
     * @param operation operación pública
     * @throws Exception si falla el transporte de la prueba
     */
    @ParameterizedTest
    @ValueSource(strings = {"login", "register"})
    void permitsOnlyExplicitPublicOperations(String operation) throws Exception {
        int before = CALLS.get();
        assertThat(send("POST", SECURITY + "/auth/" + operation, null).statusCode()).isEqualTo(200);
        assertThat(CALLS.get()).isEqualTo(before + 1);
        assertThat(AUTHORIZATION.get()).isNull();
        error(send("GET", SECURITY + "/auth/" + operation, null), 401);
        error(send("POST", SECURITY + "/auth/" + operation, "Bearer token-invalido"), 401);
    }

    /** El acceso anterior no crea una sesión ni convierte cookies o query strings en autenticación. */
    @Test
    void requiresBearerForEveryProtectedRequest() throws Exception {
        String token = TestRsaKeys.token(USER, "CLIENTE");
        String path = SHIPMENT + "/envios";
        assertThat(send("GET", path, "Bearer " + token).statusCode()).isEqualTo(200);
        int before = CALLS.get();
        error(send("GET", path, null), 401);
        error(send("GET", path + "?access_token=" + token, null), 401);
        error(send("GET", path, "Basic dXN1YXJpbzpjbGF2ZQ=="), 401);
        var cookie = client.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Cookie", "SESSION=sesion-sintetica").GET().build(), HttpResponse.BodyHandlers.ofString());
        error(cookie, 401);
        assertThat(CALLS.get()).isEqualTo(before);
    }

    /**
     * Los claims incorrectos se rechazan aunque la firma pertenezca al emisor.
     * @param name caso del contrato
     * @param claims claims sintéticos que incumplen la regla
     * @throws Exception si falla el transporte de la prueba
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidClaims")
    void rejectsInvalidClaimsBeforeRouting(String name, JWTClaimsSet claims) throws Exception {
        int before = CALLS.get();
        error(send("GET", SHIPMENT + "/envios", "Bearer " + TestRsaKeys.firmar(claims)), 401);
        assertThat(CALLS.get()).as(name).isEqualTo(before);
    }

    /**
     * Proporciona fallos de identidad, roles y tiempos para la frontera HTTP real.
     * @return tokens con firma válida pero contrato incorrecto
     */
    static Stream<Arguments> invalidClaims() {
        Instant now = Instant.now();
        return Stream.of(
                Arguments.of("emisor ajeno", claims().issuer("otro-emisor").build()),
                Arguments.of("audiencia ajena", claims().audience("otra-api").build()),
                Arguments.of("sin sub", claims().subject(null).build()),
                Arguments.of("sub no UUID", claims().subject("admin@example.test").build()),
                Arguments.of("sin roles", claims().claim("roles", null).build()),
                Arguments.of("roles vacíos", claims().claim("roles", List.of()).build()),
                Arguments.of("roles como texto", claims().claim("roles", "ADMIN").build()),
                Arguments.of("rol desconocido", claims().claim("roles", List.of("SUPER_ADMIN")).build()),
                Arguments.of("rol no textual", claims().claim("roles", List.of(1)).build()),
                Arguments.of("sin exp", claims().expirationTime(null).build()),
                Arguments.of("sin iat", claims().issueTime(null).build()),
                Arguments.of("vencido", claims().issueTime(Date.from(now.minusSeconds(1000)))
                        .expirationTime(Date.from(now.minusSeconds(100))).build()),
                Arguments.of("nbf futuro", claims().notBeforeTime(Date.from(now.plusSeconds(300))).build()),
                Arguments.of("iat futuro", claims().issueTime(Date.from(now.plusSeconds(120)))
                        .expirationTime(Date.from(now.plusSeconds(900))).build()),
                Arguments.of("vida excesiva", claims().expirationTime(Date.from(now.plusSeconds(1800))).build()));
    }

    /** Una firma modificada y un encabezado Authorization ambiguo no llegan al destino. */
    @Test
    void rejectsTamperingAndDuplicateAuthorization() throws Exception {
        String token = TestRsaKeys.token(USER, "ADMIN");
        int signature = token.lastIndexOf('.') + 1;
        char altered = token.charAt(signature) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signature) + altered + token.substring(signature + 1);
        int before = CALLS.get();
        error(send("GET", SHIPMENT + "/envios", "Bearer " + tampered), 401);
        error(send("GET", SHIPMENT + "/envios", "Bearer " + token + ", Bearer " + token), 401);
        var request = HttpRequest.newBuilder(URI.create(baseUrl + SHIPMENT + "/envios"))
                .header("Authorization", "Bearer " + token).header("Authorization", "Bearer " + token).GET().build();
        error(client.send(request, HttpResponse.BodyHandlers.ofString()), 401);
        assertThat(CALLS.get()).isEqualTo(before);
    }

    /** Solo el Bearer validado se reenvía una vez y se retira la identidad declarada por cabeceras. */
    @Test
    void forwardsValidatedBearerWithoutClientIdentityHeaders() throws Exception {
        String token = TestRsaKeys.token(USER, "CLIENTE");
        var request = HttpRequest.newBuilder(URI.create(baseUrl + SHIPMENT + "/envios"))
                .header("Authorization", "bearer " + token)
                .header("X-User-Id", "identidad-ajena").header("X-User-Roles", "ADMIN")
                .header("X-Authenticated-User", "identidad-ajena").header("X-Authenticated-Roles", "ADMIN")
                .GET().build();
        int before = CALLS.get();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        assertThat(CALLS.get()).isEqualTo(before + 1);
        assertThat(AUTHORIZATION.get()).isEqualTo("Bearer " + token);
        assertThat(AUTH_HEADER_COUNT.get()).isEqualTo(1);
        assertThat(SPOOFED.get()).isNull();
    }

    /** Un esquema ajeno en login público se retira sin duplicar el envío a la ruta. */
    @Test
    void stripsUnvalidatedAuthorizationFromPublicLogin() throws Exception {
        int before = CALLS.get();
        assertThat(send("POST", SECURITY + "/auth/login", "Basic dXN1YXJpbzpjbGF2ZQ==").statusCode()).isEqualTo(200);
        assertThat(CALLS.get()).isEqualTo(before + 1);
        assertThat(AUTHORIZATION.get()).isNull();
    }

    /**
     * Un ADMIN tampoco abre rutas no declaradas, diagnósticos o endpoints por otro método.
     * @param path ruta ajena a las operaciones permitidas
     * @throws Exception si falla el transporte de la prueba
     */
    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/configprops", "/actuator/gateway/routes",
            "/api/v1/envios", "/applications/apisecurity/api/v1/auth/desconocido",
            "/applications/apishipment/api/v1/desconocido", "/applications/apicourier/actuator/health"})
    void deniesUndeclaredRoutes(String path) throws Exception {
        int before = CALLS.get();
        error(send("GET", path, "Bearer " + TestRsaKeys.token(USER, "ADMIN")), 403);
        assertThat(CALLS.get()).isEqualTo(before);
    }

    /**
     * Construye una regla de datos para la matriz de autorización.
     * @param method método HTTP
     * @param path ruta pública
     * @param roles roles permitidos
     * @return regla de prueba
     */
    private static Rule rule(String method, String path, Set<String> roles) { return new Rule(method, path, roles); }

    /**
     * Construye claims válidos relativos al reloj real de la aplicación bajo prueba.
     * @return builder de token CLIENTE sintético
     */
    private static JWTClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().subject(USER.toString()).issuer("logitrack-security")
                .audience("logitrack-api").claim("roles", List.of("CLIENTE"))
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(900)));
    }

    /**
     * Envía una petición sin soporte de cookies o sesiones compartidas.
     * @param method método HTTP
     * @param path ruta con prefijo público
     * @param authorization encabezado de autorización o null
     * @return estado y cuerpo devueltos por Gateway
     * @throws Exception si falla el transporte de la prueba
     */
    private static HttpResponse<String> send(String method, String path, String authorization) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(5));
        if (authorization != null) builder.header("Authorization", authorization);
        return client.send(builder.method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Comprueba el contrato de error sin cuerpos internos ni credenciales reflejadas.
     * @param response respuesta del Gateway
     * @param status estado esperado
     */
    private static void error(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        var body = JsonMapper.builder().build().readValue(response.body(), Map.class);
        assertThat(body.keySet()).containsExactlyInAnyOrder("timestamp", "status", "error", "mensaje", "detalles");
        assertThat(body.get("status")).isEqualTo(status);
        assertThat(body.get("detalles")).isEqualTo(List.of());
        Instant.parse(body.get("timestamp").toString());
        assertThat(response.body()).doesNotContain("Bearer eyJ", "token-invalido", "stackTrace", "identidad-ajena");
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        if (status == 401) assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
    }

    /** Datos de una operación pública y los roles admitidos por su contrato. */
    private record Rule(String method, String path, Set<String> allowed) { }

}
