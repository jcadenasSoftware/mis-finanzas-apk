package com.jcadenas.xpendz.ui.loans

/**
 * Guardia de ejecución única para reversión de pagos.
 *
 * Un pago solo puede tener una reversión en vuelo: tryAcquire devuelve false
 * si el mismo paymentEventId ya está siendo revertido, por lo que doble tap,
 * recomposición o callbacks duplicados nunca producen un segundo comando.
 */
class PaymentReversalGuard {
    private val inFlight = LinkedHashSet<String>()

    @Synchronized
    fun tryAcquire(paymentEventId: String): Boolean = inFlight.add(paymentEventId)

    @Synchronized
    fun release(paymentEventId: String) {
        inFlight.remove(paymentEventId)
    }

    @Synchronized
    fun isInFlight(paymentEventId: String): Boolean = inFlight.contains(paymentEventId)
}
