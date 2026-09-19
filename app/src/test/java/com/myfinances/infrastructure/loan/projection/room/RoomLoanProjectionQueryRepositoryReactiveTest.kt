package com.jcadenas.xpendz.infrastructure.loan.projection.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.projection.LoanSummaryFilter
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus
import com.jcadenas.xpendz.infrastructure.loan.admin.LoanAdminStateEntity
import com.jcadenas.xpendz.infrastructure.loan.projection.model.LoanSummaryProjectionEntity
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomLoanProjectionQueryRepositoryReactiveTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var dao: LoanProjectionDao
    private lateinit var repository: RoomLoanProjectionQueryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.loanProjectionDao()
        repository = RoomLoanProjectionQueryRepository(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeActiveSummariesReemitsAndFiltersClosedLoans() = runBlocking {
        val emissions = Channel<List<com.jcadenas.xpendz.domain.loan.projection.LoanSummaryProjection>>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) {
            repository.observeActiveSummaries(
                OWNER_ID,
                LoanSummaryFilter(loanType = LoanType.LENT, status = LoanStatus.OPEN)
            ).take(4).collect { emissions.send(it) }
        }

        val initial = emissions.receive()
        assertTrue(initial.isEmpty())

        dao.replaceSummaryProjection(activeProjection())
        val active = emissions.receive()
        assertEquals(1, active.size)
        assertEquals(LoanStatus.OPEN, active.first().status)
        assertEquals(100_000L, active.first().pendingCents)

        database.loanAdminStateDao().upsert(archivedState())
        val archived = emissions.receive()
        assertTrue(archived.isEmpty())

        dao.replaceSummaryProjection(closedProjection())
        val closed = emissions.receive()
        assertTrue(closed.isEmpty())

        job.cancel()
    }

    private fun activeProjection(): LoanSummaryProjectionEntity = LoanSummaryProjectionEntity(
        loanId = LOAN_ID,
        ownerId = OWNER_ID,
        counterparty = "Ana",
        loanType = "LENT",
        currency = "COP",
        defaultAccountId = ACCOUNT_ID,
        notes = "Nota",
        principalCents = 100_000L,
        totalPaidCents = 0L,
        pendingCents = 100_000L,
        overpaidCents = 0L,
        paymentCount = 0,
        lastPaymentAt = null,
        progressPercent = 0,
        status = LoanStatus.OPEN.name,
        closedAt = null,
        lastActivity = 1_000L,
        journalFingerprint = "fp-open"
    )

    private fun closedProjection(): LoanSummaryProjectionEntity = LoanSummaryProjectionEntity(
        loanId = LOAN_ID,
        ownerId = OWNER_ID,
        counterparty = "Ana",
        loanType = "LENT",
        currency = "COP",
        defaultAccountId = ACCOUNT_ID,
        notes = "Nota",
        principalCents = 100_000L,
        totalPaidCents = 100_000L,
        pendingCents = 0L,
        overpaidCents = 0L,
        paymentCount = 1,
        lastPaymentAt = 2_000L,
        progressPercent = 100,
        status = LoanStatus.CLOSED.name,
        closedAt = 2_000L,
        lastActivity = 2_000L,
        journalFingerprint = "fp-closed"
    )

    private fun archivedState(): LoanAdminStateEntity = LoanAdminStateEntity(
        loanId = LOAN_ID,
        ownerId = OWNER_ID,
        archived = true,
        archivedAtEpochSec = 3_000L,
        updatedAtEpochSec = 3_000L,
        updatedBy = "device-1"
    )

    companion object {
        private const val OWNER_ID = "owner-1"
        private const val LOAN_ID = "loan-1"
        private const val ACCOUNT_ID = "account-1"
    }
}
