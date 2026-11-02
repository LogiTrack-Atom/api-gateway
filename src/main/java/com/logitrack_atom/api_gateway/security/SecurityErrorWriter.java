package com.logitrack_atom.api_gateway.security;

import com.logitrack_atom.api_gateway.dto.ApiError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;

/**
 * Serializa rechazos de seguridad reactivos con el mismo formato de las APIs de dominio.
 *
 * <p>Los mensajes son constantes y no incorporan excepciones ni valores recibidos.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
@Component
public class SecurityErrorWriter {
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * Recibe el serializador y el reloj de la aplicación.
     * @param mapper serializador JSON
     * @param clock reloj UTC
     */
    public SecurityErrorWriter(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Escribe una respuesta 401 con desafío Bearer genérico.
     * @param exchange intercambio HTTP que se rechaza
     * @return finalización no bloqueante de la respuesta
     */
    public Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return write(exchange, HttpStatus.UNAUTHORIZED, "Se requiere una credencial Bearer válida");
    }

    /**
     * Escribe una respuesta 403 sin revelar el recurso o credencial involucrados.
     * @param exchange intercambio HTTP que se rechaza
     * @return finalización no bloqueante de la respuesta
     */
    public Mono<Void> forbidden(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.FORBIDDEN, "No tiene permiso para realizar esta operación");
    }

    /**
     * Prepara el cuerpo público y evita cachear el rechazo de seguridad.
     * @param exchange intercambio actual
     * @param status código que se devuelve
     * @param message descripción segura
     * @return escritura del cuerpo JSON
     */
    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        return Mono.defer(() -> {
            var response = exchange.getResponse();
            response.setStatusCode(status);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getHeaders().setCacheControl("no-store");
            byte[] body = mapper.writeValueAsBytes(new ApiError(clock.instant(), status.value(),
                    status.getReasonPhrase(), message, List.of()));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        });
    }
}
