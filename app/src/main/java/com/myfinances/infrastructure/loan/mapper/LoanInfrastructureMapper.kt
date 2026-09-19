package com.jcadenas.xpendz.infrastructure.loan.mapper

import com.jcadenas.xpendz.domain.loan.journal.LoanEventPayload
import com.jcadenas.xpendz.domain.loan.journal.LoanEventType
import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.snapshot.LoanSnapshot
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus
import com.jcadenas.xpendz.infrastructure.loan.model.CanonicalLoanEventEntity
import com.jcadenas.xpendz.infrastructure.loan.model.CanonicalLoanSnapshotEntity

class LoanInfrastructureMapper {
    fun toEntity(event: LoanMovement): CanonicalLoanEventEntity {
        var payloadLoanType: String? = null
        var payloadCounterpartyName: String? = null
        var payloadCurrency: String? = null
        var payloadDefaultAccountId: String? = null
        var payloadNotes: String? = null
        var payloadLegacyDirection: String? = null
        var payloadLegacySource: String? = null
        var payloadReason: String? = null
        var payloadTargetEventId: String? = null
        var metadataCounterpartyPresent = false
        var metadataCounterpartyValue: String? = null
        var metadataAccountPresent = false
        var metadataAccountValue: String? = null
        var metadataNotesPresent = false
        var metadataNotesValue: String? = null

        when (val payload = event.payload) {
            LoanEventPayload.EmptyPayload -> Unit
            is LoanEventPayload.CreationPayload -> {
                payloadLoanType = payload.loanType.name
                payloadCounterpartyName = payload.counterpartyName
                payloadCurrency = payload.currency
                payloadDefaultAccountId = payload.defaultAccountId
                payloadNotes = payload.notes
            }
            is LoanEventPayload.PaymentPayload -> {
                payloadLegacyDirection = payload.legacyDirection?.name
                payloadLegacySource = payload.legacySource
            }
            is LoanEventPayload.AdjustmentPayload -> {
                payloadReason = payload.reason
                payloadLegacySource = payload.legacySource
            }
            is LoanEventPayload.MetadataChangedPayload -> {
                payloadLegacySource = payload.legacySource
                metadataCounterpartyPresent = payload.changes.counterpartyName.present
                metadataCounterpartyValue = payload.changes.counterpartyName.value
                metadataAccountPresent = payload.changes.defaultAccountId.present
                metadataAccountValue = payload.changes.defaultAccountId.value
                metadataNotesPresent = payload.changes.notes.present
                metadataNotesValue = payload.changes.notes.value
            }
            is LoanEventPayload.ReversalPayload -> {
                payloadTargetEventId = payload.targetEventId
                payloadReason = payload.reason
            }
            is LoanEventPayload.ClosePayload -> payloadReason = payload.reason
        }

        return CanonicalLoanEventEntity(
            eventId = event.eventId,
            operationId = event.operationId,
            loanId = event.loanId,
            ownerId = event.ownerId,
            eventType = event.eventType.name,
            eventSchemaVersion = event.eventSchemaVersion,
            amountCents = event.amountCents,
            accountId = event.accountId,
            transactionId = event.transactionId,
            note = event.note,
            occurredAt = event.occurredAt,
            recordedAt = event.recordedAt,
            actorId = event.actorId,
            originId = event.originId,
            payloadLoanType = payloadLoanType,
            payloadCounterpartyName = payloadCounterpartyName,
            payloadCurrency = payloadCurrency,
            payloadDefaultAccountId = payloadDefaultAccountId,
            payloadNotes = payloadNotes,
            payloadLegacyDirection = payloadLegacyDirection,
            payloadLegacySource = payloadLegacySource,
            payloadReason = payloadReason,
            payloadTargetEventId = payloadTargetEventId,
            metadataCounterpartyPresent = metadataCounterpartyPresent,
            metadataCounterpartyValue = metadataCounterpartyValue,
            metadataAccountPresent = metadataAccountPresent,
            metadataAccountValue = metadataAccountValue,
            metadataNotesPresent = metadataNotesPresent,
            metadataNotesValue = metadataNotesValue
        )
    }

    fun toDomain(entity: CanonicalLoanEventEntity): LoanMovement {
        val eventType = LoanEventType.valueOf(entity.eventType)
        val payload = when (eventType) {
            LoanEventType.CREATION -> LoanEventPayload.CreationPayload(
                loanType = LoanType.valueOf(entity.payloadLoanType!!),
                counterpartyName = entity.payloadCounterpartyName!!,
                currency = entity.payloadCurrency!!,
                defaultAccountId = entity.payloadDefaultAccountId,
                notes = entity.payloadNotes
            )
            LoanEventType.TOPUP -> LoanEventPayload.EmptyPayload
            LoanEventType.PAYMENT -> LoanEventPayload.PaymentPayload(
                legacyDirection = entity.payloadLegacyDirection?.let {
                    LoanEventPayload.LegacyPaymentDirection.valueOf(it)
                },
                legacySource = entity.payloadLegacySource
            )
            LoanEventType.ADJUSTMENT -> LoanEventPayload.AdjustmentPayload(
                reason = entity.payloadReason!!,
                legacySource = entity.payloadLegacySource
            )
            LoanEventType.METADATA_CHANGED -> LoanEventPayload.MetadataChangedPayload(
                changes = LoanEventPayload.MetadataChanges(
                    counterpartyName = LoanEventPayload.FieldValue(
                        entity.metadataCounterpartyPresent,
                        entity.metadataCounterpartyValue
                    ),
                    defaultAccountId = LoanEventPayload.FieldValue(
                        entity.metadataAccountPresent,
                        entity.metadataAccountValue
                    ),
                    notes = LoanEventPayload.FieldValue(
                        entity.metadataNotesPresent,
                        entity.metadataNotesValue
                    )
                ),
                legacySource = entity.payloadLegacySource
            )
            LoanEventType.REVERSAL -> LoanEventPayload.ReversalPayload(
                targetEventId = entity.payloadTargetEventId!!,
                reason = entity.payloadReason!!
            )
            LoanEventType.CLOSE -> LoanEventPayload.ClosePayload(entity.payloadReason)
        }
        return LoanMovement(
            eventId = entity.eventId,
            operationId = entity.operationId,
            loanId = entity.loanId,
            ownerId = entity.ownerId,
            eventType = eventType,
            eventSchemaVersion = entity.eventSchemaVersion,
            amountCents = entity.amountCents,
            accountId = entity.accountId,
            transactionId = entity.transactionId,
            note = entity.note,
            occurredAt = entity.occurredAt,
            recordedAt = entity.recordedAt,
            actorId = entity.actorId,
            originId = entity.originId,
            payload = payload
        )
    }

    fun toEntity(snapshot: LoanSnapshot): CanonicalLoanSnapshotEntity = CanonicalLoanSnapshotEntity(
        loanId = snapshot.loanId,
        ownerId = snapshot.ownerId,
        loanType = snapshot.loanType.name,
        counterpartyName = snapshot.counterpartyName,
        currency = snapshot.currency,
        defaultAccountId = snapshot.defaultAccountId,
        notes = snapshot.notes,
        principalCents = snapshot.principalCents,
        totalPaidCents = snapshot.totalPaidCents,
        netBalanceCents = snapshot.netBalanceCents,
        pendingCents = snapshot.pendingCents,
        overpaidCents = snapshot.overpaidCents,
        status = snapshot.status.name,
        closedAt = snapshot.closedAt,
        lastActivityAt = snapshot.lastActivityAt,
        journalEventCount = snapshot.journalEventCount,
        journalFingerprint = snapshot.journalFingerprint,
        reducerVersion = snapshot.reducerVersion
    )

    fun toDomain(entity: CanonicalLoanSnapshotEntity): LoanSnapshot = LoanSnapshot(
        loanId = entity.loanId,
        ownerId = entity.ownerId,
        loanType = LoanType.valueOf(entity.loanType),
        counterpartyName = entity.counterpartyName,
        currency = entity.currency,
        defaultAccountId = entity.defaultAccountId,
        notes = entity.notes,
        principalCents = entity.principalCents,
        totalPaidCents = entity.totalPaidCents,
        netBalanceCents = entity.netBalanceCents,
        pendingCents = entity.pendingCents,
        overpaidCents = entity.overpaidCents,
        status = LoanStatus.valueOf(entity.status),
        closedAt = entity.closedAt,
        lastActivityAt = entity.lastActivityAt,
        journalEventCount = entity.journalEventCount,
        journalFingerprint = entity.journalFingerprint,
        reducerVersion = entity.reducerVersion
    )
}
