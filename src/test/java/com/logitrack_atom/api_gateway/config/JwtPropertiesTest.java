package com.logitrack_atom.api_gateway.config;

import com.logitrack_atom.api_gateway.support.TestRsaKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.time.Duration;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que el arranque no acepte configuraciones temporales inseguras o incompletas.
 *
 * @author MaxDz
 * @version 0.3.0
 */
class JwtPropertiesTest {
    /** Duraciones nulas, negativas o fuera del contrato se rechazan al enlazar propiedades. */
    @ParameterizedTest
    @MethodSource("invalidos")
    void rechazaTiemposInvalidos(Duration ttl, Duration skew) {
        assertThatThrownBy(() -> new JwtProperties("logitrack-security", "logitrack-api", ttl, skew,
                TestRsaKeys.publicResource())).isInstanceOf(IllegalArgumentException.class);
    }

    /** Proporciona límites inválidos sin necesitar claves reales del entorno. */
    static Stream<Arguments> invalidos() {
        return Stream.of(
                Arguments.of(null, Duration.ZERO), Arguments.of(Duration.ZERO, Duration.ZERO),
                Arguments.of(Duration.ofSeconds(-1), Duration.ZERO), Arguments.of(Duration.ofSeconds(901), Duration.ZERO),
                Arguments.of(Duration.ofMillis(1500), Duration.ZERO), Arguments.of(Duration.ofMinutes(15), null),
                Arguments.of(Duration.ofMinutes(15), Duration.ofSeconds(-1)),
                Arguments.of(Duration.ofMinutes(15), Duration.ofSeconds(61)),
                Arguments.of(Duration.ofMinutes(15), Duration.ofMillis(500)));
    }

    /** El emisor vacío y la falta de una ubicación de clave no habilitan valores por defecto. */
    @Test
    void rechazaContratoIncompleto() {
        assertThatThrownBy(() -> new JwtProperties(" ", "logitrack-api", Duration.ofMinutes(15), Duration.ZERO,
                TestRsaKeys.publicResource())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties("logitrack-security", "logitrack-api", Duration.ofMinutes(15), Duration.ZERO,
                null)).isInstanceOf(IllegalArgumentException.class);
    }
}
