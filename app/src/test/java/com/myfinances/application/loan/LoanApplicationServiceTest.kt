package com.jcadenas.xpendz.application.loan

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
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.projection.FakeLoanProjectionQueryRepository
import com.jcadenas.xpendz.domain.loan.projection.FakeLoanProjector
import com.jcadenas.xpendz.domain.loan.reducer.DefaultLoanReducer
import com.jcadenas.xpendz.domain.loan.repository.FakeLoanRepository
import com.jcadenas.xpendz.domain.loan.service.DefaultLoanAggregateService
import com.jcadenas.xpendz.domain.loan.service.error.BusinessRuleViolation
import com.jcadenas.xpendz.domain.loan.service.error.InvariantViolation
import com.jcadenas.xpendz.domain.loan.service.error.ValidationError
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class LoanApplicationServiceTest {
    private lateinit var repository: FakeLoanRepository
    private lateinit var aggregate: RecordingLoanAggregateService
    private lateinit var fakeProjector: FakeLoanProjector
    private lateinit var projector: RecordingLoanProjector
    private lateinit var query: FakeLoanProjectionQueryRepository
    private lateinit var service: LoanApplicationService
    private var operationSequence = 1

    @Before
    fun setUp() {
        operationSequence = 1
        repository = FakeLoanRepository()
        val delegate = DefaultLoanAggregateService(repository, DefaultLoanReducer())
        aggregate = RecordingLoanAggregateService(delegate)
        fakeProjector = FakeLoanProjector()
        projector = RecordingLoanProjector(fakeProjector)
        query = FakeLoanProjectionQueryRepository(fakeProjector)
        service = LoanApplicationService(aggregate, projector)
    }

    @Test
    fun createLoanIsAppliedAndProjected() {
        val result = service.process(create(nextOperationId()))
        assertEquals(Outcome.APPLIED, result.outcome)
        assertEquals(1, aggregate.callCount)
        assertEquals(1, projector.projectCallCount)
        assertNotNull(query.getSummaryProjection(OWNER_ID, LOAN_ID))
    }

    @Test
    fun registerPaymentIsAppliedAndProjected() {
        service.process(create(nextOperationId()))
        operationSequence++
        val result = service.process(payment(nextOperationId(), query.getSummaryProjection(OWNER_ID, LOAN_ID)!!.journalFingerprint, 40_000))
        assertEquals(Outcome.APPLIED, result.outcome)
        assertEquals(2, aggregate.callCount)
        assertEquals(2, projector.projectCallCount)
        assertEquals(1, query.getPaymentProjections(OWNER_ID, LOAN_ID).size)
    }

    @Test
    fun addPrincipalIsAppliedAndProjected() {
        service.process(create(nextOperationId()))
        operationSequence++
        val result = service.process(topup(nextOperationId(), query.getSummaryProjection(OWNER_ID, LOAN_ID)!!.journalFingerprint, 30_000))
        assertEquals(Outcome.APPLIED, result.outcome)
        assertEquals(2, aggregate.callCount)
        assertEquals(2, projector.projectCallCount)
        assertEquals(130_000, query.getSummaryProjection(OWNER_ID, LOAN_ID)!!.principalCents)
    }

    @Test
    fun adjustPrincipalIsAppliedAndProjected() {
        service.process(create(nextOperationId()))
        operationSequence++
        val result = service.process(adjustment(nextOperationId(), query.getSummaryProjection(OWNER_ID, LOAN_ID)!!.journalFingerprint, -20_000))
        assertEquals(Outcome.APPLIED, result.outcome)
        assertEquals(2, aggregate.callCount)
        assertEquals(2, projector.projectCallCount)
        assertEquals(80_000, query.getSummaryProjection(OWNER_ID, LOAN_ID)!!.principalCents)
    }

    @Test
    fun updateMetadataIsAppliedAndProjected() {
        service.process(create(nextOperationId()))
        operationSequence++
        val result = service.process(metadata(nextOperationId(), query.getSummaryProjection(OWNER_ID, LOAN_ID)!!.journalFingerprint))
        assertEquals(Outcome.APPLIED, result.outcome)
        assertEquals(2, aggregate.callCount)
        assertEquals(2, projector.projectCallCount)
        assertEquals("Ana Pérez", query.getSummaryProjection(OWNER_ID, LOAN_ID)!!.counterparty)
    }

    @Test
    fun reversePaymentIsAppliedAndProjected() {
        service.process(create(nextOperationId()))
        operationSequence++
        val paymentOp = nextOperationId()
        val paid = service.process(payment(paymentOp, query.getSummaryProjection(OWNER_ID, LOAN_ID)!!.journalFingerprint, 40_000))
        operationSequence++
        val result = service.process(reversal(nextOperationId(), paid.currentSnapshot.journalFingerprint, paid.event.eventId))
        assertEquals(Outcome.APPLIED, result.outcome)
        assertEquals(3, aggregate.callCount)
        assertEquals(3, projector.projectCallCount)
        assertEquals(0, query.getPaymentProjections(OWNER_ID, LOAN_ID).size)
    }

    @Test
    fun closeLoanIsAppliedAndProjected() {
        service.process(create(nextOperationId()))
        operationSequence++
        val paid = service.process(payment(nextOperationId(), query.getSummaryProjection(OWNER_ID, LOAN_ID)!!.journalFingerprint, 100_000))
        operationSequence++
        val result = service.process(close(nextOperationId(), paid.currentSnapshot.journalFingerprint))
        assertEquals(Outcome.APPLIED, result.outcome)
        assertEquals(3, aggregate.callCount)
        assertEquals(3, projector.projectCallCount)
        assertEquals(LoanStatus.CLOSED, query.getSummaryProjection(OWNER_ID, LOAN_ID)!!.status)
    }

    @Test
    fun replayDoesNotProjectAgain() {
        val command = create(nextOperationId())
        val first = service.process(command)
        assertEquals(Outcome.APPLIED, first.outcome)
        assertEquals(1, aggregate.callCount)
        assertEquals(1, projector.projectCallCount)
        val second = service.process(command)
        assertEquals(Outcome.REPLAYED, second.outcome)
        assertEquals(2, aggregate.callCount)
        assertEquals(1, projector.projectCallCount)
        assertEquals(first.currentSnapshot, second.currentSnapshot)
    }

    @Test
    fun validationErrorDoesNotProject() {
        val beforeProject = projector.projectCallCount
        assertThrows(ValidationError::class.java) { service.process(createInvalidPrincipal()) }
        assertEquals(1, aggregate.callCount)
        assertEquals(beforeProject, projector.projectCallCount)
    }

    @Test
    fun businessRuleViolationDoesNotProject() {
        val beforeProject = projector.projectCallCount
        assertThrows(BusinessRuleViolation::class.java) { service.process(paymentOnNonExistingLoan()) }
        assertEquals(1, aggregate.callCount)
        assertEquals(beforeProject, projector.projectCallCount)
    }

    @Test
    fun invariantViolationDoesNotProject() {
        val first = create("00000000-0000-4000-8000-000000000001")
        service.process(first)
        val beforeProject = projector.projectCallCount
        val second = CreateLoanCommand(
            first.envelope,
            LoanType.BORROWED,
            200_000,
            "Bob",
            "EUR",
            "A2",
            "tx-create-2",
            "Otro"
        )
        assertThrows(InvariantViolation::class.java) { service.process(second) }
        assertEquals(2, aggregate.callCount)
        assertEquals(beforeProject, projector.projectCallCount)
    }

    private fun create(operationId: String) = CreateLoanCommand(
        envelope(LoanCommandType.CREATE_LOAN, operationId, null, 1_000),
        LoanType.LENT,
        100_000,
        "Ana",
        "USD",
        "A1",
        "tx-create",
        "Inicial"
    )

    private fun createInvalidPrincipal() = CreateLoanCommand(
        envelope(LoanCommandType.CREATE_LOAN, nextOperationId(), null, 1_000),
        LoanType.LENT,
        0,
        "Ana",
        "USD",
        "A1",
        "tx-create",
        "Inicial"
    )

    private fun payment(operationId: String, fingerprint: String, amount: Long) = RegisterPaymentCommand(
        envelope(LoanCommandType.REGISTER_PAYMENT, operationId, fingerprint, 2_000L + operationSequence),
        amount,
        "A1",
        "tx-$operationId",
        "Pago"
    )

    private fun paymentOnNonExistingLoan() = RegisterPaymentCommand(
        envelope(LoanCommandType.REGISTER_PAYMENT, nextOperationId(), null, 2_000),
        10_000,
        "A1",
        "tx-pay",
        "Pago"
    )

    private fun topup(operationId: String, fingerprint: String, amount: Long) = AddPrincipalCommand(
        envelope(LoanCommandType.ADD_PRINCIPAL, operationId, fingerprint, 4_000L + operationSequence),
        amount,
        "A1",
        "tx-$operationId",
        "Capital"
    )

    private fun adjustment(operationId: String, fingerprint: String, delta: Long) = AdjustPrincipalCommand(
        envelope(LoanCommandType.ADJUST_PRINCIPAL, operationId, fingerprint, 5_000L + operationSequence),
        delta,
        "Corrección",
        "A1",
        "tx-$operationId",
        null
    )

    private fun metadata(operationId: String, fingerprint: String) = UpdateMetadataCommand(
        envelope(LoanCommandType.UPDATE_METADATA, operationId, fingerprint, 6_000L + operationSequence),
        FieldChange(true, "Ana Pérez"),
        FieldChange(false, null),
        FieldChange(false, null)
    )

    private fun reversal(operationId: String, fingerprint: String, target: String) = ReversePaymentCommand(
        envelope(LoanCommandType.REVERSE_PAYMENT, operationId, fingerprint, 3_000L + operationSequence),
        target,
        "Error",
        null
    )

    private fun close(operationId: String, fingerprint: String) = CloseLoanCommand(
        envelope(LoanCommandType.CLOSE_LOAN, operationId, fingerprint, 8_000L + operationSequence),
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

    companion object {
        private const val OWNER_ID = "owner-1"
        private const val LOAN_ID = "loan-1"
    }
}
