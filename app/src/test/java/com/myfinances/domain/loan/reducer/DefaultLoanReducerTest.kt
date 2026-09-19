package com.jcadenas.xpendz.domain.loan.reducer

import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnosticCode
import com.jcadenas.xpendz.domain.loan.journal.LoanEventPayload
import com.jcadenas.xpendz.domain.loan.journal.LoanEventType
import com.jcadenas.xpendz.domain.loan.journal.LoanJournalEntry
import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLoanReducerTest {
    private val reducer = DefaultLoanReducer()

    @Test
    fun ordersJournalCanonicallyRegardlessOfArrivalOrder() {
        val creation = creation("e1", "o1", "loan-1", "owner-1", 100, 100)
        val topup = entry("e2", "o2", "loan-1", "owner-1", "TOPUP", 200, 200, 10, emptyMap())
        val payment = entry("e3", "o3", "loan-1", "owner-1", "PAYMENT", 300, 300, 5, emptyMap())

        val first = reducer.reduce(listOf(payment, creation, topup))
        val second = reducer.reduce(listOf(topup, payment, creation))

        assertEquals(ReductionResultType.VALID, first.type)
        assertEquals(listOf("e1", "e2", "e3"), ids(first.normalizedJournal))
        assertEquals(first, second)
        assertNotNull(first.snapshot)
    }

    @Test
    fun canonicalizesLegacyPaymentAliases() {
        val creation = creation("e1", "o1", "loan-1", "owner-1", 100, 100)
        val paymentIn = entry("e2", "o2", "loan-1", "owner-1", "PAYMENT_IN", 200, 200, 10, emptyMap())
        val paymentOut = entry("e3", "o3", "loan-1", "owner-1", "PAYMENT_OUT", 300, 300, 20, emptyMap())

        val result = reducer.reduce(listOf(paymentOut, creation, paymentIn))

        assertEquals(ReductionResultType.VALID, result.type)
        assertEquals(LoanEventType.PAYMENT, result.normalizedJournal[1].eventType)
        assertEquals(LoanEventType.PAYMENT, result.normalizedJournal[2].eventType)
        assertEquals(
            LoanEventPayload.LegacyPaymentDirection.IN,
            (result.normalizedJournal[1].payload as LoanEventPayload.PaymentPayload).legacyDirection
        )
        assertEquals(
            LoanEventPayload.LegacyPaymentDirection.OUT,
            (result.normalizedJournal[2].payload as LoanEventPayload.PaymentPayload).legacyDirection
        )
    }

    @Test
    fun collapsesEquivalentDuplicateEventIds() {
        val creation = creation("e1", "o1", "loan-1", "owner-1", 100, 100)
        val payment = entry("e2", "o2", "loan-1", "owner-1", "PAYMENT", 200, 200, 10, emptyMap())

        val result = reducer.reduce(listOf(payment, creation, payment))

        assertEquals(ReductionResultType.VALID, result.type)
        assertEquals(listOf("e1", "e2"), ids(result.normalizedJournal))
        assertEquals(listOf("e2"), ids(result.discardedDuplicates))
    }

    @Test
    fun rejectsConflictingEventIds() {
        val creation = creation("e1", "o1", "loan-1", "owner-1", 100, 100)
        val first = entry("e2", "o2", "loan-1", "owner-1", "PAYMENT", 200, 200, 10, emptyMap())
        val second = entry("e2", "o2", "loan-1", "owner-1", "PAYMENT", 200, 200, 20, emptyMap())

        val result = reducer.reduce(listOf(creation, second, first))

        assertEquals(ReductionResultType.INVALID, result.type)
        assertCodes(result, LoanDiagnosticCode.EVENT_ID_CONFLICT)
    }

    @Test
    fun rejectsConflictingOperationIds() {
        val creation = creation("e1", "o1", "loan-1", "owner-1", 100, 100)
        val first = entry("e2", "shared", "loan-1", "owner-1", "PAYMENT", 200, 200, 10, emptyMap())
        val second = entry("e3", "shared", "loan-1", "owner-1", "PAYMENT", 200, 200, 20, emptyMap())

        val result = reducer.reduce(listOf(creation, first, second))

        assertEquals(ReductionResultType.INVALID, result.type)
        assertCodes(result, LoanDiagnosticCode.OPERATION_CONFLICT)
    }

    @Test
    fun resolvesEveryNormativeReversalTarget() {
        listOf("TOPUP", "PAYMENT", "ADJUSTMENT", "CLOSE").forEach { targetType ->
            val creation = creation("e1", "o1", "loan-1", "owner-1", 100, 100)
            val target = target("e2", "o2", targetType)
            val reversal = reversal("e3", "o3", "loan-1", "owner-1", "e2", 300)

            val result = reducer.reduce(listOf(reversal, target, creation))

            assertEquals(targetType, ReductionResultType.VALID, result.type)
            assertEquals(targetType, listOf("e2"), ids(result.revertedEvents))
            assertEquals(targetType, listOf("e1"), ids(result.effectiveEvents))
        }
    }

    @Test
    fun reportsUnresolvedReversalAsIncomplete() {
        val result = reducer.reduce(
            listOf(
                creation("e1", "o1", "loan-1", "owner-1", 100, 100),
                reversal("e2", "o2", "loan-1", "owner-1", "missing", 200)
            )
        )

        assertEquals(ReductionResultType.INCOMPLETE, result.type)
        assertCodes(result, LoanDiagnosticCode.UNRESOLVED_REVERSAL)
    }

    @Test
    fun rejectsNonReversibleTarget() {
        val result = reducer.reduce(
            listOf(
                creation("e1", "o1", "loan-1", "owner-1", 100, 100),
                reversal("e2", "o2", "loan-1", "owner-1", "e1", 200)
            )
        )

        assertEquals(ReductionResultType.INVALID, result.type)
        assertCodes(result, LoanDiagnosticCode.NON_REVERSIBLE_TARGET)
    }

    @Test
    fun rejectsCrossLoanReversal() {
        val result = reducer.reduce(
            listOf(
                creation("e1", "o1", "loan-1", "owner-1", 100, 100),
                entry("e2", "o2", "loan-2", "owner-1", "PAYMENT", 200, 200, 10, emptyMap()),
                reversal("e3", "o3", "loan-1", "owner-1", "e2", 300)
            )
        )

        assertEquals(ReductionResultType.INVALID, result.type)
        assertTrue(codes(result).contains(LoanDiagnosticCode.MIXED_AGGREGATES))
        assertTrue(codes(result).contains(LoanDiagnosticCode.CROSS_LOAN_REVERSAL))
    }

    @Test
    fun rejectsMultipleReversalsOfSameTarget() {
        val result = reducer.reduce(
            listOf(
                creation("e1", "o1", "loan-1", "owner-1", 100, 100),
                entry("e2", "o2", "loan-1", "owner-1", "PAYMENT", 200, 200, 10, emptyMap()),
                reversal("e3", "o3", "loan-1", "owner-1", "e2", 300),
                reversal("e4", "o4", "loan-1", "owner-1", "e2", 400)
            )
        )

        assertEquals(ReductionResultType.INVALID, result.type)
        assertCodes(result, LoanDiagnosticCode.MULTIPLE_REVERSALS)
    }

    @Test
    fun rejectsEventBeforeCreation() {
        val result = reducer.reduce(
            listOf(
                creation("e1", "o1", "loan-1", "owner-1", 200, 200),
                entry("e2", "o2", "loan-1", "owner-1", "PAYMENT", 100, 100, 10, emptyMap())
            )
        )

        assertEquals(ReductionResultType.INVALID, result.type)
        assertCodes(result, LoanDiagnosticCode.EVENT_BEFORE_CREATION)
    }

    @Test
    fun rejectsMultipleCreations() {
        val result = reducer.reduce(
            listOf(
                creation("e1", "o1", "loan-1", "owner-1", 100, 100),
                creation("e2", "o2", "loan-1", "owner-1", 200, 200)
            )
        )

        assertEquals(ReductionResultType.INVALID, result.type)
        assertCodes(result, LoanDiagnosticCode.MULTIPLE_CREATIONS)
    }

    @Test
    fun rejectsMixedAggregates() {
        val result = reducer.reduce(
            listOf(
                creation("e1", "o1", "loan-1", "owner-1", 100, 100),
                entry("e2", "o2", "loan-2", "owner-1", "TOPUP", 200, 200, 10, emptyMap())
            )
        )

        assertEquals(ReductionResultType.INVALID, result.type)
        assertCodes(result, LoanDiagnosticCode.MIXED_AGGREGATES)
    }

    @Test
    fun reportsMissingCreationAsInvalid() {
        val result = reducer.reduce(
            listOf(entry("e1", "o1", "loan-1", "owner-1", "PAYMENT", 100, 100, 10, emptyMap()))
        )

        assertEquals(ReductionResultType.INVALID, result.type)
        assertCodes(result, LoanDiagnosticCode.MISSING_CREATION)
    }

    @Test
    fun reportsUnsupportedTypeAsIncomplete() {
        val result = reducer.reduce(
            listOf(entry("e1", "o1", "loan-1", "owner-1", "UNKNOWN", 100, 100, null, emptyMap()))
        )

        assertEquals(ReductionResultType.INCOMPLETE, result.type)
        assertCodes(result, LoanDiagnosticCode.UNSUPPORTED_EVENT_TYPE)
    }

    @Test
    fun reportsUnsupportedVersionAsIncomplete() {
        val unsupported = LoanJournalEntry(
            eventId = "e1",
            operationId = "o1",
            loanId = "loan-1",
            ownerId = "owner-1",
            rawEventType = "CREATION",
            eventSchemaVersion = 2,
            amountCents = 100,
            accountId = null,
            transactionId = null,
            note = null,
            occurredAt = 100,
            recordedAt = 100,
            actorId = null,
            originId = null,
            rawPayload = creationPayload()
        )

        val result = reducer.reduce(listOf(unsupported))

        assertEquals(ReductionResultType.INCOMPLETE, result.type)
        assertCodes(result, LoanDiagnosticCode.UNSUPPORTED_EVENT_VERSION)
    }

    @Test
    fun rejectsInvalidPayload() {
        val result = reducer.reduce(
            listOf(entry("e1", "o1", "loan-1", "owner-1", "CREATION", 100, 100, 100, emptyMap()))
        )

        assertEquals(ReductionResultType.INVALID, result.type)
        assertCodes(result, LoanDiagnosticCode.INVALID_EVENT_PAYLOAD)
    }

    @Test
    fun collapsesEquivalentOperationIdsWithDifferentEventIds() {
        val creation = creation("e1", "o1", "loan-1", "owner-1", 100, 100)
        val first = entry("e2", "shared", "loan-1", "owner-1", "PAYMENT", 200, 200, 10, emptyMap())
        val second = entry("e3", "shared", "loan-1", "owner-1", "PAYMENT", 200, 200, 10, emptyMap())

        val result = reducer.reduce(listOf(second, creation, first))

        assertEquals(ReductionResultType.VALID, result.type)
        assertEquals(listOf("e1", "e2"), ids(result.normalizedJournal))
        assertEquals(listOf("e3"), ids(result.discardedDuplicates))
    }

    @Test
    fun reportsLegacyDirectionMismatchWithoutInvalidatingJournal() {
        val payment = entry(
            "e2", "o2", "loan-1", "owner-1", "PAYMENT_IN", 200, 200, 10,
            mapOf("legacyDirection" to "OUT")
        )

        val result = reducer.reduce(
            listOf(
                creation("e1", "o1", "loan-1", "owner-1", 100, 100),
                payment
            )
        )

        assertEquals(ReductionResultType.VALID, result.type)
        assertCodes(result, LoanDiagnosticCode.LEGACY_DIRECTION_MISMATCH)
        assertEquals(
            LoanEventPayload.LegacyPaymentDirection.IN,
            (result.normalizedJournal[1].payload as LoanEventPayload.PaymentPayload).legacyDirection
        )
    }

    @Test
    fun canonicalizesMetadataChangesStructurally() {
        val metadata = entry(
            "e2", "o2", "loan-1", "owner-1", "METADATA_CHANGED", 200, 200, null,
            mapOf("changes" to mapOf("counterpartyName" to "Ana Pérez"))
        )

        val result = reducer.reduce(
            listOf(
                creation("e1", "o1", "loan-1", "owner-1", 100, 100),
                metadata
            )
        )

        assertEquals(ReductionResultType.VALID, result.type)
        val payload = result.normalizedJournal[1].payload as LoanEventPayload.MetadataChangedPayload
        assertTrue(payload.changes.counterpartyName.present)
        assertEquals("Ana Pérez", payload.changes.counterpartyName.value)
        assertTrue(!payload.changes.notes.present)
    }

    @Test
    fun rejectsMissingOrUnexpectedAmountByEventShape() {
        val paymentWithoutAmount = entry(
            "e2", "o2", "loan-1", "owner-1", "PAYMENT", 200, 200, null, emptyMap()
        )
        val closeWithAmount = entry(
            "e3", "o3", "loan-1", "owner-1", "CLOSE", 300, 300, 1, emptyMap()
        )

        val first = reducer.reduce(
            listOf(
                creation("e1", "o1", "loan-1", "owner-1", 100, 100),
                paymentWithoutAmount
            )
        )
        val second = reducer.reduce(
            listOf(
                creation("e1", "o1", "loan-1", "owner-1", 100, 100),
                closeWithAmount
            )
        )

        assertEquals(ReductionResultType.INVALID, first.type)
        assertCodes(first, LoanDiagnosticCode.INVALID_EVENT_PAYLOAD)
        assertEquals(ReductionResultType.INVALID, second.type)
        assertCodes(second, LoanDiagnosticCode.INVALID_EVENT_PAYLOAD)
    }

    @Test
    fun rejectsReversalOfReversal() {
        val result = reducer.reduce(
            listOf(
                creation("e1", "o1", "loan-1", "owner-1", 100, 100),
                entry("e2", "o2", "loan-1", "owner-1", "PAYMENT", 200, 200, 10, emptyMap()),
                reversal("e3", "o3", "loan-1", "owner-1", "e2", 300),
                reversal("e4", "o4", "loan-1", "owner-1", "e3", 400)
            )
        )

        assertEquals(ReductionResultType.INVALID, result.type)
        assertTrue(codes(result).contains(LoanDiagnosticCode.NON_REVERSIBLE_TARGET))
    }

    @Test
    fun canonicalEntryPointRejectsMalformedCanonicalEvent() {
        val malformed = LoanMovement(
            eventId = "e1",
            operationId = "o1",
            loanId = "loan-1",
            ownerId = "owner-1",
            eventType = LoanEventType.CREATION,
            eventSchemaVersion = 1,
            amountCents = null,
            accountId = null,
            transactionId = null,
            note = null,
            occurredAt = 100,
            recordedAt = 100,
            actorId = null,
            originId = null,
            payload = LoanEventPayload.CreationPayload(LoanType.LENT, "Ana", "USD", null, null)
        )

        val result = reducer.reduceCanonical(listOf(malformed))

        assertEquals(ReductionResultType.INVALID, result.type)
        assertCodes(result, LoanDiagnosticCode.INVALID_EVENT_PAYLOAD)
        assertNull(result.snapshot)
    }

    private fun creation(
        eventId: String,
        operationId: String,
        loanId: String,
        ownerId: String,
        occurredAt: Long,
        recordedAt: Long
    ): LoanJournalEntry = entry(
        eventId,
        operationId,
        loanId,
        ownerId,
        "CREATION",
        occurredAt,
        recordedAt,
        100,
        creationPayload()
    )

    private fun target(eventId: String, operationId: String, type: String): LoanJournalEntry {
        val payload: Map<String, Any?> = when (type) {
            "ADJUSTMENT" -> mapOf("reason" to "correction")
            else -> emptyMap()
        }
        val amount = if (type == "CLOSE") null else 10L
        return entry(eventId, operationId, "loan-1", "owner-1", type, 200, 200, amount, payload)
    }

    private fun reversal(
        eventId: String,
        operationId: String,
        loanId: String,
        ownerId: String,
        targetEventId: String,
        occurredAt: Long
    ): LoanJournalEntry = entry(
        eventId,
        operationId,
        loanId,
        ownerId,
        "REVERSAL",
        occurredAt,
        occurredAt,
        null,
        mapOf("targetEventId" to targetEventId, "reason" to "mistake")
    )

    private fun entry(
        eventId: String,
        operationId: String,
        loanId: String,
        ownerId: String,
        type: String,
        occurredAt: Long,
        recordedAt: Long,
        amount: Long?,
        payload: Map<String, Any?>
    ): LoanJournalEntry = LoanJournalEntry(
        eventId = eventId,
        operationId = operationId,
        loanId = loanId,
        ownerId = ownerId,
        rawEventType = type,
        eventSchemaVersion = 1,
        amountCents = amount,
        accountId = null,
        transactionId = null,
        note = null,
        occurredAt = occurredAt,
        recordedAt = recordedAt,
        actorId = null,
        originId = null,
        rawPayload = payload
    )

    private fun creationPayload(): Map<String, Any?> = mapOf(
        "loanType" to "LENT",
        "counterpartyName" to "Ana",
        "currency" to "USD"
    )

    private fun ids(events: List<LoanMovement>): List<String> = events.map { it.eventId }

    private fun codes(result: LoanReductionResult): List<LoanDiagnosticCode> =
        result.diagnostics.map { it.code }

    private fun assertCodes(result: LoanReductionResult, vararg expected: LoanDiagnosticCode) {
        assertEquals(expected.toList(), codes(result))
    }
}
