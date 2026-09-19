package com.jcadenas.xpendz.infrastructure.loan.projection.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.domain.loan.aggregate.LoanCommandResult
import com.jcadenas.xpendz.domain.loan.commands.AddPrincipalCommand
import com.jcadenas.xpendz.domain.loan.commands.CloseLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.CreateLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandEnvelope
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandType
import com.jcadenas.xpendz.domain.loan.commands.RegisterPaymentCommand
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.projection.LoanProjectionQueryRepository
import com.jcadenas.xpendz.domain.loan.projection.LoanProjector
import com.jcadenas.xpendz.domain.loan.reducer.DefaultLoanReducer
import com.jcadenas.xpendz.domain.loan.service.DefaultLoanAggregateService
import com.jcadenas.xpendz.domain.loan.service.LoanAggregateService
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus
import com.jcadenas.xpendz.infrastructure.loan.room.RoomLoanAggregateExecutor
import com.jcadenas.xpendz.infrastructure.loan.room.RoomLoanRepositoryAdapter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoanProjectorIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomLoanRepositoryAdapter
    private lateinit var service: LoanAggregateService
    private lateinit var projector: LoanProjector
    private lateinit var query: LoanProjectionQueryRepository
    private lateinit var databaseName: String
    private var operationSequence = 1

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "projector-${UUID.randomUUID()}.db"
        database = openDatabase()
        repository = RoomLoanRepositoryAdapter(database, database.canonicalLoanDao())
        val reducer = DefaultLoanReducer()
        service = RoomLoanAggregateExecutor(database, DefaultLoanAggregateService(repository, reducer))
        projector = DefaultLoanProjector(database, database.loanProjectionDao())
        query = RoomLoanProjectionQueryRepository(database.loanProjectionDao())
        operationSequence = 1
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun projectsCompleteLoanLifecycleAndRebuildsFromJournal() {
        val created = service.process(create(nextOperationId()))
        projector.project(created)

        val createdSummary = query.getSummaryProjection(OWNER_ID, LOAN_ID)
        assertNotNull(createdSummary)
        assertEquals(100_000, createdSummary!!.principalCents)
        assertEquals(0, createdSummary.totalPaidCents)
        assertEquals(LoanStatus.OPEN, createdSummary.status)

        val payment = service.process(payment(nextOperationId(), createdSummary.journalFingerprint, 40_000))
        projector.project(payment)

        val paidSummary = query.getSummaryProjection(OWNER_ID, LOAN_ID)!!
        assertEquals(40_000, paidSummary.totalPaidCents)
        assertEquals(60_000, paidSummary.pendingCents)

        val payments = query.getPaymentProjections(OWNER_ID, LOAN_ID)
        assertEquals(1, payments.size)
        assertEquals(40_000, payments.first().amountCents)

        val topup = service.process(topup(nextOperationId(), paidSummary.journalFingerprint, 50_000))
        projector.project(topup)

        val finalPayment = service.process(payment(nextOperationId(), topup.currentSnapshot.journalFingerprint, 110_000))
        projector.project(finalPayment)

        val closed = service.process(close(nextOperationId(), finalPayment.currentSnapshot.journalFingerprint))
        projector.project(closed)

        val closedSummary = query.getSummaryProjection(OWNER_ID, LOAN_ID)!!
        assertEquals(LoanStatus.CLOSED, closedSummary.status)
        assertEquals(0, closedSummary.pendingCents)
        assertEquals(2, query.getPaymentProjections(OWNER_ID, LOAN_ID).size)

        projector.rebuild(OWNER_ID, LOAN_ID, repository, DefaultLoanReducer())
        val rebuiltSummary = query.getSummaryProjection(OWNER_ID, LOAN_ID)
        assertEquals(closedSummary, rebuiltSummary)
    }

    @Test
    fun rebuildFromEmptyJournalLeavesNoProjections() {
        projector.rebuild(OWNER_ID, LOAN_ID, repository, DefaultLoanReducer())
        assertEquals(emptyList<Any>(), query.getPaymentProjections(OWNER_ID, LOAN_ID))
        assertNull(query.getSummaryProjection(OWNER_ID, LOAN_ID))
    }

    private fun openDatabase() = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        databaseName
    ).allowMainThreadQueries().addMigrations(
        AppDatabase.MIGRATION_12_13,
        AppDatabase.MIGRATION_13_14
    ).build()

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

    private fun payment(operationId: String, fingerprint: String, amount: Long) = RegisterPaymentCommand(
        envelope(LoanCommandType.REGISTER_PAYMENT, operationId, fingerprint, 2_000L + operationSequence),
        amount,
        "A1",
        "tx-$operationId",
        "Pago"
    )

    private fun topup(operationId: String, fingerprint: String, amount: Long) = AddPrincipalCommand(
        envelope(LoanCommandType.ADD_PRINCIPAL, operationId, fingerprint, 4_000L + operationSequence),
        amount,
        "A1",
        "tx-$operationId",
        "Capital"
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
