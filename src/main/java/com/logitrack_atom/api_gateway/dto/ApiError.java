package com.logitrack_atom.api_gateway.dto;

import java.time.Instant;
import java.util.List;

/**
 * Conserva el contrato público de errores de los servicios de LogiTrack.
 * @param timestamp instante UTC de la respuesta
 * @param status código HTTP
 * @param error descripción del estado HTTP
 * @param mensaje descripción segura sin credenciales ni detalles internos
 * @param detalles lista vacía para rechazos de autenticación y autorización
 * @author MaxDz
 * @version 0.3.0
 */
public record ApiError(Instant timestamp, int status, String error, String mensaje, List<String> detalles) { }
