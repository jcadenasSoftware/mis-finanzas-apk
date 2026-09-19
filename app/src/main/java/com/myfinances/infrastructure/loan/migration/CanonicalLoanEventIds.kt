package com.jcadenas.xpendz.infrastructure.loan.migration

import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Identidad compartida para eventos canónicos reconstruidos desde transporte
 * (replay, migración legacy, reconciliación de pagos). El mismo material
 * produce el mismo operationId/eventId en Android y Desktop, de modo que un
 * documento `loanPayments/{id}` mantiene identidad estable en todos los
 * dispositivos y puede ser eliminado por reversión desde cualquiera.
 */
object CanonicalLoanEventIds {

    private const val REPLAY_TAG = "historical-loan-replay-v1"

    /**
     * operationId para un evento transportado: el docId de `loanPayments` ya es
     * el eventId canónico del dispositivo origen, así que se adopta directamente
     * cuando es un UUID v4/v7 válido. Para filas legacy con ids no UUID se cae
     * al id determinístico compartido.
     */
    fun forTransportPayment(loanId: String, paymentId: String, occurredAtEpochSec: Long): String =
        paymentId.takeIf(::isShareableUuid)
            ?: deterministic(loanId, "PAYMENT", "pay:$paymentId", occurredAtEpochSec)

    /** operationId determinístico para cualquier evento reconstruido (mov:/tx:/synth:/loan:). */
    fun deterministic(loanId: String, eventType: String, sourceKey: String, occurredAtEpochSec: Long): String {
        val material = "$loanId|$eventType|$sourceKey|$occurredAtEpochSec|$REPLAY_TAG"
        val uuid = UUID.nameUUIDFromBytes(material.toByteArray(StandardCharsets.UTF_8))
        val msb = (uuid.mostSignificantBits and -0xF001L) or 0x0000000000004000L
        val lsb = (uuid.leastSignificantBits and 0x3fffffffffffffffL) or Long.MIN_VALUE
        return UUID(msb, lsb).toString()
    }

    /** true si el valor ya es un UUID v4/v7 en formato canónico. */
    fun isShareableUuid(value: String): Boolean = try {
        val parsed = UUID.fromString(value)
        parsed.toString() == value && (parsed.version() == 4 || parsed.version() == 7)
    } catch (e: Exception) {
        false
    }
}
