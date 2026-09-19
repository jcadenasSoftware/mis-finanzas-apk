package com.jcadenas.xpendz.domain.loan.projection

import com.jcadenas.xpendz.domain.loan.aggregate.LoanCommandResult
import com.jcadenas.xpendz.domain.loan.journal.LoanEventPayload
import com.jcadenas.xpendz.domain.loan.journal.LoanEventType
import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import com.jcadenas.xpendz.domain.loan.reducer.LoanReducer
import com.jcadenas.xpendz.domain.loan.reducer.ReductionResultType
import com.jcadenas.xpendz.domain.loan.repository.LoanRepository
import com.jcadenas.xpendz.domain.loan.snapshot.LoanSnapshot

class FakeLoanProjector : LoanProjector {
    private val payments = mutableMapOf<String, LoanPaymentProjection>()
    private val summaries = mutableMapOf<String, LoanSummaryProjection>()

    override fun project(result: LoanCommandResult) {
        for (change in result.projectionChanges) {
            when (change.type) {
                LoanProjectionChangeType.NONE -> Unit
                LoanProjectionChangeType.ADD_PAYMENT_PROJECTION -> {
                    val payment = toPaymentProjection(result.event)
                    payments[payment.sourceEventId] = payment
                }
                LoanProjectionChangeType.REMOVE_PAYMENT_PROJECTION -> change.sourceEventId?.let { payments.remove(it) }
                LoanProjectionChangeType.REBUILD_LOAN_SNAPSHOT -> {
                    val summary = toSummaryProjection(result.currentSnapshot, paymentSummary(result.currentSnapshot.ownerId, result.currentSnapshot.loanId))
                    summaries[key(summary.ownerId, summary.loanId)] = summary
                }
            }
        }
    }

    override fun rebuild(ownerId: String, loanId: String, repository: LoanRepository, reducer: LoanReducer) {
        val journal = repository.getJournal(ownerId, loanId)
        val reduction = reducer.reduceCanonical(journal)
        if (reduction.type != ReductionResultType.VALID || reduction.snapshot == null) {
            return
        }
        removeAll(ownerId, loanId)
        for (event in reduction.effectiveEvents) {
            if (event.eventType == LoanEventType.PAYMENT) {
                val payment = toPaymentProjection(event)
                payments[payment.sourceEventId] = payment
            }
        }
        val summary = toSummaryProjection(reduction.snapshot!!, paymentSummary(ownerId, loanId))
        summaries[key(ownerId, loanId)] = summary
    }

    fun getPaymentProjections(ownerId: String, loanId: String): List<LoanPaymentProjection> =
        payments.values.filter { it.ownerId == ownerId && it.loanId == loanId }.sortedBy { it.occurredAt }

    fun getSummaryProjection(ownerId: String, loanId: String): LoanSummaryProjection? =
        summaries[key(ownerId, loanId)]

    fun listSummaries(ownerId: String, filter: LoanSummaryFilter): List<LoanSummaryProjection> {
        var result = summaries.values
            .filter { it.ownerId == ownerId }
            .filter { filter.loanType == null || it.loanType == filter.loanType }
            .filter { filter.status == null || it.status == filter.status }

        result = when (filter.sortBy) {
            SortBy.COUNTERPARTY -> if (filter.ascending) result.sortedBy { it.counterparty } else result.sortedByDescending { it.counterparty }
            SortBy.PRINCIPAL_CENTS -> if (filter.ascending) result.sortedBy { it.principalCents } else result.sortedByDescending { it.principalCents }
            SortBy.PENDING_CENTS -> if (filter.ascending) result.sortedBy { it.pendingCents } else result.sortedByDescending { it.pendingCents }
            SortBy.LAST_ACTIVITY -> if (filter.ascending) result.sortedBy { it.lastActivity } else result.sortedByDescending { it.lastActivity }
        }

        return if (filter.limit != null && filter.limit > 0) result.take(filter.limit) else result
    }

    private fun removeAll(ownerId: String, loanId: String) {
        payments.values.removeAll { it.ownerId == ownerId && it.loanId == loanId }
        summaries.remove(key(ownerId, loanId))
    }

    private fun key(ownerId: String, loanId: String) = "$ownerId\u0000$loanId"

    private fun toPaymentProjection(event: LoanMovement): LoanPaymentProjection {
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

    private fun paymentSummary(ownerId: String, loanId: String): Pair<Int, Long?> {
        val relevant = payments.values.filter { it.ownerId == ownerId && it.loanId == loanId }
        val count = relevant.size
        val lastPaymentAt = relevant.maxOfOrNull { it.occurredAt }
        return count to lastPaymentAt
    }

    private fun toSummaryProjection(snapshot: LoanSnapshot, paymentSummary: Pair<Int, Long?>): LoanSummaryProjection {
        val (paymentCount, lastPaymentAt) = paymentSummary
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
