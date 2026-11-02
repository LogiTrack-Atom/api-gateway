package com.logitrack_atom.api_gateway.enums;

/**
 * Enumera los roles de negocio reconocidos por LogiTrack.
 *
 * <p>El registro público asigna exclusivamente CLIENTE. Los otros roles
 * requieren un procedimiento administrativo.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
public enum Rol {
    /** Administración de recursos e identidades. */
    ADMIN,
    /** Operaciones sobre los envíos propios del cliente asociado. */
    CLIENTE,
    /** Operaciones sobre los envíos asignados al repartidor asociado. */
    REPARTIDOR
}
