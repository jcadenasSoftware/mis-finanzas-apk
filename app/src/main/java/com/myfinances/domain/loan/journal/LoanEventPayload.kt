package com.jcadenas.xpendz.domain.loan.journal

sealed interface LoanEventPayload {
    data object EmptyPayload : LoanEventPayload

    data class CreationPayload(
        val loanType: LoanType,
        val counterpartyName: String,
        val currency: String,
        val defaultAccountId: String?,
        val notes: String?
    ) : LoanEventPayload

    data class PaymentPayload(
        val legacyDirection: LegacyPaymentDirection?,
        val legacySource: String?
    ) : LoanEventPayload

    data class AdjustmentPayload(
        val reason: String,
        val legacySource: String?
    ) : LoanEventPayload

    data class MetadataChangedPayload(
        val changes: MetadataChanges,
        val legacySource: String?
    ) : LoanEventPayload

    data class ReversalPayload(
        val targetEventId: String,
        val reason: String
    ) : LoanEventPayload

    data class ClosePayload(val reason: String?) : LoanEventPayload

    data class MetadataChanges(
        val counterpartyName: FieldValue<String>,
        val defaultAccountId: FieldValue<String>,
        val notes: FieldValue<String>
    )

    data class FieldValue<T>(val present: Boolean, val value: T?)

    enum class LegacyPaymentDirection {
        IN,
        OUT
    }
}
