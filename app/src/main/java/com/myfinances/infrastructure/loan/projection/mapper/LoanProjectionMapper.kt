package com.jcadenas.xpendz.infrastructure.loan.projection.mapper

import com.jcadenas.xpendz.domain.loan.journal.LoanEventPayload
import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.projection.LoanPaymentDirection
import com.jcadenas.xpendz.domain.loan.projection.LoanPaymentProjection
import com.jcadenas.xpendz.domain.loan.projection.LoanSummaryProjection
import com.jcadenas.xpendz.domain.loan.snapshot.LoanSnapshot
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus
import com.jcadenas.xpendz.infrastructure.loan.projection.model.LoanPaymentProjectionEntity
import com.jcadenas.xpendz.infrastructure.loan.projection.model.LoanSummaryProjectionEntity

class LoanProjectionMapper {
    fun toEntity(projection: LoanPaymentProjection): LoanPaymentProjectionEntity = LoanPaymentProjectionEntity(
        sourceEventId = projection.sourceEventId,
        operationId = projection.operationId,
        ownerId = projection.ownerId,
        loanId = projection.loanId,
        accountId = projection.accountId,
        transactionId = projection.transactionId,
        occurredAt = projection.occurredAt,
        amountCents = projection.amountCents,
        direction = projection.direction?.name,
        note = projection.note
    )

    fun toDomain(entity: LoanPaymentProjectionEntity): LoanPaymentProjection = LoanPaymentProjection(
        sourceEventId = entity.sourceEventId,
        operationId = entity.operationId,
        ownerId = entity.ownerId,
        loanId = entity.loanId,
        accountId = entity.accountId,
        transactionId = entity.transactionId,
        occurredAt = entity.occurredAt,
        amountCents = entity.amountCents,
        direction = entity.direction?.let { LoanPaymentDirection.valueOf(it) },
        note = entity.note
    )

    fun toPaymentProjection(event: LoanMovement): LoanPaymentProjection {
        val payload = event.payload as LoanEventPayload.PaymentPayload
        val direction = payload.legacyDirection?.let { LoanPaymentDirection.valueOf(it.name) }
        return LoanPaymentProjection(
            sourceEventId = event.eventId,
            operationId = event.operationId,
            ownerId = event.ownerId,
            loanId = event.loanId,
            accountId = event.accountId,
            transactionId = event.transactionId,
            occurredAt = event.occurredAt,
            amountCents = event.amountCents!!,
            direction = direction,
            note = event.note
        )
    }

    fun toEntity(projection: LoanSummaryProjection): LoanSummaryProjectionEntity = LoanSummaryProjectionEntity(
        loanId = projection.loanId,
        ownerId = projection.ownerId,
        counterparty = projection.counterparty,
        loanType = projection.loanType.name,
        currency = projection.currency,
        defaultAccountId = projection.defaultAccountId,
        notes = projection.notes,
        principalCents = projection.principalCents,
        totalPaidCents = projection.totalPaidCents,
        pendingCents = projection.pendingCents,
        overpaidCents = projection.overpaidCents,
        paymentCount = projection.paymentCount,
        lastPaymentAt = projection.lastPaymentAt,
        progressPercent = projection.progressPercent,
        status = projection.status.name,
        closedAt = projection.closedAt,
        lastActivity = projection.lastActivity,
        journalFingerprint = projection.journalFingerprint
    )

    fun toDomain(entity: LoanSummaryProjectionEntity): LoanSummaryProjection = LoanSummaryProjection(
        loanId = entity.loanId,
        ownerId = entity.ownerId,
        counterparty = entity.counterparty,
        loanType = LoanType.valueOf(entity.loanType),
        currency = entity.currency,
        defaultAccountId = entity.defaultAccountId,
        notes = entity.notes,
        principalCents = entity.principalCents,
        totalPaidCents = entity.totalPaidCents,
        pendingCents = entity.pendingCents,
        overpaidCents = entity.overpaidCents,
        paymentCount = entity.paymentCount,
        lastPaymentAt = entity.lastPaymentAt,
        progressPercent = entity.progressPercent,
        status = LoanStatus.valueOf(entity.status),
        closedAt = entity.closedAt,
        lastActivity = entity.lastActivity,
        journalFingerprint = entity.journalFingerprint
    )

    fun toSummaryProjection(snapshot: LoanSnapshot, paymentCount: Int, lastPaymentAt: Long?): LoanSummaryProjection {
        val progressPercent = if (snapshot.principalCents == 0L) 0 else (snapshot.totalPaidCents * 100 / snapshot.principalCents).toInt()
        return LoanSummaryProjection(
            loanId = snapshot.loanId,
            ownerId = snapshot.ownerId,
            counterparty = snapshot.counterpartyName,
            loanType = snapshot.loanType,
            currency = snapshot.currency,
            defaultAccountId = snapshot.defaultAccountId,
            notes = snapshot.notes,
            principalCents = snapshot.principalCents,
            totalPaidCents = snapshot.totalPaidCents,
            pendingCents = snapshot.pendingCents,
            overpaidCents = snapshot.overpaidCents,
            paymentCount = paymentCount,
            lastPaymentAt = lastPaymentAt,
            progressPercent = progressPercent,
            status = snapshot.status,
            closedAt = snapshot.closedAt,
            lastActivity = snapshot.lastActivityAt,
            journalFingerprint = snapshot.journalFingerprint
        )
    }
}
