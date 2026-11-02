package com.logitrack_atom.api_gateway;

import com.logitrack_atom.api_gateway.config.ConfigMarkerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Inicia la aplicación reactiva de entrada al sistema LogiTrack.
 *
 * <p>La configuración de rutas pertenece al repositorio central. El registro en
 * Eureka se habilita por la dependencia cliente y sus propiedades externas.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
@SpringBootApplication
@EnableConfigurationProperties(ConfigMarkerProperties.class)
public class ApiGatewayApplication {
    /** Crea el punto de configuración de la aplicación. */
    public ApiGatewayApplication() { }

    /**
     * Inicia el contexto y el servidor HTTP reactivo.
     * @param args argumentos de configuración recibidos del entorno de ejecución
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
