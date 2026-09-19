package com.jcadenas.xpendz.infrastructure.loan.di

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jcadenas.xpendz.application.loan.LoanApplicationService
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.domain.loan.aggregate.Outcome
import com.jcadenas.xpendz.domain.loan.commands.CreateLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandEnvelope
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandType
import com.jcadenas.xpendz.domain.loan.commands.RegisterPaymentCommand
import com.jcadenas.xpendz.domain.loan.commands.ReversePaymentCommand
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.projection.LoanProjectionQueryRepository
import com.jcadenas.xpendz.domain.loan.projection.LoanSummaryFilter
import com.jcadenas.xpendz.domain.loan.projection.SortBy
import com.jcadenas.xpendz.domain.loan.reducer.DefaultLoanReducer
import com.jcadenas.xpendz.domain.loan.service.DefaultLoanAggregateService
import com.jcadenas.xpendz.data.repository.LoanAdminStateRemotePublisher
import com.jcadenas.xpendz.data.repository.LoanAdminStateRepository
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus
import com.jcadenas.xpendz.infrastructure.loan.admin.LoanAdminStateEntity
import com.jcadenas.xpendz.infrastructure.loan.projection.room.DefaultLoanProjector
import com.jcadenas.xpendz.infrastructure.loan.projection.room.RoomLoanProjectionQueryRepository
import com.jcadenas.xpendz.infrastructure.loan.room.RoomLoanAggregateExecutor
import com.jcadenas.xpendz.infrastructure.loan.room.RoomLoanRepositoryAdapter
import com.jcadenas.xpendz.sync.DeviceIdProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoanCompositionIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var service: LoanApplicationService
    private lateinit var query: LoanProjectionQueryRepository
    private lateinit var databaseName: String
    private var operationSequence = 1

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "loan-composition-${UUID.randomUUID()}.db"
        database = openDatabase()
        val repository = RoomLoanRepositoryAdapter(database, database.canonicalLoanDao())
        val aggregate = DefaultLoanAggregateService(repository, DefaultLoanReducer())
        val executor = RoomLoanAggregateExecutor(database, aggregate)
        val projector = DefaultLoanProjector(database, database.loanProjectionDao())
        service = LoanApplicationService(executor, projector)
        query = RoomLoanProjectionQueryRepository(database.loanProjectionDao())
        operationSequence = 1
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun fullyWiredServiceExecutesCreatePaymentReverseAndReplay() {
        val created = service.process(create(nextOperationId()))
        assertEquals(Outcome.APPLIED, created.outcome)

        val createdSummary = query.getSummaryProjection(OWNER_ID, LOAN_ID)
        assertNotNull(createdSummary)
        assertEquals(100_000, createdSummary!!.principalCents)
        assertEquals(LoanStatus.OPEN, createdSummary.status)
        assertEquals("A1", createdSummary.defaultAccountId)
        assertEquals("Inicial", createdSummary.notes)
        assertEquals(0, createdSummary.paymentCount)
        assertNull(createdSummary.lastPaymentAt)
        assertEquals(0, createdSummary.progressPercent)

        val paymentCommand = payment(nextOperationId(), createdSummary.journalFingerprint, 40_000)
        val paid = service.process(paymentCommand)
        assertEquals(Outcome.APPLIED, paid.outcome)

        val paidSummary = query.getSummaryProjection(OWNER_ID, LOAN_ID)!!
        assertEquals(40_000, paidSummary.totalPaidCents)
        assertEquals(60_000, paidSummary.pendingCents)
        assertEquals(1, paidSummary.paymentCount)
        assertNotNull(paidSummary.lastPaymentAt)
        assertEquals(40, paidSummary.progressPercent)

        val paymentProjection = query.getPaymentProjections(OWNER_ID, LOAN_ID).first()
        assertEquals(40_000, paymentProjection.amountCents)

        val replay = service.process(paymentCommand)
        assertEquals(Outcome.REPLAYED, replay.outcome)
        assertEquals(paid.currentSnapshot, replay.currentSnapshot)
        assertEquals(1, query.getPaymentProjections(OWNER_ID, LOAN_ID).size)

        val reversed = service.process(reverse(nextOperationId(), paidSummary.journalFingerprint, paid.event.eventId))
        assertEquals(Outcome.APPLIED, reversed.outcome)

        val reversedSummary = query.getSummaryProjection(OWNER_ID, LOAN_ID)!!
        assertEquals(0, reversedSummary.totalPaidCents)
        assertEquals(100_000, reversedSummary.pendingCents)
        assertEquals(0, reversedSummary.paymentCount)
        assertNull(reversedSummary.lastPaymentAt)
        assertEquals(0, reversedSummary.progressPercent)
        assertTrue(query.getPaymentProjections(OWNER_ID, LOAN_ID).isEmpty())
    }

    @Test
    fun canListSummariesForOwnerWithFilterAndSort() {
        val created = service.process(create(nextOperationId()))
        assertEquals(Outcome.APPLIED, created.outcome)

        val single = query.getSummaryProjection(OWNER_ID, LOAN_ID)
        assertNotNull(single)

        val all = query.listSummaries(OWNER_ID, LoanSummaryFilter())
        assertEquals(1, all.size)
        assertEquals(LOAN_ID, all[0].loanId)

        val byType = query.listSummaries(OWNER_ID, LoanSummaryFilter(loanType = LoanType.LENT))
        assertEquals(1, byType.size)

        val byStatus = query.listSummaries(OWNER_ID, LoanSummaryFilter(status = LoanStatus.OPEN))
        assertEquals(1, byStatus.size)

        val byTypeAndStatus = query.listSummaries(OWNER_ID, LoanSummaryFilter(loanType = LoanType.BORROWED, status = LoanStatus.OPEN))
        assertTrue(byTypeAndStatus.isEmpty())

        val limited = query.listSummaries(OWNER_ID, LoanSummaryFilter(limit = 5))
        assertEquals(1, limited.size)

        val sorted = query.listSummaries(OWNER_ID, LoanSummaryFilter(sortBy = SortBy.PRINCIPAL_CENTS, ascending = true))
        assertEquals(1, sorted.size)
        assertEquals(single!!.principalCents, sorted[0].principalCents)
    }

    @Test
    fun archiveStateHidesLoanWithoutTouchingCanonicalData() = runBlocking {
        val created = service.process(create(nextOperationId()))
        assertEquals(Outcome.APPLIED, created.outcome)
        val payment = payment(nextOperationId(), created.currentSnapshot.journalFingerprint, 40_000)
        val paid = service.process(payment)
        assertEquals(Outcome.APPLIED, paid.outcome)

        val activeBefore = query.observeActiveSummaries(
            OWNER_ID,
            LoanSummaryFilter(loanType = LoanType.LENT, status = LoanStatus.OPEN)
        ).first()
        assertEquals(1, activeBefore.size)

        val journalBefore = database.canonicalLoanDao().getJournal(OWNER_ID, LOAN_ID)
        val snapshotBefore = database.canonicalLoanDao().loadSnapshot(OWNER_ID, LOAN_ID)
        val summaryBefore = query.getSummaryProjection(OWNER_ID, LOAN_ID)
        val paymentsBefore = query.getPaymentProjections(OWNER_ID, LOAN_ID)

        val published = mutableListOf<LoanAdminStateEntity>()
        val adminRepository = LoanAdminStateRepository(
            database.loanAdminStateDao(),
            object : LoanAdminStateRemotePublisher {
                override suspend fun publish(state: LoanAdminStateEntity) {
                    published += state
                }
            },
            DeviceIdProvider(context)
        )
        adminRepository.archive(OWNER_ID, LOAN_ID, "device-1")

        val activeAfter = query.observeActiveSummaries(
            OWNER_ID,
            LoanSummaryFilter(loanType = LoanType.LENT, status = LoanStatus.OPEN)
        ).first()
        assertTrue(activeAfter.isEmpty())
        assertEquals(journalBefore, database.canonicalLoanDao().getJournal(OWNER_ID, LOAN_ID))
        assertEquals(snapshotBefore, database.canonicalLoanDao().loadSnapshot(OWNER_ID, LOAN_ID))
        assertEquals(summaryBefore, query.getSummaryProjection(OWNER_ID, LOAN_ID))
        assertEquals(paymentsBefore, query.getPaymentProjections(OWNER_ID, LOAN_ID))
        assertEquals(1, published.size)
        assertEquals(true, published.first().pendingSync)
        assertEquals(false, database.loanAdminStateDao().getByLoan(OWNER_ID, LOAN_ID)!!.pendingSync)
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

    private fun reverse(operationId: String, fingerprint: String, target: String) = ReversePaymentCommand(
        envelope(LoanCommandType.REVERSE_PAYMENT, operationId, fingerprint, 3_000L + operationSequence),
        target,
        "Error",
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
