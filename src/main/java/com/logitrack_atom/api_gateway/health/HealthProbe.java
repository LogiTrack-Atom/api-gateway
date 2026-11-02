package com.logitrack_atom.api_gateway.health;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Comprueba la salud local del contenedor con las clases disponibles en la JRE.
 *
 * <p>Limita conexión y lectura, no sigue redirecciones y no imprime respuestas
 * ni configuración. El puerto puede suministrarse mediante SERVER_PORT.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
public final class HealthProbe {
    /** Impide crear instancias de la utilidad. */
    private HealthProbe() { }

    /**
     * Devuelve salida cero únicamente ante salud HTTP 200.
     * @param args argumentos no utilizados
     */
    public static void main(String[] args) {
        try {
            int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8066"));
            if (port < 1 || port > 65535) {
                System.exit(1);
                return;
            }
            System.exit(healthy(URI.create("http://127.0.0.1:" + port + "/actuator/health")) ? 0 : 1);
        } catch (IllegalArgumentException ex) {
            System.exit(1);
        }
    }

    /**
     * Consulta un endpoint sin credenciales y cierra la conexión al terminar.
     * @param endpoint URI local de salud
     * @return true exclusivamente cuando se obtiene HTTP 200
     */
    static boolean healthy(URI endpoint) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) endpoint.toURL().openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.setInstanceFollowRedirects(false);
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException ex) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
