package com.logitrack_atom.api_gateway.security;

import com.logitrack_atom.api_gateway.config.JwtConfig;
import com.logitrack_atom.api_gateway.config.JwtProperties;
import com.logitrack_atom.api_gateway.support.TestRsaKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jwt.JwtException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

/**
 * Verifica firma y contrato con un reloj fijo y sin base ni servicio HTTP.
 *
 * @author MaxDz
 * @version 0.3.0
 */
class JwtSecurityTest {
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID USER_ID = UUID.fromString("9ed7751a-30f6-4e93-aad4-9fe667112025");
    private static final JwtConfig CONFIG = new JwtConfig();
    private static final JwtProperties PROPERTIES = new JwtProperties("logitrack-security", "logitrack-api",
            Duration.ofMinutes(15), Duration.ofSeconds(60), TestRsaKeys.publicResource());

    /** La verificación RS256 necesita exclusivamente la clave pública. */
    @Test
    void validaSoloConPublica() throws Exception {
        var decoder = CONFIG.jwtDecoder((RSAPublicKey) TestRsaKeys.pair().getPublic(), PROPERTIES, CLOCK);
        var jwt = decoder.decode(firmar(validClaims().build())).block();
        assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(jwt.getAudience()).containsExactly("logitrack-api");
    }

    /** Los roles se traducen explícitamente a autoridades de negocio. */
    @Test
    void convierteRolesSinConfundirlosConScopes() throws Exception {
        var claims = validClaims().claim("roles", List.of("ADMIN", "REPARTIDOR")).build();
        var jwt = CONFIG.jwtDecoder((RSAPublicKey) TestRsaKeys.pair().getPublic(), PROPERTIES, CLOCK).decode(firmar(claims)).block();
        var authentication = CONFIG.jwtAuthenticationConverter().convert(jwt).block();
        assertThat(authentication.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_REPARTIDOR", "FACTOR_BEARER");
    }

    /** Rechaza claims incompletos o incoherentes aunque la firma sea auténtica. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("claimsInvalidos")
    void rechazaContratoInvalido(String caso, JWTClaimsSet claims) throws Exception {
        var decoder = CONFIG.jwtDecoder((RSAPublicKey) TestRsaKeys.pair().getPublic(), PROPERTIES, CLOCK);
        assertThatThrownBy(() -> decoder.decode(firmar(claims)).block()).as(caso).isInstanceOf(JwtException.class);
    }

    /** Construye fallos independientes del validador temporal y del contrato de identidad. */
    static Stream<Arguments> claimsInvalidos() {
        return Stream.of(
                Arguments.of("vencido fuera de tolerancia", validClaims().issueTime(Date.from(NOW.minusSeconds(1000)))
                        .notBeforeTime(Date.from(NOW.minusSeconds(1000))).expirationTime(Date.from(NOW.minusSeconds(100))).build()),
                Arguments.of("nbf futuro", validClaims().notBeforeTime(Date.from(NOW.plusSeconds(61))).build()),
                Arguments.of("iat futuro", validClaims().issueTime(Date.from(NOW.plusSeconds(61)))
                        .expirationTime(Date.from(NOW.plusSeconds(961))).build()),
                Arguments.of("emisor ajeno", validClaims().issuer("otro-emisor").build()),
                Arguments.of("audiencia ajena", validClaims().audience("otra-api").build()),
                Arguments.of("sin audiencia", validClaims().audience((String) null).build()),
                Arguments.of("sin emisor", validClaims().issuer(null).build()),
                Arguments.of("sin exp", validClaims().expirationTime(null).build()),
                Arguments.of("sin iat", validClaims().issueTime(null).build()),
                Arguments.of("sin sub", validClaims().subject(null).build()),
                Arguments.of("sub no UUID", validClaims().subject("admin@example.com").build()),
                Arguments.of("UUID abreviado", validClaims().subject("1-1-1-1-1").build()),
                Arguments.of("sin roles", validClaims().claim("roles", null).build()),
                Arguments.of("roles vacíos", validClaims().claim("roles", List.of()).build()),
                Arguments.of("rol desconocido", validClaims().claim("roles", List.of("SUPER_ADMIN")).build()),
                Arguments.of("roles no lista", validClaims().claim("roles", "ADMIN").build()),
                Arguments.of("rol no texto", validClaims().claim("roles", List.of(123)).build()),
                Arguments.of("vida excesiva", validClaims().expirationTime(Date.from(NOW.plusSeconds(901))).build()));
    }

    /** Una firma ajena y los algoritmos RSA alternativo, HMAC y none se rechazan. */
    @Test
    void rechazaClavesAjenasYConfusionDeAlgoritmo() throws Exception {
        var claims = validClaims().build();
        var decoder = CONFIG.jwtDecoder((RSAPublicKey) TestRsaKeys.pair().getPublic(), PROPERTIES, CLOCK);
        var foreign = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        foreign.sign(new RSASSASigner(TestRsaKeys.generar(2048).getPrivate()));
        var rsa512 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS512), claims);
        rsa512.sign(new RSASSASigner(TestRsaKeys.pair().getPrivate()));
        var hmac = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        hmac.sign(new MACSigner(TestRsaKeys.pair().getPublic().getEncoded()));
        for (String token : List.of(foreign.serialize(), rsa512.serialize(), hmac.serialize(), new PlainJWT(claims).serialize())) {
            assertThatThrownBy(() -> decoder.decode(token).block()).isInstanceOf(JwtException.class);
        }
    }

    /** Comprueba ambos lados del límite de tolerancia temporal configurado. */
    @Test
    void aplicaSesentaSegundosDeTolerancia() throws Exception {
        String token = firmar(validClaims().build());
        var publicKey = (RSAPublicKey) TestRsaKeys.pair().getPublic();
        var nearExpiration = Clock.fixed(NOW.plusSeconds(959), ZoneOffset.UTC);
        assertThat(CONFIG.jwtDecoder(publicKey, PROPERTIES, nearExpiration).decode(token).block().getSubject())
                .isEqualTo(USER_ID.toString());
        var outsideExpiration = Clock.fixed(NOW.plusSeconds(961), ZoneOffset.UTC);
        assertThatThrownBy(() -> CONFIG.jwtDecoder(publicKey, PROPERTIES, outsideExpiration).decode(token).block())
                .isInstanceOf(JwtException.class);
    }

    /** Crea claims de referencia sin valores secretos. */
    private static JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder().subject(USER_ID.toString()).issuer("logitrack-security")
                .audience("logitrack-api").claim("roles", List.of("CLIENTE"))
                .issueTime(Date.from(NOW)).notBeforeTime(Date.from(NOW)).expirationTime(Date.from(NOW.plusSeconds(900)));
    }

    /** Firma un fixture con el par temporal compartido. */
    private static String firmar(JWTClaimsSet claims) throws Exception {
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        token.sign(new RSASSASigner(TestRsaKeys.pair().getPrivate()));
        return token.serialize();
    }
}
