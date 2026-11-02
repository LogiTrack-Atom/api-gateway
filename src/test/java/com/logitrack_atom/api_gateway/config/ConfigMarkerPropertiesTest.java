package com.logitrack_atom.api_gateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba que una configuración incompleta no se acepta como válida.
 * @author MaxDz
 * @version 0.3.0
 */
class ConfigMarkerPropertiesTest {
    /** Los valores ausentes o vacíos impiden construir el bean de configuración. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingMarker(String marker) {
        assertThatThrownBy(() -> new ConfigMarkerProperties(marker))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** El marcador remoto conserva el ambiente que identifica. */
    @Test
    void preservesMarker() {
        assertThat(new ConfigMarkerProperties("api-gateway-prod").marker()).isEqualTo("api-gateway-prod");
    }
}
