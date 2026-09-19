package com.jcadenas.xpendz.domain.loan.reducer

import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnosticCode
import com.jcadenas.xpendz.domain.loan.journal.LoanJournalEntry
import com.jcadenas.xpendz.domain.loan.snapshot.LoanSnapshot
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanFinancialReducerTest {
    private val reducer = DefaultLoanReducer()

    @Test
    fun v1BuildsSnapshotFromCreation() {
        val result = reducer.reduce(listOf(creation("e1", 1_000, 100_000, "Ana", "A1", "Inicial")))

        assertEquals(ReductionResultType.VALID, result.type)
        val snapshot = requireSnapshot(result)
        assertEquals("loan-1", snapshot.loanId)
        assertEquals("owner-1", snapshot.ownerId)
        assertEquals("Ana", snapshot.counterpartyName)
        assertEquals("USD", snapshot.currency)
        assertEquals("A1", snapshot.defaultAccountId)
        assertEquals("Inicial", snapshot.notes)
        assertFinancial(snapshot, 100_000, 0, 100_000, 100_000, 0, LoanStatus.OPEN, null)
        assertEquals(1_000, snapshot.lastActivityAt)
        assertEquals(1, snapshot.journalEventCount)
        assertEquals(1, snapshot.reducerVersion)
        assertEquals(EXPECTED_CREATION_FINGERPRINT, snapshot.journalFingerprint)
    }

    @Test
    fun v2FoldsTopupAndPartialPayment() {
        val snapshot = requireSnapshot(
            reducer.reduce(
                listOf(
                    payment("e3", 3_000, 30_000),
                    creation("e1", 1_000, 100_000, "Ana", "A1", null),
                    topup("e2", 2_000, 25_000)
                )
            )
        )

        assertFinancial(snapshot, 125_000, 30_000, 95_000, 95_000, 0, LoanStatus.OPEN, null)
    }

    @Test
    fun v3ClosesAtExactPayment() {
        val snapshot = requireSnapshot(
            reducer.reduce(
                listOf(
                    creation("e1", 1_000, 100_000, "Ana", null, null),
                    payment("e2", 2_000, 40_000),
                    payment("e3", 3_000, 60_000)
                )
            )
        )

        assertFinancial(snapshot, 100_000, 100_000, 0, 0, 0, LoanStatus.CLOSED, 3_000)
    }

    @Test
    fun v4ExcludesReversedPayment() {
        val result = reducer.reduce(
            listOf(
                creation("e1", 1_000, 100_000, "Ana", null, null),
                payment("e2", 2_000, 40_000),
                reversal("e3", 3_000, "e2")
            )
        )

        val snapshot = requireSnapshot(result)
        assertFinancial(snapshot, 100_000, 0, 100_000, 100_000, 0, LoanStatus.OPEN, null)
        assertEquals(3_000, snapshot.lastActivityAt)
        assertEquals(3, snapshot.journalEventCount)
        assertEquals(listOf("e2"), result.revertedEvents.map { it.eventId })
    }

    @Test
    fun v7ProducesSameSnapshotForEveryArrivalOrder() {
        val events = listOf(
            creation("e1", 1_000, 100_000, "Ana", null, null),
            topup("e2", 2_000, 10_000),
            payment("e3", 3_000, 20_000)
        )
        val expected = requireSnapshot(reducer.reduce(events))

        assertEquals(expected, requireSnapshot(reducer.reduce(listOf(events[2], events[0], events[1]))))
        assertEquals(expected, requireSnapshot(reducer.reduce(listOf(events[1], events[2], events[0]))))
        assertFinancial(expected, 110_000, 20_000, 90_000, 90_000, 0, LoanStatus.OPEN, null)
    }

    @Test
    fun v8AppliesSignedAdjustment() {
        val snapshot = requireSnapshot(
            reducer.reduce(
                listOf(
                    creation("e1", 1_000, 100_000, "Ana", null, null),
                    topup("e2", 2_000, 20_000),
                    adjustment("e3", 3_000, -15_000),
                    payment("e4", 4_000, 25_000)
                )
            )
        )

        assertFinancial(snapshot, 105_000, 25_000, 80_000, 80_000, 0, LoanStatus.OPEN, null)
    }

    @Test
    fun v9RepresentsOverpaymentWithoutTruncatingPaid() {
        val result = reducer.reduce(
            listOf(
                creation("e1", 1_000, 100_000, "Ana", null, null),
                payment("e2", 2_000, 60_000),
                payment("e3", 3_000, 50_000)
            )
        )

        val snapshot = requireSnapshot(result)
        assertFinancial(snapshot, 100_000, 110_000, -10_000, 0, 10_000, LoanStatus.CLOSED, 3_000)
        assertTrue(codes(result).contains(LoanDiagnosticCode.OVERPAYMENT))
    }

    @Test
    fun v10AppliesMetadataCumulatively() {
        val snapshot = requireSnapshot(
            reducer.reduce(
                listOf(
                    creation("e1", 1_000, 100_000, "Ana", "A1", null),
                    metadata("e2", 2_000, value("Ana Pérez"), absent(), absent()),
                    metadata("e3", 3_000, absent(), value("A2"), value("Acuerdo actualizado"))
                )
            )
        )

        assertEquals("Ana Pérez", snapshot.counterpartyName)
        assertEquals("A2", snapshot.defaultAccountId)
        assertEquals("Acuerdo actualizado", snapshot.notes)
        assertFinancial(snapshot, 100_000, 0, 100_000, 100_000, 0, LoanStatus.OPEN, null)
    }

    @Test
    fun v11CombinesMetadataChangesOnDifferentFields() {
        val snapshot = requireSnapshot(
            reducer.reduce(
                listOf(
                    metadata("e3", 3_000, absent(), value("A2"), absent()),
                    creation("e1", 1_000, 100_000, "Ana", "A1", null),
                    metadata("e2", 2_000, value("Ana Pérez"), absent(), absent())
                )
            )
        )

        assertEquals("Ana Pérez", snapshot.counterpartyName)
        assertEquals("A2", snapshot.defaultAccountId)
        assertNull(snapshot.notes)
    }

    @Test
    fun v13FoldsEveryLegacyPaymentAlias() {
        val snapshot = requireSnapshot(
            reducer.reduce(
                listOf(
                    creation("e1", 1_000, 500_000, "Ana", null, null),
                    paymentAlias("e2", 2_000, 212_600, "PAYMENT_IN"),
                    paymentAlias("e3", 3_000, 66_000, "PAYMENT_IN"),
                    paymentAlias("e4", 4_000, 50_000, "PAYMENT_IN"),
                    paymentAlias("e5", 5_000, 15_000, "PAYMENT_IN"),
                    paymentAlias("e6", 6_000, 10_000, "PAYMENT_IN")
                )
            )
        )

        assertFinancial(snapshot, 500_000, 353_600, 146_400, 146_400, 0, LoanStatus.OPEN, null)
        assertEquals(6, snapshot.journalEventCount)
    }

    @Test
    fun v15ReportsPrematureCloseWithoutChangingState() {
        val result = reducer.reduce(
            listOf(
                creation("e1", 1_000, 100_000, "Ana", null, null),
                payment("e2", 2_000, 20_000),
                close("e3", 3_000)
            )
        )

        val snapshot = requireSnapshot(result)
        assertFinancial(snapshot, 100_000, 20_000, 80_000, 80_000, 0, LoanStatus.OPEN, null)
        assertTrue(codes(result).contains(LoanDiagnosticCode.PREMATURE_CLOSE))
    }

    @Test
    fun v16TopupReopensClosedLoan() {
        val snapshot = requireSnapshot(
            reducer.reduce(
                listOf(
                    creation("e1", 1_000, 100_000, "Ana", null, null),
                    payment("e2", 2_000, 100_000),
                    topup("e3", 3_000, 20_000)
                )
            )
        )

        assertFinancial(snapshot, 120_000, 100_000, 20_000, 20_000, 0, LoanStatus.OPEN, null)
    }

    @Test
    fun positiveAdjustmentReopensClosedLoan() {
        val snapshot = requireSnapshot(
            reducer.reduce(
                listOf(
                    creation("e1", 1_000, 100_000, "Ana", null, null),
                    payment("e2", 2_000, 100_000),
                    adjustment("e3", 3_000, 20_000)
                )
            )
        )

        assertFinancial(snapshot, 120_000, 100_000, 20_000, 20_000, 0, LoanStatus.OPEN, null)
    }

    @Test
    fun reversalRemovesClosedAtFromRevertedClosingPayment() {
        val snapshot = requireSnapshot(
            reducer.reduce(
                listOf(
                    creation("e1", 1_000, 100_000, "Ana", null, null),
                    payment("e2", 2_000, 100_000),
                    reversal("e3", 3_000, "e2")
                )
            )
        )

        assertEquals(LoanStatus.OPEN, snapshot.status)
        assertNull(snapshot.closedAt)
        assertEquals(100_000, snapshot.pendingCents)
    }

    @Test
    fun detectsCheckedArithmeticOverflow() {
        val result = reducer.reduce(
            listOf(
                creation("e1", 1_000, Long.MAX_VALUE, "Ana", null, null),
                topup("e2", 2_000, 1)
            )
        )

        assertEquals(ReductionResultType.INVALID, result.type)
        assertNull(result.snapshot)
        assertTrue(codes(result).contains(LoanDiagnosticCode.ARITHMETIC_OVERFLOW))
    }

    @Test
    fun rejectsAdjustmentThatMakesPrincipalNonPositive() {
        val result = reducer.reduce(
            listOf(
                creation("e1", 1_000, 100_000, "Ana", null, null),
                adjustment("e2", 2_000, -100_000)
            )
        )

        assertEquals(ReductionResultType.INVALID, result.type)
        assertNull(result.snapshot)
        assertTrue(codes(result).contains(LoanDiagnosticCode.NON_POSITIVE_PRINCIPAL))
    }

    @Test
    fun rejectsZeroAdjustmentEvent() {
        val result = reducer.reduce(
            listOf(
                creation("e1", 1_000, 100_000, "Ana", null, null),
                adjustment("e2", 2_000, 0)
            )
        )

        assertEquals(ReductionResultType.INVALID, result.type)
        assertTrue(codes(result).contains(LoanDiagnosticCode.INVALID_EVENT_PAYLOAD))
        assertNull(result.snapshot)
    }

    @Test
    fun fingerprintAndEventCountIgnoreDiscardedDuplicates() {
        val creation = creation("e1", 1_000, 100_000, "Ana", null, null)
        val payment = payment("e2", 2_000, 20_000)
        val withoutDuplicate = requireSnapshot(reducer.reduce(listOf(creation, payment)))
        val withDuplicateResult = reducer.reduce(listOf(payment, creation, payment))
        val withDuplicate = requireSnapshot(withDuplicateResult)

        assertEquals(withoutDuplicate.journalFingerprint, withDuplicate.journalFingerprint)
        assertEquals(2, withDuplicate.journalEventCount)
        assertEquals(1, withDuplicateResult.discardedDuplicates.size)
    }

    @Test
    fun lastActivityIncludesCloseReversalAndRevertedEvents() {
        val snapshot = requireSnapshot(
            reducer.reduce(
                listOf(
                    creation("e1", 1_000, 100_000, "Ana", null, null),
                    payment("e2", 7_000, 20_000),
                    close("e3", 8_000),
                    reversal("e4", 9_000, "e2")
                )
            )
        )

        assertEquals(9_000, snapshot.lastActivityAt)
        assertEquals(4, snapshot.journalEventCount)
    }

    @Test
    fun reducerIsDeterministicAndFingerprintHandlesCanonicalStrings() {
        val events = listOf(
            creation("e1", 1_000, 100_000, "A\"na\n😀", "A1", "línea\\dos"),
            metadata("e2", 2_000, absent(), clear(), value("cambio"))
        )
        val expected = reducer.reduce(events)

        repeat(20) { index ->
            val reordered = if (index % 2 == 0) events.reversed() else events
            assertEquals(expected, reducer.reduce(reordered))
        }
        assertNotNull(requireSnapshot(expected).journalFingerprint)
        assertFalse(requireSnapshot(expected).journalFingerprint.isBlank())
    }

    @Test
    fun closeAfterFinancialClosureDoesNotMoveClosedAt() {
        val snapshot = requireSnapshot(
            reducer.reduce(
                listOf(
                    creation("e1", 1_000, 100_000, "Ana", null, null),
                    payment("e2", 2_000, 100_000),
                    close("e3", 3_000)
                )
            )
        )

        assertEquals(2_000L, snapshot.closedAt)
        assertEquals(3_000L, snapshot.lastActivityAt)
        assertEquals(LoanStatus.CLOSED, snapshot.status)
    }

    @Test
    fun metadataCanClearNullableFields() {
        val snapshot = requireSnapshot(
            reducer.reduce(
                listOf(
                    creation("e1", 1_000, 100_000, "Ana", "A1", "nota"),
                    metadata("e2", 2_000, absent(), clear(), clear())
                )
            )
        )

        assertNull(snapshot.defaultAccountId)
        assertNull(snapshot.notes)
        assertEquals("Ana", snapshot.counterpartyName)
    }

    @Test
    fun rejectsNonPositiveCreationTopupAndPaymentAmounts() {
        val creationResult = reducer.reduce(
            listOf(creation("e1", 1_000, 0, "Ana", null, null))
        )
        val topupResult = reducer.reduce(
            listOf(
                creation("e1", 1_000, 100_000, "Ana", null, null),
                topup("e2", 2_000, 0)
            )
        )
        val paymentResult = reducer.reduce(
            listOf(
                creation("e1", 1_000, 100_000, "Ana", null, null),
                payment("e2", 2_000, 0)
            )
        )

        assertTrue(codes(creationResult).contains(LoanDiagnosticCode.NON_POSITIVE_PRINCIPAL))
        assertTrue(codes(topupResult).contains(LoanDiagnosticCode.INVALID_EVENT_PAYLOAD))
        assertTrue(codes(paymentResult).contains(LoanDiagnosticCode.INVALID_EVENT_PAYLOAD))
        assertNull(creationResult.snapshot)
        assertNull(topupResult.snapshot)
        assertNull(paymentResult.snapshot)
    }

    @Test
    fun detectsPaymentAndAdjustmentOverflow() {
        val paymentOverflow = reducer.reduce(
            listOf(
                creation("e1", 1_000, Long.MAX_VALUE, "Ana", null, null),
                payment("e2", 2_000, Long.MAX_VALUE),
                payment("e3", 3_000, 1)
            )
        )
        val adjustmentOverflow = reducer.reduce(
            listOf(
                creation("e1", 1_000, Long.MAX_VALUE, "Ana", null, null),
                adjustment("e2", 2_000, 1)
            )
        )

        assertTrue(codes(paymentOverflow).contains(LoanDiagnosticCode.ARITHMETIC_OVERFLOW))
        assertTrue(codes(adjustmentOverflow).contains(LoanDiagnosticCode.ARITHMETIC_OVERFLOW))
        assertNull(paymentOverflow.snapshot)
        assertNull(adjustmentOverflow.snapshot)
    }

    @Test
    fun rejectsInvalidUnicodeDuringCanonicalFingerprinting() {
        val result = reducer.reduce(
            listOf(creation("e1", 1_000, 100_000, "\uD800", null, null))
        )

        assertEquals(ReductionResultType.INVALID, result.type)
        assertTrue(codes(result).contains(LoanDiagnosticCode.INVALID_EVENT_PAYLOAD))
        assertNull(result.snapshot)
    }

    @Test
    fun fingerprintPreservesDistinctInt64ValuesWithoutIeee754Loss() {
        val maximum = requireSnapshot(
            reducer.reduce(listOf(creation("e1", 1_000, Long.MAX_VALUE, "Ana", null, null)))
        )
        val previous = requireSnapshot(
            reducer.reduce(listOf(creation("e1", 1_000, Long.MAX_VALUE - 1, "Ana", null, null)))
        )

        assertFalse(maximum.journalFingerprint == previous.journalFingerprint)
    }

    @Test
    fun fingerprintUsesNormativeCanonicalJsonShape() {
        val result = reducer.reduce(
            listOf(creation("e1", 1_000, 100_000, "Ana", "A1", "Inicial"))
        )

        assertEquals(
            "[{\"account_id\":null,\"actor_id\":null,\"amount_cents\":\"100000\",\"event_id\":\"e1\",\"event_schema_version\":\"1\",\"event_type\":\"CREATION\",\"loan_id\":\"loan-1\",\"note\":null,\"occurred_at\":\"1000\",\"operation_id\":\"e1\",\"origin_id\":null,\"owner_id\":\"owner-1\",\"payload\":{\"counterparty_name\":\"Ana\",\"currency\":\"USD\",\"default_account_id\":\"A1\",\"loan_type\":\"LENT\",\"notes\":\"Inicial\"},\"recorded_at\":\"1000\",\"transaction_id\":null}]",
            LoanJournalFingerprint.canonicalJson(result.normalizedJournal)
        )
    }

    private fun requireSnapshot(result: LoanReductionResult): LoanSnapshot {
        assertEquals(ReductionResultType.VALID, result.type)
        assertNotNull(result.snapshot)
        return result.snapshot!!
    }

    private fun assertFinancial(
        snapshot: LoanSnapshot,
        principal: Long,
        paid: Long,
        net: Long,
        pending: Long,
        overpaid: Long,
        status: LoanStatus,
        closedAt: Long?
    ) {
        assertEquals(principal, snapshot.principalCents)
        assertEquals(paid, snapshot.totalPaidCents)
        assertEquals(net, snapshot.netBalanceCents)
        assertEquals(pending, snapshot.pendingCents)
        assertEquals(overpaid, snapshot.overpaidCents)
        assertEquals(status, snapshot.status)
        assertEquals(closedAt, snapshot.closedAt)
    }

    private fun codes(result: LoanReductionResult): List<LoanDiagnosticCode> =
        result.diagnostics.map { it.code }

    private fun creation(
        id: String,
        occurredAt: Long,
        amount: Long,
        counterparty: String,
        accountId: String?,
        notes: String?
    ): LoanJournalEntry {
        val payload = mutableMapOf<String, Any?>(
            "loanType" to "LENT",
            "counterpartyName" to counterparty,
            "currency" to "USD"
        )
        if (accountId != null) payload["defaultAccountId"] = accountId
        if (notes != null) payload["notes"] = notes
        return entry(id, occurredAt, "CREATION", amount, payload)
    }

    private fun topup(id: String, occurredAt: Long, amount: Long): LoanJournalEntry =
        entry(id, occurredAt, "TOPUP", amount, emptyMap())

    private fun payment(id: String, occurredAt: Long, amount: Long): LoanJournalEntry =
        entry(id, occurredAt, "PAYMENT", amount, emptyMap())

    private fun paymentAlias(id: String, occurredAt: Long, amount: Long, type: String): LoanJournalEntry =
        entry(id, occurredAt, type, amount, emptyMap())

    private fun adjustment(id: String, occurredAt: Long, amount: Long): LoanJournalEntry =
        entry(id, occurredAt, "ADJUSTMENT", amount, mapOf("reason" to "correction"))

    private fun close(id: String, occurredAt: Long): LoanJournalEntry =
        entry(id, occurredAt, "CLOSE", null, emptyMap())

    private fun reversal(id: String, occurredAt: Long, targetId: String): LoanJournalEntry =
        entry(
            id,
            occurredAt,
            "REVERSAL",
            null,
            mapOf("targetEventId" to targetId, "reason" to "mistake")
        )

    private fun metadata(
        id: String,
        occurredAt: Long,
        counterparty: Change,
        account: Change,
        notes: Change
    ): LoanJournalEntry {
        val changes = mutableMapOf<String, Any?>()
        if (counterparty.present) changes["counterpartyName"] = counterparty.value
        if (account.present) changes["defaultAccountId"] = account.value
        if (notes.present) changes["notes"] = notes.value
        return entry(id, occurredAt, "METADATA_CHANGED", null, mapOf("changes" to changes))
    }

    private fun entry(
        id: String,
        occurredAt: Long,
        type: String,
        amount: Long?,
        payload: Map<String, Any?>
    ): LoanJournalEntry = LoanJournalEntry(
        eventId = id,
        operationId = id,
        loanId = "loan-1",
        ownerId = "owner-1",
        rawEventType = type,
        eventSchemaVersion = 1,
        amountCents = amount,
        accountId = null,
        transactionId = null,
        note = null,
        occurredAt = occurredAt,
        recordedAt = occurredAt,
        actorId = null,
        originId = null,
        rawPayload = payload
    )

    private fun absent() = Change(false, null)

    private fun clear() = Change(true, null)

    private fun value(value: String) = Change(true, value)

    private data class Change(val present: Boolean, val value: String?)

    companion object {
        private const val EXPECTED_CREATION_FINGERPRINT = "aaf32e6a3309ec64bef0cb7a4db46e6a1cc414845ad410bc42a7bed5726632d1"
    }
}
