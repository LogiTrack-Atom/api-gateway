package com.logitrack_atom.api_gateway.support;

import org.springframework.core.io.FileSystemResource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Set;

/**
 * Prepara claves efímeras de prueba sin material privado versionado ni fallback de producción.
 *
 * @author MaxDz
 * @version 0.3.0
 */
public final class TestRsaKeys {
    private static final KeyPair PAIR = generar(2048);
    private static final Path DIRECTORY;
    static {
        try {
            DIRECTORY = Files.createTempDirectory("logitrack-rsa-tests-");
            escribir(DIRECTORY.resolve("public.pem"), "PUBLIC KEY", PAIR.getPublic().getEncoded());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.deleteIfExists(DIRECTORY.resolve("public.pem"));
                    Files.deleteIfExists(DIRECTORY);
                } catch (java.io.IOException ignored) { }
            }, "limpieza-claves-prueba"));
        } catch (java.io.IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    /** Impide instanciar la utilidad de fixtures. */
    private TestRsaKeys() { }

    /**
     * Obtiene el par generado para el proceso de pruebas.
     * @return par efímero compartido únicamente dentro del proceso de pruebas
     */
    public static KeyPair pair() { return PAIR; }

    /**
     * Obtiene la ubicación temporal del fixture público.
     * @return recurso público de prueba fuera del repositorio
     */
    public static FileSystemResource publicResource() { return new FileSystemResource(DIRECTORY.resolve("public.pem")); }

    /**
     * Genera un par local para probar firmas ajenas o tamaños no admitidos.
     * @param bits tamaño solicitado por la prueba
     * @return par RSA sin persistir
     */
    public static KeyPair generar(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo generar el fixture RSA.", ex);
        }
    }

    /**
     * Escribe un fixture PEM con permisos de propietario cuando el sistema lo admite.
     * @param path archivo temporal
     * @param type tipo de encabezado PEM
     * @param encoded bytes generados en memoria
     * @throws java.io.IOException si no se puede escribir el fixture
     */
    public static void escribir(Path path, String type, byte[] encoded) throws java.io.IOException {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n", StandardCharsets.US_ASCII);
        try {
            Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) { }
    }
    /**
     * Firma un JWT de pruebas con identidad y roles expresos.
     * @param id UUID de la cuenta sintética
     * @param roles roles que se desean comprobar
     * @return token efímero para el proceso de pruebas
     */
    public static String token(java.util.UUID id, String... roles) {
        java.time.Instant now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return firmar(new com.nimbusds.jwt.JWTClaimsSet.Builder().subject(id.toString())
                .issuer("logitrack-security").audience("logitrack-api").claim("roles", java.util.List.of(roles))
                .issueTime(java.util.Date.from(now)).expirationTime(java.util.Date.from(now.plusSeconds(900))).build());
    }

    /**
     * Firma claims sintéticos sin persistir la clave privada.
     * @param claims contenido del fixture
     * @return serialización JWT RS256
     */
    public static String firmar(com.nimbusds.jwt.JWTClaimsSet claims) {
        try {
            var token = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256), claims);
            token.sign(new com.nimbusds.jose.crypto.RSASSASigner(PAIR.getPrivate()));
            return token.serialize();
        } catch (com.nimbusds.jose.JOSEException ex) {
            throw new IllegalStateException("No se pudo firmar el fixture JWT.", ex);
        }
    }
}
