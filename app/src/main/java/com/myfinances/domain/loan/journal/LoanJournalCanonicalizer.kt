package com.jcadenas.xpendz.domain.loan.journal

import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnostic
import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnosticCode

class LoanJournalCanonicalizer {
    fun canonicalize(entry: LoanJournalEntry?): CanonicalizationResult {
        if (entry == null) {
            return invalid(null)
        }
        if (entry.eventSchemaVersion != 1) {
            return diagnostic(entry.eventId, LoanDiagnosticCode.UNSUPPORTED_EVENT_VERSION)
        }

        val mapping = mapEventType(entry.rawEventType)
            ?: return diagnostic(entry.eventId, LoanDiagnosticCode.UNSUPPORTED_EVENT_TYPE)
        if (entry.eventId.isBlank() || entry.operationId.isBlank() || entry.loanId.isBlank() || entry.ownerId.isBlank() || entry.rawPayload == null) {
            return invalid(entry.eventId)
        }

        return try {
            validateAmountShape(entry.amountCents, mapping.eventType)
            val diagnostics = aliasDiagnostics(entry, mapping)
            val payload = payloadFor(entry.rawPayload, mapping)
            CanonicalizationResult(
                event = LoanMovement(
                    eventId = entry.eventId,
                    operationId = entry.operationId,
                    loanId = entry.loanId,
                    ownerId = entry.ownerId,
                    eventType = mapping.eventType,
                    eventSchemaVersion = entry.eventSchemaVersion,
                    amountCents = entry.amountCents,
                    accountId = entry.accountId,
                    transactionId = entry.transactionId,
                    note = entry.note,
                    occurredAt = entry.occurredAt,
                    recordedAt = entry.recordedAt,
                    actorId = entry.actorId,
                    originId = entry.originId,
                    payload = payload
                ),
                diagnostics = diagnostics
            )
        } catch (_: IllegalArgumentException) {
            invalid(entry.eventId)
        }
    }

    private fun validateAmountShape(amountCents: Long?, eventType: LoanEventType) {
        val requiresAmount = when (eventType) {
            LoanEventType.CREATION,
            LoanEventType.TOPUP,
            LoanEventType.PAYMENT,
            LoanEventType.ADJUSTMENT -> true
            LoanEventType.METADATA_CHANGED,
            LoanEventType.REVERSAL,
            LoanEventType.CLOSE -> false
        }
        if (requiresAmount != (amountCents != null)) {
            throw IllegalArgumentException()
        }
    }

    private fun aliasDiagnostics(
        entry: LoanJournalEntry,
        mapping: EventMapping
    ): List<LoanDiagnostic> {
        val aliasDirection = mapping.legacyDirection ?: return emptyList()
        val rawDirection = optionalString(entry.rawPayload.orEmpty(), "legacyDirection") ?: return emptyList()
        val payloadDirection = try {
            LoanEventPayload.LegacyPaymentDirection.valueOf(rawDirection)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException()
        }
        if (payloadDirection == aliasDirection) {
            return emptyList()
        }
        return listOf(
            LoanDiagnostic(
                LoanDiagnosticCode.LEGACY_DIRECTION_MISMATCH,
                entry.eventId,
                null
            )
        )
    }

    private fun payloadFor(raw: Map<String, Any?>, mapping: EventMapping): LoanEventPayload =
        when (mapping.eventType) {
            LoanEventType.CREATION -> creationPayload(raw)
            LoanEventType.TOPUP -> {
                requireKeys(raw, emptySet())
                LoanEventPayload.EmptyPayload
            }
            LoanEventType.PAYMENT -> paymentPayload(raw, mapping.legacyDirection)
            LoanEventType.ADJUSTMENT -> adjustmentPayload(raw)
            LoanEventType.METADATA_CHANGED -> metadataPayload(raw)
            LoanEventType.REVERSAL -> reversalPayload(raw)
            LoanEventType.CLOSE -> closePayload(raw)
        }

    private fun creationPayload(raw: Map<String, Any?>): LoanEventPayload.CreationPayload {
        requireKeys(raw, setOf("loanType", "counterpartyName", "currency", "defaultAccountId", "notes"))
        val loanType = try {
            LoanType.valueOf(requiredString(raw, "loanType"))
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException()
        }
        return LoanEventPayload.CreationPayload(
            loanType = loanType,
            counterpartyName = requiredString(raw, "counterpartyName"),
            currency = requiredString(raw, "currency"),
            defaultAccountId = optionalString(raw, "defaultAccountId"),
            notes = optionalString(raw, "notes")
        )
    }

    private fun paymentPayload(
        raw: Map<String, Any?>,
        aliasDirection: LoanEventPayload.LegacyPaymentDirection?
    ): LoanEventPayload.PaymentPayload {
        requireKeys(raw, setOf("legacyDirection", "legacySource"))
        var direction = aliasDirection
        val rawDirection = optionalString(raw, "legacyDirection")
        if (direction == null && rawDirection != null) {
            direction = try {
                LoanEventPayload.LegacyPaymentDirection.valueOf(rawDirection)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException()
            }
        }
        return LoanEventPayload.PaymentPayload(direction, optionalString(raw, "legacySource"))
    }

    private fun adjustmentPayload(raw: Map<String, Any?>): LoanEventPayload.AdjustmentPayload {
        requireKeys(raw, setOf("reason", "legacySource"))
        return LoanEventPayload.AdjustmentPayload(
            reason = requiredString(raw, "reason"),
            legacySource = optionalString(raw, "legacySource")
        )
    }

    private fun metadataPayload(raw: Map<String, Any?>): LoanEventPayload.MetadataChangedPayload {
        requireKeys(raw, setOf("changes", "legacySource"))
        val untypedChanges = raw["changes"] as? Map<*, *> ?: throw IllegalArgumentException()
        if (untypedChanges.keys.any { it !is String }) {
            throw IllegalArgumentException()
        }
        val changes = untypedChanges.entries.associate { it.key as String to it.value }
        requireKeys(changes, setOf("counterpartyName", "defaultAccountId", "notes"))
        if (changes.isEmpty()) {
            throw IllegalArgumentException()
        }
        return LoanEventPayload.MetadataChangedPayload(
            changes = LoanEventPayload.MetadataChanges(
                counterpartyName = fieldValue(changes, "counterpartyName", nullable = false),
                defaultAccountId = fieldValue(changes, "defaultAccountId", nullable = true),
                notes = fieldValue(changes, "notes", nullable = true)
            ),
            legacySource = optionalString(raw, "legacySource")
        )
    }

    private fun reversalPayload(raw: Map<String, Any?>): LoanEventPayload.ReversalPayload {
        requireKeys(raw, setOf("targetEventId", "reason"))
        return LoanEventPayload.ReversalPayload(
            targetEventId = requiredString(raw, "targetEventId"),
            reason = requiredString(raw, "reason")
        )
    }

    private fun closePayload(raw: Map<String, Any?>): LoanEventPayload.ClosePayload {
        requireKeys(raw, setOf("reason"))
        return LoanEventPayload.ClosePayload(optionalString(raw, "reason"))
    }

    private fun fieldValue(
        values: Map<String, Any?>,
        key: String,
        nullable: Boolean
    ): LoanEventPayload.FieldValue<String> {
        if (!values.containsKey(key)) {
            return LoanEventPayload.FieldValue(present = false, value = null)
        }
        val value = values[key]
        if ((!nullable && value == null) || (value != null && value !is String)) {
            throw IllegalArgumentException()
        }
        return LoanEventPayload.FieldValue(present = true, value = value as String?)
    }

    private fun requiredString(values: Map<String, Any?>, key: String): String {
        val value = optionalString(values, key)
        if (value.isNullOrBlank()) {
            throw IllegalArgumentException()
        }
        return value
    }

    private fun optionalString(values: Map<String, Any?>, key: String): String? {
        val value = values[key] ?: return null
        return value as? String ?: throw IllegalArgumentException()
    }

    private fun requireKeys(values: Map<String, *>, allowed: Set<String>) {
        if (!allowed.containsAll(values.keys)) {
            throw IllegalArgumentException()
        }
    }

    private fun mapEventType(rawEventType: String?): EventMapping? = when (rawEventType) {
        "CREATION" -> EventMapping(LoanEventType.CREATION, null)
        "TOPUP" -> EventMapping(LoanEventType.TOPUP, null)
        "PAYMENT" -> EventMapping(LoanEventType.PAYMENT, null)
        "PAYMENT_IN" -> EventMapping(LoanEventType.PAYMENT, LoanEventPayload.LegacyPaymentDirection.IN)
        "PAYMENT_OUT" -> EventMapping(LoanEventType.PAYMENT, LoanEventPayload.LegacyPaymentDirection.OUT)
        "ADJUSTMENT" -> EventMapping(LoanEventType.ADJUSTMENT, null)
        "METADATA_CHANGED" -> EventMapping(LoanEventType.METADATA_CHANGED, null)
        "REVERSAL" -> EventMapping(LoanEventType.REVERSAL, null)
        "CLOSE" -> EventMapping(LoanEventType.CLOSE, null)
        else -> null
    }

    private fun invalid(eventId: String?): CanonicalizationResult =
        diagnostic(eventId, LoanDiagnosticCode.INVALID_EVENT_PAYLOAD)

    private fun diagnostic(eventId: String?, code: LoanDiagnosticCode): CanonicalizationResult =
        CanonicalizationResult(null, listOf(LoanDiagnostic(code, eventId, null)))

    private data class EventMapping(
        val eventType: LoanEventType,
        val legacyDirection: LoanEventPayload.LegacyPaymentDirection?
    )

    data class CanonicalizationResult(
        val event: LoanMovement?,
        val diagnostics: List<LoanDiagnostic>
    )
}
