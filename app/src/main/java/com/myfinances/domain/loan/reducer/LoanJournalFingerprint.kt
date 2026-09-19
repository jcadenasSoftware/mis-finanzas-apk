package com.jcadenas.xpendz.domain.loan.reducer

import com.jcadenas.xpendz.domain.loan.journal.LoanEventPayload
import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import java.security.MessageDigest

object LoanJournalFingerprint {
    fun compute(events: List<LoanMovement>): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalJson(events).toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    internal fun canonicalJson(events: List<LoanMovement>): String = buildString {
        append('[')
        events.sortedWith(canonicalOrder).forEachIndexed { index, event ->
            if (index > 0) append(',')
            appendEvent(this, event)
        }
        append(']')
    }

    private fun appendEvent(out: StringBuilder, event: LoanMovement) {
        out.append('{')
        field(out, "account_id", event.accountId, false)
        field(out, "actor_id", event.actorId, true)
        field(out, "amount_cents", event.amountCents, true)
        field(out, "event_id", event.eventId, true)
        field(out, "event_schema_version", event.eventSchemaVersion, true)
        field(out, "event_type", event.eventType.name, true)
        field(out, "loan_id", event.loanId, true)
        field(out, "note", event.note, true)
        field(out, "occurred_at", event.occurredAt, true)
        field(out, "operation_id", event.operationId, true)
        field(out, "origin_id", event.originId, true)
        field(out, "owner_id", event.ownerId, true)
        appendKey(out, "payload", true)
        appendPayload(out, event.payload)
        field(out, "recorded_at", event.recordedAt, true)
        field(out, "transaction_id", event.transactionId, true)
        out.append('}')
    }

    private fun appendPayload(out: StringBuilder, payload: LoanEventPayload) {
        when (payload) {
            LoanEventPayload.EmptyPayload -> out.append("{}")
            is LoanEventPayload.CreationPayload -> {
                out.append('{')
                field(out, "counterparty_name", payload.counterpartyName, false)
                field(out, "currency", payload.currency, true)
                field(out, "default_account_id", payload.defaultAccountId, true)
                field(out, "loan_type", payload.loanType.name, true)
                field(out, "notes", payload.notes, true)
                out.append('}')
            }
            is LoanEventPayload.PaymentPayload -> {
                out.append('{')
                field(out, "legacy_direction", payload.legacyDirection?.name, false)
                field(out, "legacy_source", payload.legacySource, true)
                out.append('}')
            }
            is LoanEventPayload.AdjustmentPayload -> {
                out.append('{')
                field(out, "legacy_source", payload.legacySource, false)
                field(out, "reason", payload.reason, true)
                out.append('}')
            }
            is LoanEventPayload.MetadataChangedPayload -> {
                out.append('{')
                appendKey(out, "changes", false)
                appendMetadataChanges(out, payload.changes)
                field(out, "legacy_source", payload.legacySource, true)
                out.append('}')
            }
            is LoanEventPayload.ReversalPayload -> {
                out.append('{')
                field(out, "reason", payload.reason, false)
                field(out, "target_event_id", payload.targetEventId, true)
                out.append('}')
            }
            is LoanEventPayload.ClosePayload -> {
                out.append('{')
                field(out, "reason", payload.reason, false)
                out.append('}')
            }
        }
    }

    private fun appendMetadataChanges(
        out: StringBuilder,
        changes: LoanEventPayload.MetadataChanges
    ) {
        out.append('{')
        var comma = false
        if (changes.counterpartyName.present) {
            field(out, "counterparty_name", changes.counterpartyName.value, comma)
            comma = true
        }
        if (changes.defaultAccountId.present) {
            field(out, "default_account_id", changes.defaultAccountId.value, comma)
            comma = true
        }
        if (changes.notes.present) {
            field(out, "notes", changes.notes.value, comma)
        }
        out.append('}')
    }

    private fun field(out: StringBuilder, key: String, value: Any?, comma: Boolean) {
        appendKey(out, key, comma)
        when (value) {
            null -> out.append("null")
            is Number -> appendString(out, value.toLong().toString())
            is String -> appendString(out, value)
            else -> throw IllegalArgumentException("Unsupported canonical JSON value")
        }
    }

    private fun appendKey(out: StringBuilder, key: String, comma: Boolean) {
        if (comma) out.append(',')
        appendString(out, key)
        out.append(':')
    }

    private fun appendString(out: StringBuilder, value: String) {
        out.append('"')
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) {
                    throw IllegalArgumentException("Invalid Unicode surrogate")
                }
                out.append(current)
                out.append(value[++index])
                index++
                continue
            }
            if (Character.isLowSurrogate(current)) {
                throw IllegalArgumentException("Invalid Unicode surrogate")
            }
            when (current) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\t' -> out.append("\\t")
                '\n' -> out.append("\\n")
                '\u000C' -> out.append("\\f")
                '\r' -> out.append("\\r")
                else -> {
                    if (current.code <= 0x1f) {
                        val hex = "0123456789abcdef"
                        out.append("\\u00")
                        out.append(hex[(current.code ushr 4) and 0x0f])
                        out.append(hex[current.code and 0x0f])
                    } else {
                        out.append(current)
                    }
                }
            }
            index++
        }
        out.append('"')
    }

    private val canonicalOrder = compareBy<LoanMovement>(
        { it.occurredAt },
        { it.recordedAt },
        { it.eventId }
    )
}
