package com.jcadenas.xpendz.ui.obligations

/**
 * Guardia de ejecución única por operación del módulo Obligaciones.
 *
 * Una misma operación (registrar abono, editar abono, eliminar abono,
 * guardar obligación, cancelar obligación) solo puede estar una vez en
 * vuelo: tryAcquire devuelve false si ya está en curso, por lo que doble
 * tap, recomposición o callbacks duplicados nunca producen una segunda
 * ejecución contra ObligationService.
 */
class ObligationOperationGuard {
    private val inFlight = LinkedHashSet<String>()

    @Synchronized
    fun tryAcquire(operationKey: String): Boolean = inFlight.add(operationKey)

    @Synchronized
    fun release(operationKey: String) {
        inFlight.remove(operationKey)
    }

    @Synchronized
    fun isInFlight(operationKey: String): Boolean = inFlight.contains(operationKey)
}
