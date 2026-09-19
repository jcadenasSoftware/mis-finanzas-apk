package com.jcadenas.xpendz.domain.loan.reducer

import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnostic
import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnosticCategory
import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnosticCode
import com.jcadenas.xpendz.domain.loan.journal.LoanEventPayload
import com.jcadenas.xpendz.domain.loan.journal.LoanEventType
import com.jcadenas.xpendz.domain.loan.journal.LoanJournalCanonicalizer
import com.jcadenas.xpendz.domain.loan.journal.LoanJournalEntry
import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import com.jcadenas.xpendz.domain.loan.snapshot.LoanSnapshot
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus

class DefaultLoanReducer(
    private val canonicalizer: LoanJournalCanonicalizer = LoanJournalCanonicalizer()
) : LoanReducer {
    override fun reduce(events: Collection<LoanJournalEntry>): LoanReductionResult {
        val canonicalized = mutableListOf<LoanMovement>()
        val diagnostics = mutableListOf<LoanDiagnostic>()
        events.forEach { entry ->
            val canonical = canonicalizer.canonicalize(entry)
            canonical.event?.let(canonicalized::add)
            diagnostics += canonical.diagnostics
        }

        return reduceCanonicalized(canonicalized, diagnostics)
    }

    override fun reduceCanonical(events: Collection<LoanMovement>): LoanReductionResult {
        val canonicalized = mutableListOf<LoanMovement>()
        val diagnostics = mutableListOf<LoanDiagnostic>()
        events.forEach { event ->
            val diagnostic = validateCanonicalEvent(event)
            if (diagnostic == null) {
                canonicalized += event
            } else {
                diagnostics += diagnostic
            }
        }
        return reduceCanonicalized(canonicalized, diagnostics)
    }

    private fun validateCanonicalEvent(event: LoanMovement): LoanDiagnostic? {
        if (event.eventSchemaVersion != 1) {
            return LoanDiagnostic(LoanDiagnosticCode.UNSUPPORTED_EVENT_VERSION, event.eventId, null)
        }
        if (event.eventId.isBlank() ||
            event.operationId.isBlank() ||
            event.loanId.isBlank() ||
            event.ownerId.isBlank()
        ) {
            return LoanDiagnostic(LoanDiagnosticCode.INVALID_EVENT_PAYLOAD, event.eventId, null)
        }

        val valid = when (event.eventType) {
            LoanEventType.CREATION -> event.amountCents != null &&
                event.payload is LoanEventPayload.CreationPayload &&
                event.payload.counterpartyName.isNotBlank() &&
                event.payload.currency.isNotBlank()
            LoanEventType.TOPUP -> event.amountCents != null &&
                event.payload is LoanEventPayload.EmptyPayload
            LoanEventType.PAYMENT -> event.amountCents != null &&
                event.payload is LoanEventPayload.PaymentPayload
            LoanEventType.ADJUSTMENT -> event.amountCents != null &&
                event.amountCents != 0L &&
                event.payload is LoanEventPayload.AdjustmentPayload &&
                event.payload.reason.isNotBlank()
            LoanEventType.METADATA_CHANGED -> event.amountCents == null &&
                event.payload is LoanEventPayload.MetadataChangedPayload &&
                validMetadataChanges(event.payload.changes)
            LoanEventType.REVERSAL -> event.amountCents == null &&
                event.payload is LoanEventPayload.ReversalPayload &&
                event.payload.targetEventId.isNotBlank() &&
                event.payload.reason.isNotBlank()
            LoanEventType.CLOSE -> event.amountCents == null &&
                event.payload is LoanEventPayload.ClosePayload
        }
        return if (valid) null else LoanDiagnostic(
            LoanDiagnosticCode.INVALID_EVENT_PAYLOAD,
            event.eventId,
            null
        )
    }

    private fun validMetadataChanges(changes: LoanEventPayload.MetadataChanges): Boolean {
        val anyPresent = changes.counterpartyName.present ||
            changes.defaultAccountId.present ||
            changes.notes.present
        return anyPresent &&
            (!changes.counterpartyName.present || !changes.counterpartyName.value.isNullOrBlank())
    }

    private fun reduceCanonicalized(
        canonicalized: List<LoanMovement>,
        diagnostics: MutableList<LoanDiagnostic>
    ): LoanReductionResult {
        if (hasBlockingDiagnostics(diagnostics)) {
            return result(
                type = resultType(diagnostics),
                normalized = canonicalized.sortedWith(canonicalOrder),
                diagnostics = diagnostics
            )
        }

        val byEventId = collapseByEventId(canonicalized)
        diagnostics += byEventId.diagnostics
        if (hasBlockingDiagnostics(diagnostics)) {
            return result(
                type = ReductionResultType.INVALID,
                normalized = byEventId.events,
                diagnostics = diagnostics,
                duplicates = byEventId.duplicates
            )
        }

        val byOperationId = collapseByOperationId(byEventId.events)
        diagnostics += byOperationId.diagnostics
        validateAggregateIdentity(byEventId.events, diagnostics)
        val duplicates = (byEventId.duplicates + byOperationId.duplicates).sortedWith(canonicalOrder)
        if (byOperationId.diagnostics.isNotEmpty()) {
            return result(
                type = ReductionResultType.INVALID,
                normalized = byOperationId.events,
                diagnostics = diagnostics,
                duplicates = duplicates
            )
        }

        val normalized = byOperationId.events.sortedWith(canonicalOrder)
        validateCreation(normalized, diagnostics)
        val reversals = resolveReversals(normalized, diagnostics)
        val structuralType = resultType(diagnostics)
        if (structuralType != ReductionResultType.VALID) {
            return result(
                type = structuralType,
                normalized = normalized,
                diagnostics = diagnostics,
                effective = reversals.effectiveEvents,
                reverted = reversals.revertedEvents,
                duplicates = duplicates
            )
        }
        return foldFinancial(normalized, reversals, duplicates, diagnostics)
    }

    private fun foldFinancial(
        normalized: List<LoanMovement>,
        reversals: ReversalResolution,
        duplicates: List<LoanMovement>,
        structuralDiagnostics: List<LoanDiagnostic>
    ): LoanReductionResult {
        val diagnostics = structuralDiagnostics.toMutableList()
        val effective = reversals.effectiveEvents
        val creation = effective.first { it.eventType == LoanEventType.CREATION }
        val creationPayload = creation.payload as LoanEventPayload.CreationPayload

        var principal = 0L
        var totalPaid = 0L
        var previousPending: Long? = null
        var closedAt: Long? = null
        var counterpartyName = creationPayload.counterpartyName
        val currency = creationPayload.currency
        var defaultAccountId = creationPayload.defaultAccountId
        var notes = creationPayload.notes

        return try {
            effective.forEach { event ->
                when (event.eventType) {
                    LoanEventType.CREATION -> {
                        if (event.amountCents!! <= 0L) {
                            return financialFailure(
                                LoanDiagnosticCode.NON_POSITIVE_PRINCIPAL,
                                event,
                                normalized,
                                diagnostics,
                                reversals,
                                duplicates
                            )
                        }
                        principal = addExact(principal, event.amountCents)
                    }
                    LoanEventType.TOPUP -> {
                        if (event.amountCents!! <= 0L) {
                            return financialFailure(
                                LoanDiagnosticCode.INVALID_EVENT_PAYLOAD,
                                event,
                                normalized,
                                diagnostics,
                                reversals,
                                duplicates
                            )
                        }
                        principal = addExact(principal, event.amountCents)
                    }
                    LoanEventType.PAYMENT -> {
                        if (event.amountCents!! <= 0L) {
                            return financialFailure(
                                LoanDiagnosticCode.INVALID_EVENT_PAYLOAD,
                                event,
                                normalized,
                                diagnostics,
                                reversals,
                                duplicates
                            )
                        }
                        totalPaid = addExact(totalPaid, event.amountCents)
                    }
                    LoanEventType.ADJUSTMENT -> {
                        if (event.amountCents == 0L) {
                            return financialFailure(
                                LoanDiagnosticCode.INVALID_EVENT_PAYLOAD,
                                event,
                                normalized,
                                diagnostics,
                                reversals,
                                duplicates
                            )
                        }
                        principal = addExact(principal, event.amountCents!!)
                        if (principal <= 0L) {
                            return financialFailure(
                                LoanDiagnosticCode.NON_POSITIVE_PRINCIPAL,
                                event,
                                normalized,
                                diagnostics,
                                reversals,
                                duplicates
                            )
                        }
                    }
                    LoanEventType.METADATA_CHANGED -> {
                        val changes = (event.payload as LoanEventPayload.MetadataChangedPayload).changes
                        if (changes.counterpartyName.present) {
                            counterpartyName = changes.counterpartyName.value!!
                        }
                        if (changes.defaultAccountId.present) {
                            defaultAccountId = changes.defaultAccountId.value
                        }
                        if (changes.notes.present) {
                            notes = changes.notes.value
                        }
                    }
                    LoanEventType.CLOSE -> {
                        val pendingAtClose = maxOf(subtractExact(principal, totalPaid), 0L)
                        if (pendingAtClose > 0L) {
                            diagnostics += LoanDiagnostic(
                                LoanDiagnosticCode.PREMATURE_CLOSE,
                                event.eventId,
                                null
                            )
                        }
                    }
                    LoanEventType.REVERSAL -> Unit
                }

                val netBalance = subtractExact(principal, totalPaid)
                val currentPending = maxOf(netBalance, 0L)
                if (previousPending != null && previousPending!! > 0L && currentPending == 0L) {
                    closedAt = event.occurredAt
                }
                if (currentPending > 0L) {
                    closedAt = null
                }
                previousPending = currentPending
            }

            val netBalance = subtractExact(principal, totalPaid)
            val pending = maxOf(netBalance, 0L)
            val overpaid = if (netBalance < 0L) subtractExact(0L, netBalance) else 0L
            if (overpaid > 0L) {
                diagnostics += LoanDiagnostic(LoanDiagnosticCode.OVERPAYMENT, null, null)
            }
            val status = if (pending == 0L) LoanStatus.CLOSED else LoanStatus.OPEN
            val snapshot = LoanSnapshot(
                loanId = creation.loanId,
                ownerId = creation.ownerId,
                loanType = creationPayload.loanType,
                counterpartyName = counterpartyName,
                currency = currency,
                defaultAccountId = defaultAccountId,
                notes = notes,
                principalCents = principal,
                totalPaidCents = totalPaid,
                netBalanceCents = netBalance,
                pendingCents = pending,
                overpaidCents = overpaid,
                status = status,
                closedAt = closedAt,
                lastActivityAt = normalized.maxOf { it.occurredAt },
                journalEventCount = normalized.size,
                journalFingerprint = LoanJournalFingerprint.compute(normalized),
                reducerVersion = 1
            )
            resultWithSnapshot(
                snapshot = snapshot,
                normalized = normalized,
                diagnostics = diagnostics,
                effective = effective,
                reverted = reversals.revertedEvents,
                duplicates = duplicates
            )
        } catch (_: ArithmeticException) {
            financialFailure(
                LoanDiagnosticCode.ARITHMETIC_OVERFLOW,
                null,
                normalized,
                diagnostics,
                reversals,
                duplicates
            )
        } catch (_: IllegalArgumentException) {
            financialFailure(
                LoanDiagnosticCode.INVALID_EVENT_PAYLOAD,
                null,
                normalized,
                diagnostics,
                reversals,
                duplicates
            )
        }
    }

    private fun financialFailure(
        code: LoanDiagnosticCode,
        event: LoanMovement?,
        normalized: List<LoanMovement>,
        diagnostics: List<LoanDiagnostic>,
        reversals: ReversalResolution,
        duplicates: List<LoanMovement>
    ): LoanReductionResult = result(
        type = ReductionResultType.INVALID,
        normalized = normalized,
        diagnostics = diagnostics + LoanDiagnostic(code, event?.eventId, null),
        effective = reversals.effectiveEvents,
        reverted = reversals.revertedEvents,
        duplicates = duplicates
    )

    private fun collapseByEventId(events: List<LoanMovement>): CollapseResult {
        val unique = mutableListOf<LoanMovement>()
        val duplicates = mutableListOf<LoanMovement>()
        val diagnostics = mutableListOf<LoanDiagnostic>()
        events.groupBy { it.eventId }.toSortedMap().forEach { (eventId, group) ->
            val first = group.first()
            if (group.any { it != first }) {
                diagnostics += LoanDiagnostic(LoanDiagnosticCode.EVENT_ID_CONFLICT, eventId, null)
            } else {
                unique += first
                repeat(group.size - 1) { duplicates += first }
            }
        }
        return CollapseResult(
            unique.sortedWith(canonicalOrder),
            duplicates.sortedWith(canonicalOrder),
            diagnostics
        )
    }

    private fun collapseByOperationId(events: List<LoanMovement>): CollapseResult {
        val unique = mutableListOf<LoanMovement>()
        val duplicates = mutableListOf<LoanMovement>()
        val diagnostics = mutableListOf<LoanDiagnostic>()
        events.groupBy { it.operationId }.toSortedMap().forEach { (_, group) ->
            val orderedGroup = group.sortedWith(canonicalOrder)
            val first = orderedGroup.first()
            if (orderedGroup.any { !sameOperationEvent(first, it) }) {
                diagnostics += LoanDiagnostic(LoanDiagnosticCode.OPERATION_CONFLICT, first.eventId, null)
            } else {
                unique += first
                duplicates += orderedGroup.drop(1)
            }
        }
        return CollapseResult(
            unique.sortedWith(canonicalOrder),
            duplicates.sortedWith(canonicalOrder),
            diagnostics
        )
    }

    private fun sameOperationEvent(left: LoanMovement, right: LoanMovement): Boolean =
        left.operationId == right.operationId &&
            left.loanId == right.loanId &&
            left.ownerId == right.ownerId &&
            left.eventType == right.eventType &&
            left.eventSchemaVersion == right.eventSchemaVersion &&
            left.amountCents == right.amountCents &&
            left.accountId == right.accountId &&
            left.transactionId == right.transactionId &&
            left.note == right.note &&
            left.occurredAt == right.occurredAt &&
            left.recordedAt == right.recordedAt &&
            left.actorId == right.actorId &&
            left.originId == right.originId &&
            left.payload == right.payload

    private fun validateAggregateIdentity(
        events: List<LoanMovement>,
        diagnostics: MutableList<LoanDiagnostic>
    ) {
        val first = events.firstOrNull() ?: return
        val mismatch = events.firstOrNull { it.loanId != first.loanId || it.ownerId != first.ownerId }
        if (mismatch != null) {
            diagnostics += LoanDiagnostic(
                LoanDiagnosticCode.MIXED_AGGREGATES,
                first.eventId,
                mismatch.eventId
            )
        }
    }

    private fun validateCreation(
        events: List<LoanMovement>,
        diagnostics: MutableList<LoanDiagnostic>
    ) {
        val creations = events.filter { it.eventType == LoanEventType.CREATION }
        when {
            creations.isEmpty() -> diagnostics += LoanDiagnostic(LoanDiagnosticCode.MISSING_CREATION, null, null)
            creations.size > 1 -> diagnostics += LoanDiagnostic(
                LoanDiagnosticCode.MULTIPLE_CREATIONS,
                creations[0].eventId,
                creations[1].eventId
            )
            else -> {
                val creation = creations.single()
                val preceding = events.firstOrNull {
                    it.eventType != LoanEventType.CREATION && canonicalOrder.compare(it, creation) < 0
                }
                if (preceding != null) {
                    diagnostics += LoanDiagnostic(
                        LoanDiagnosticCode.EVENT_BEFORE_CREATION,
                        preceding.eventId,
                        creation.eventId
                    )
                }
            }
        }
    }

    private fun resolveReversals(
        normalized: List<LoanMovement>,
        diagnostics: MutableList<LoanDiagnostic>
    ): ReversalResolution {
        val eventsById = normalized.associateBy { it.eventId }
        val reversalsByTarget = sortedMapOf<String, MutableList<LoanMovement>>()

        normalized.filter { it.eventType == LoanEventType.REVERSAL }.forEach { reversal ->
            val payload = reversal.payload as LoanEventPayload.ReversalPayload
            val target = eventsById[payload.targetEventId]
            when {
                target == null -> diagnostics += LoanDiagnostic(
                    LoanDiagnosticCode.UNRESOLVED_REVERSAL,
                    reversal.eventId,
                    payload.targetEventId
                )
                target.loanId != reversal.loanId || target.ownerId != reversal.ownerId ->
                    diagnostics += LoanDiagnostic(
                        LoanDiagnosticCode.CROSS_LOAN_REVERSAL,
                        reversal.eventId,
                        target.eventId
                    )
                target.eventType !in reversibleTypes -> diagnostics += LoanDiagnostic(
                    LoanDiagnosticCode.NON_REVERSIBLE_TARGET,
                    reversal.eventId,
                    target.eventId
                )
                else -> reversalsByTarget.getOrPut(target.eventId) { mutableListOf() }.add(reversal)
            }
        }

        val reversedIds = mutableSetOf<String>()
        reversalsByTarget.forEach { (targetEventId, reversals) ->
            val orderedReversals = reversals.sortedWith(canonicalOrder)
            if (orderedReversals.size > 1) {
                diagnostics += LoanDiagnostic(
                    LoanDiagnosticCode.MULTIPLE_REVERSALS,
                    orderedReversals[0].eventId,
                    orderedReversals[1].eventId
                )
            } else {
                reversedIds += targetEventId
            }
        }

        return ReversalResolution(
            effectiveEvents = normalized
                .filter { it.eventType != LoanEventType.REVERSAL && it.eventId !in reversedIds }
                .sortedWith(canonicalOrder),
            revertedEvents = normalized
                .filter { it.eventId in reversedIds }
                .sortedWith(canonicalOrder)
        )
    }

    private fun hasBlockingDiagnostics(diagnostics: List<LoanDiagnostic>): Boolean =
        diagnostics.any {
            it.category == LoanDiagnosticCategory.INVALIDATING_ERROR ||
                it.category == LoanDiagnosticCategory.INCOMPLETE_STATE
        }

    private fun resultType(diagnostics: List<LoanDiagnostic>): ReductionResultType = when {
        diagnostics.any { it.category == LoanDiagnosticCategory.INVALIDATING_ERROR } -> ReductionResultType.INVALID
        diagnostics.any { it.category == LoanDiagnosticCategory.INCOMPLETE_STATE } -> ReductionResultType.INCOMPLETE
        else -> ReductionResultType.VALID
    }

    private fun result(
        type: ReductionResultType,
        normalized: List<LoanMovement>,
        diagnostics: List<LoanDiagnostic>,
        effective: List<LoanMovement> = emptyList(),
        reverted: List<LoanMovement> = emptyList(),
        duplicates: List<LoanMovement> = emptyList()
    ): LoanReductionResult = LoanReductionResult(
        type = type,
        snapshot = null,
        normalizedJournal = normalized.toList(),
        diagnostics = diagnostics.sortedWith(diagnosticOrder),
        effectiveEvents = effective.toList(),
        revertedEvents = reverted.toList(),
        discardedDuplicates = duplicates.toList()
    )

    private fun resultWithSnapshot(
        snapshot: LoanSnapshot,
        normalized: List<LoanMovement>,
        diagnostics: List<LoanDiagnostic>,
        effective: List<LoanMovement>,
        reverted: List<LoanMovement>,
        duplicates: List<LoanMovement>
    ): LoanReductionResult = LoanReductionResult(
        type = ReductionResultType.VALID,
        snapshot = snapshot,
        normalizedJournal = normalized.toList(),
        diagnostics = diagnostics.sortedWith(diagnosticOrder),
        effectiveEvents = effective.toList(),
        revertedEvents = reverted.toList(),
        discardedDuplicates = duplicates.toList()
    )

    private fun addExact(left: Long, right: Long): Long {
        val result = left + right
        if (((left xor result) and (right xor result)) < 0L) {
            throw ArithmeticException()
        }
        return result
    }

    private fun subtractExact(left: Long, right: Long): Long {
        val result = left - right
        if (((left xor right) and (left xor result)) < 0L) {
            throw ArithmeticException()
        }
        return result
    }

    private data class CollapseResult(
        val events: List<LoanMovement>,
        val duplicates: List<LoanMovement>,
        val diagnostics: List<LoanDiagnostic>
    )

    private data class ReversalResolution(
        val effectiveEvents: List<LoanMovement>,
        val revertedEvents: List<LoanMovement>
    )

    companion object {
        private val canonicalOrder = compareBy<LoanMovement>(
            { it.occurredAt },
            { it.recordedAt },
            { it.eventId }
        )

        private val diagnosticOrder = Comparator<LoanDiagnostic> { left, right ->
            compareValuesBy(
                left,
                right,
                { it.category.ordinal },
                { it.code.name },
                { it.primaryEventId },
                { it.relatedEventId }
            )
        }

        private val reversibleTypes = setOf(
            LoanEventType.TOPUP,
            LoanEventType.PAYMENT,
            LoanEventType.ADJUSTMENT,
            LoanEventType.CLOSE
        )
    }
}
