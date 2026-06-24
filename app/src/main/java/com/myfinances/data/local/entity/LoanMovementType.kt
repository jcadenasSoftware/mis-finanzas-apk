package com.jcadenas.xpendz.data.local.entity

enum class LoanMovementType {
    CREATION,     // Creación de préstamo
    TOPUP,        // Adición al principal (acumulación)
    PAYMENT,      // Pago/abono (genérico, para compatibilidad)
    PAYMENT_IN,   // Pago recibido (para LENT)
    PAYMENT_OUT,  // Pago realizado (para BORROWED)
    ADJUSTMENT,   // Corrección manual
    CLOSE;        // Cierre de préstamo

    companion object {
        private val allowedValues = values().map { it.name }.toSet()

        fun normalizeOrNull(raw: String?): String? {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return normalized.takeIf { it.isNotBlank() && it in allowedValues }
        }

        fun requireValid(raw: String): String {
            return normalizeOrNull(raw)
                ?: throw IllegalArgumentException("Invalid loan movement type: $raw")
        }
    }
}
