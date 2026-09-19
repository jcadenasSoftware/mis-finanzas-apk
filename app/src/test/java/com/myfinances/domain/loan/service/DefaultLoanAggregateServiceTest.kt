package com.jcadenas.xpendz.domain.loan.service

import com.jcadenas.xpendz.domain.loan.aggregate.LoanCommandResult
import com.jcadenas.xpendz.domain.loan.aggregate.Outcome
import com.jcadenas.xpendz.domain.loan.commands.AddPrincipalCommand
import com.jcadenas.xpendz.domain.loan.commands.AdjustPrincipalCommand
import com.jcadenas.xpendz.domain.loan.commands.CloseLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.CreateLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.FieldChange
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandEnvelope
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandType
import com.jcadenas.xpendz.domain.loan.commands.RegisterPaymentCommand
import com.jcadenas.xpendz.domain.loan.commands.ReversePaymentCommand
import com.jcadenas.xpendz.domain.loan.commands.UpdateMetadataCommand
import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnostic
import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnosticCode
import com.jcadenas.xpendz.domain.loan.journal.LoanEventPayload
import com.jcadenas.xpendz.domain.loan.journal.LoanEventType
import com.jcadenas.xpendz.domain.loan.journal.LoanJournalEntry
import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.projection.LoanProjectionChangeType
import com.jcadenas.xpendz.domain.loan.reducer.DefaultLoanReducer
import com.jcadenas.xpendz.domain.loan.reducer.LoanReducer
import com.jcadenas.xpendz.domain.loan.reducer.LoanReductionResult
import com.jcadenas.xpendz.domain.loan.reducer.ReductionResultType
import com.jcadenas.xpendz.domain.loan.repository.FakeLoanRepository
import com.jcadenas.xpendz.domain.loan.service.error.BusinessRuleViolation
import com.jcadenas.xpendz.domain.loan.service.error.InvariantViolation
import com.jcadenas.xpendz.domain.loan.service.error.LoanAggregateErrorCode
import com.jcadenas.xpendz.domain.loan.service.error.ValidationError
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class DefaultLoanAggregateServiceTest {
    private lateinit var repository: FakeLoanRepository
    private lateinit var service: DefaultLoanAggregateService
    private var operationSequence = 1

    @Before
    fun setUp() {
        repository = FakeLoanRepository()
        service = DefaultLoanAggregateService(repository, DefaultLoanReducer())
        operationSequence = 1
    }

    @Test
    fun c1CreatesLoanWithExactlyOneCreationEvent() {
        val result = service.process(createLoan(nextOperationId()))

        assertEquals(Outcome.APPLIED, result.outcome)
        assertEquals(LoanEventType.CREATION, result.event.eventType)
        assertEquals(result.operationId, result.event.eventId)
        assertEquals(result.operationId, result.event.operationId)
        assertEquals(result.event.occurredAt, result.event.recordedAt)
        assertNull(result.previousSnapshot)
        assertEquals(100_000, result.currentSnapshot.principalCents)
        assertEquals(1, repository.appendCount)
        assertProjectionTypes(result, LoanProjectionChangeType.REBUILD_LOAN_SNAPSHOT)
    }

    @Test
    fun c2RegistersPartialPaymentAndAddsConceptualProjection() {
        createInitialLoan()
        val operationId = nextOperationId()
        val result = service.process(payment(operationId, fingerprint(), 25_000))

        assertEquals(Outcome.APPLIED, result.outcome)
        assertEquals(LoanEventType.PAYMENT, result.event.eventType)
        assertEquals(25_000, result.currentSnapshot.totalPaidCents)
        assertEquals(75_000, result.currentSnapshot.pendingCents)
        assertProjectionTypes(
            result,
            LoanProjectionChangeType.ADD_PAYMENT_PROJECTION,
            LoanProjectionChangeType.REBUILD_LOAN_SNAPSHOT
        )
        assertEquals(operationId, result.projectionChanges[0].sourceEventId)
    }

    @Test
    fun c3ReplaySkipsFingerprintAndReturnsCurrentSnapshotWithoutAppending() {
        createInitialLoan()
        val staleFingerprint = fingerprint()
        val operationId = nextOperationId()
        val original = payment(operationId, staleFingerprint, 20_000)
        service.process(original)
        service.process(topup(nextOperationId(), fingerprint(), 10_000))
        val beforeReplay = repository.appendCount

        val replay = service.process(original)

        assertEquals(Outcome.REPLAYED, replay.outcome)
        assertEquals(beforeReplay, repository.appendCount)
        assertEquals(90_000, replay.currentSnapshot.pendingCents)
        assertEquals(replay.currentSnapshot, replay.previousSnapshot)
        assertProjectionTypes(replay, LoanProjectionChangeType.NONE)
    }

    @Test
    fun c4RejectsDifferentCommandUsingExistingOperationId() {
        createInitialLoan()
        val operationId = nextOperationId()
        service.process(payment(operationId, fingerprint(), 20_000))
        val eventCount = repository.appendCount

        val error = assertThrows(InvariantViolation::class.java) {
            service.process(payment(operationId, fingerprint(), 30_000))
        }

        assertEquals(LoanAggregateErrorCode.OPERATION_CONFLICT, error.code)
        assertEquals(eventCount, repository.appendCount)
    }

    @Test
    fun c5ExactPaymentClosesLoan() {
        createInitialLoan()
        val result = service.process(payment(nextOperationId(), fingerprint(), 100_000))

        assertEquals(0, result.currentSnapshot.pendingCents)
        assertEquals(LoanStatus.CLOSED, result.currentSnapshot.status)
    }

    @Test
    fun c6RejectsPaymentWhenLoanHasNoPendingBalance() {
        createInitialLoan()
        service.process(payment(nextOperationId(), fingerprint(), 100_000))
        val eventCount = repository.appendCount

        val error = assertThrows(BusinessRuleViolation::class.java) {
            service.process(payment(nextOperationId(), fingerprint(), 1_000))
        }

        assertEquals(LoanAggregateErrorCode.LOAN_HAS_NO_PENDING_BALANCE, error.code)
        assertEquals(eventCount, repository.appendCount)
    }

    @Test
    fun c7AddingPrincipalReopensClosedLoan() {
        createInitialLoan()
        service.process(payment(nextOperationId(), fingerprint(), 100_000))

        val result = service.process(topup(nextOperationId(), fingerprint(), 20_000))

        assertEquals(LoanEventType.TOPUP, result.event.eventType)
        assertEquals(20_000, result.currentSnapshot.pendingCents)
        assertEquals(LoanStatus.OPEN, result.currentSnapshot.status)
    }

    @Test
    fun c8AdjustsPrincipalWithOneAdjustmentEvent() {
        createInitialLoan()
        val result = service.process(adjustment(nextOperationId(), fingerprint(), -10_000))

        assertEquals(LoanEventType.ADJUSTMENT, result.event.eventType)
        assertEquals(90_000, result.currentSnapshot.principalCents)
        assertEquals(2, repository.appendCount)
    }

    @Test
    fun c9RejectsAdjustmentBelowTotalPaid() {
        createInitialLoan()
        service.process(payment(nextOperationId(), fingerprint(), 80_000))
        val eventCount = repository.appendCount

        val error = assertThrows(BusinessRuleViolation::class.java) {
            service.process(adjustment(nextOperationId(), fingerprint(), -30_000))
        }

        assertEquals(LoanAggregateErrorCode.PRINCIPAL_BELOW_TOTAL_PAID, error.code)
        assertEquals(eventCount, repository.appendCount)
    }

    @Test
    fun c10UpdatesMetadataWithoutChangingFinancialState() {
        createInitialLoan()
        val result = service.process(
            metadata(
                nextOperationId(),
                fingerprint(),
                FieldChange(true, "Ana Pérez"),
                FieldChange(true, "A2"),
                FieldChange(true, "Acuerdo actualizado")
            )
        )

        assertEquals(LoanEventType.METADATA_CHANGED, result.event.eventType)
        assertEquals("Ana Pérez", result.currentSnapshot.counterpartyName)
        assertEquals("A2", result.currentSnapshot.defaultAccountId)
        assertEquals("Acuerdo actualizado", result.currentSnapshot.notes)
        assertEquals(100_000, result.currentSnapshot.principalCents)
    }

    @Test
    fun c11RejectsMetadataNoOp() {
        createInitialLoan()
        val eventCount = repository.appendCount

        val error = assertThrows(BusinessRuleViolation::class.java) {
            service.process(
                metadata(
                    nextOperationId(),
                    fingerprint(),
                    FieldChange(true, "Ana"),
                    FieldChange(false, null),
                    FieldChange(false, null)
                )
            )
        }

        assertEquals(LoanAggregateErrorCode.NO_EFFECTIVE_CHANGE, error.code)
        assertEquals(eventCount, repository.appendCount)
    }

    @Test
    fun c12ReversesEffectivePaymentAndRemovesConceptualProjection() {
        createInitialLoan()
        val paymentOperation = nextOperationId()
        service.process(payment(paymentOperation, fingerprint(), 40_000))

        val result = service.process(reversal(nextOperationId(), fingerprint(), paymentOperation))

        assertEquals(LoanEventType.REVERSAL, result.event.eventType)
        assertEquals(0, result.currentSnapshot.totalPaidCents)
        assertEquals(100_000, result.currentSnapshot.pendingCents)
        assertProjectionTypes(
            result,
            LoanProjectionChangeType.REMOVE_PAYMENT_PROJECTION,
            LoanProjectionChangeType.REBUILD_LOAN_SNAPSHOT
        )
        assertEquals(paymentOperation, result.projectionChanges[0].sourceEventId)
    }

    @Test
    fun c13AddsExplicitCloseMarkerOnlyAfterFinancialClosure() {
        createInitialLoan()
        service.process(payment(nextOperationId(), fingerprint(), 100_000))
        val result = service.process(close(nextOperationId(), fingerprint()))

        assertEquals(LoanEventType.CLOSE, result.event.eventType)
        assertEquals(LoanStatus.CLOSED, result.currentSnapshot.status)
        assertEquals(0, result.currentSnapshot.pendingCents)
    }

    @Test
    fun c14RejectsSecondConcurrentCommandWithStaleFingerprint() {
        createInitialLoan()
        val sharedFingerprint = fingerprint()
        val first = payment(nextOperationId(), sharedFingerprint, 30_000)
        val second = payment(nextOperationId(), sharedFingerprint, 25_000)
        service.process(first)
        val eventCount = repository.appendCount

        val error = assertThrows(BusinessRuleViolation::class.java) {
            service.process(second)
        }

        assertEquals(LoanAggregateErrorCode.STALE_AGGREGATE_VERSION, error.code)
        assertEquals(eventCount, repository.appendCount)
    }

    @Test
    fun c15SameConcurrentOperationConvergesToOneEvent() {
        createInitialLoan()
        val command = payment(nextOperationId(), fingerprint(), 10_000)

        val applied = service.process(command)
        val replayed = service.process(command)

        assertEquals(Outcome.APPLIED, applied.outcome)
        assertEquals(Outcome.REPLAYED, replayed.outcome)
        assertEquals(2, repository.appendCount)
        assertEquals(10_000, replayed.currentSnapshot.totalPaidCents)
    }

    @Test
    fun validatesEnvelopeBeforeRepositoryMutation() {
        val command = createLoan("not-a-uuid")

        val error = assertThrows(ValidationError::class.java) { service.process(command) }

        assertEquals(LoanAggregateErrorCode.INVALID_OPERATION_ID, error.code)
        assertEquals(0, repository.appendCount)
    }

    @Test
    fun requiresFingerprintForNewCommandsButNotReplay() {
        createInitialLoan()
        val eventCount = repository.appendCount

        val error = assertThrows(ValidationError::class.java) {
            service.process(payment(nextOperationId(), null, 10_000))
        }

        assertEquals(LoanAggregateErrorCode.EXPECTED_FINGERPRINT_REQUIRED, error.code)
        assertEquals(eventCount, repository.appendCount)
    }

    @Test
    fun rejectsCandidateJournalWhenReducerDoesNotReturnValid() {
        val delegate = DefaultLoanReducer()
        val rejectingReducer = object : LoanReducer {
            override fun reduce(events: Collection<LoanJournalEntry>): LoanReductionResult =
                delegate.reduce(events)

            override fun reduceCanonical(events: Collection<LoanMovement>): LoanReductionResult {
                if (events.isNotEmpty()) {
                    return LoanReductionResult(
                        type = ReductionResultType.INVALID,
                        snapshot = null,
                        normalizedJournal = events.toList(),
                        diagnostics = listOf(
                            LoanDiagnostic(LoanDiagnosticCode.INVALID_EVENT_PAYLOAD, null, null)
                        ),
                        effectiveEvents = emptyList(),
                        revertedEvents = emptyList(),
                        discardedDuplicates = emptyList()
                    )
                }
                return delegate.reduceCanonical(events)
            }
        }
        val rejectingService = DefaultLoanAggregateService(repository, rejectingReducer)

        val error = assertThrows(InvariantViolation::class.java) {
            rejectingService.process(createLoan(nextOperationId()))
        }

        assertEquals(LoanAggregateErrorCode.CANDIDATE_JOURNAL_INVALID, error.code)
        assertEquals(0, repository.appendCount)
    }

    @Test
    fun rejectsPaymentAbovePendingWithoutAppending() {
        createInitialLoan()
        val eventCount = repository.appendCount

        val error = assertThrows(BusinessRuleViolation::class.java) {
            service.process(payment(nextOperationId(), fingerprint(), 100_001))
        }

        assertEquals(LoanAggregateErrorCode.PAYMENT_EXCEEDS_PENDING, error.code)
        assertEquals(eventCount, repository.appendCount)
    }

    @Test
    fun rejectsCloseWhilePendingWithoutAppending() {
        createInitialLoan()
        val eventCount = repository.appendCount

        val error = assertThrows(BusinessRuleViolation::class.java) {
            service.process(close(nextOperationId(), fingerprint()))
        }

        assertEquals(LoanAggregateErrorCode.LOAN_HAS_PENDING_BALANCE, error.code)
        assertEquals(eventCount, repository.appendCount)
    }

    @Test
    fun preservesArithmeticOverflowClassificationFromCandidateReducer() {
        service.process(
            CreateLoanCommand(
                envelope(LoanCommandType.CREATE_LOAN, nextOperationId(), null, 1_000),
                LoanType.LENT,
                Long.MAX_VALUE,
                "Ana",
                "USD",
                null,
                null,
                null
            )
        )
        val eventCount = repository.appendCount

        val error = assertThrows(BusinessRuleViolation::class.java) {
            service.process(topup(nextOperationId(), fingerprint(), 1))
        }

        assertEquals(LoanAggregateErrorCode.ARITHMETIC_OVERFLOW, error.code)
        assertEquals(eventCount, repository.appendCount)
    }

    @Test
    fun rejectsAlreadyReversedPayment() {
        createInitialLoan()
        val paymentOperation = nextOperationId()
        service.process(payment(paymentOperation, fingerprint(), 20_000))
        service.process(reversal(nextOperationId(), fingerprint(), paymentOperation))
        val eventCount = repository.appendCount

        val error = assertThrows(BusinessRuleViolation::class.java) {
            service.process(reversal(nextOperationId(), fingerprint(), paymentOperation))
        }

        assertEquals(LoanAggregateErrorCode.PAYMENT_ALREADY_REVERSED, error.code)
        assertEquals(eventCount, repository.appendCount)
    }

    @Test
    fun rejectsCrossLoanReversalTarget() {
        createInitialLoan()
        val crossLoanPayment = LoanMovement(
            eventId = "cross-event",
            operationId = "cross-operation",
            loanId = "loan-2",
            ownerId = OWNER_ID,
            eventType = LoanEventType.PAYMENT,
            eventSchemaVersion = 1,
            amountCents = 10_000,
            accountId = null,
            transactionId = null,
            note = null,
            occurredAt = 2_000,
            recordedAt = 2_000,
            actorId = null,
            originId = null,
            payload = LoanEventPayload.PaymentPayload(null, null)
        )
        repository.appendEvent(crossLoanPayment)
        val eventCount = repository.appendCount

        val error = assertThrows(BusinessRuleViolation::class.java) {
            service.process(reversal(nextOperationId(), fingerprint(), "cross-event"))
        }

        assertEquals(LoanAggregateErrorCode.CROSS_LOAN_TARGET, error.code)
        assertEquals(eventCount, repository.appendCount)
    }

    @Test
    fun rejectsNonPaymentReversalTarget() {
        val creation = createInitialLoan()
        val eventCount = repository.appendCount

        val error = assertThrows(BusinessRuleViolation::class.java) {
            service.process(reversal(nextOperationId(), fingerprint(), creation.event.eventId))
        }

        assertEquals(LoanAggregateErrorCode.TARGET_NOT_PAYMENT, error.code)
        assertEquals(eventCount, repository.appendCount)
    }

    @Test
    fun rejectsUuidVersionOutsideNativeContract() {
        val error = assertThrows(ValidationError::class.java) {
            service.process(createLoan("00000000-0000-1000-8000-000000000001"))
        }

        assertEquals(LoanAggregateErrorCode.INVALID_OPERATION_ID, error.code)
        assertEquals(0, repository.appendCount)
    }

    private fun createInitialLoan(): LoanCommandResult =
        service.process(createLoan(nextOperationId()))

    private fun fingerprint(): String =
        repository.loadSnapshot(OWNER_ID, LOAN_ID)!!.journalFingerprint

    private fun createLoan(operationId: String) = CreateLoanCommand(
        envelope(LoanCommandType.CREATE_LOAN, operationId, null, 1_000),
        LoanType.LENT,
        100_000,
        "Ana",
        "usd",
        "A1",
        "tx-create",
        "Inicial"
    )

    private fun payment(operationId: String, fingerprint: String?, amount: Long) =
        RegisterPaymentCommand(
            envelope(LoanCommandType.REGISTER_PAYMENT, operationId, fingerprint, 2_000L + operationSequence),
            amount,
            "A1",
            "tx-$operationId",
            "Pago"
        )

    private fun topup(operationId: String, fingerprint: String, amount: Long) =
        AddPrincipalCommand(
            envelope(LoanCommandType.ADD_PRINCIPAL, operationId, fingerprint, 3_000L + operationSequence),
            amount,
            "A1",
            "tx-$operationId",
            "Capital"
        )

    private fun adjustment(operationId: String, fingerprint: String, delta: Long) =
        AdjustPrincipalCommand(
            envelope(LoanCommandType.ADJUST_PRINCIPAL, operationId, fingerprint, 4_000L + operationSequence),
            delta,
            "Corrección",
            "A1",
            "tx-$operationId",
            null
        )

    private fun metadata(
        operationId: String,
        fingerprint: String,
        counterparty: FieldChange<String>,
        account: FieldChange<String>,
        notes: FieldChange<String>
    ) = UpdateMetadataCommand(
        envelope(LoanCommandType.UPDATE_METADATA, operationId, fingerprint, 5_000L + operationSequence),
        counterparty,
        account,
        notes
    )

    private fun reversal(operationId: String, fingerprint: String, targetEventId: String) =
        ReversePaymentCommand(
            envelope(LoanCommandType.REVERSE_PAYMENT, operationId, fingerprint, 6_000L + operationSequence),
            targetEventId,
            "Error",
            null
        )

    private fun close(operationId: String, fingerprint: String) = CloseLoanCommand(
        envelope(LoanCommandType.CLOSE_LOAN, operationId, fingerprint, 7_000L + operationSequence),
        "Confirmado",
        null
    )

    private fun envelope(
        type: LoanCommandType,
        operationId: String,
        fingerprint: String?,
        occurredAt: Long
    ) = LoanCommandEnvelope(
        type,
        operationId,
        LOAN_ID,
        OWNER_ID,
        fingerprint,
        occurredAt,
        "actor-1",
        "device-1"
    )

    private fun nextOperationId(): String =
        "00000000-0000-4000-8000-${operationSequence++.toString().padStart(12, '0')}"

    private fun assertProjectionTypes(
        result: LoanCommandResult,
        vararg expected: LoanProjectionChangeType
    ) {
        assertEquals(expected.toList(), result.projectionChanges.map { it.type })
    }

    companion object {
        private const val OWNER_ID = "owner-1"
        private const val LOAN_ID = "loan-1"
    }
}
