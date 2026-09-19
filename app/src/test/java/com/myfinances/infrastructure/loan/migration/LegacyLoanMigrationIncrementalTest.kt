package com.jcadenas.xpendz.infrastructure.loan.migration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jcadenas.xpendz.application.loan.LoanApplicationService
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.data.local.entity.LoanEntity
import com.jcadenas.xpendz.data.local.entity.LoanMovementEntity
import com.jcadenas.xpendz.data.local.entity.LoanPaymentEntity
import com.jcadenas.xpendz.data.local.entity.TransactionEntity
import com.jcadenas.xpendz.domain.loan.reducer.DefaultLoanReducer
import com.jcadenas.xpendz.domain.loan.service.DefaultLoanAggregateService
import com.jcadenas.xpendz.infrastructure.loan.projection.room.DefaultLoanProjector
import com.jcadenas.xpendz.infrastructure.loan.projection.room.LoanProjectionDao
import com.jcadenas.xpendz.infrastructure.loan.replay.HistoricalLoanReplayTool
import com.jcadenas.xpendz.infrastructure.loan.room.CanonicalLoanDao
import com.jcadenas.xpendz.infrastructure.loan.room.RoomLoanAggregateExecutor
import com.jcadenas.xpendz.infrastructure.loan.room.RoomLoanRepositoryAdapter
import com.jcadenas.xpendz.sync.DeviceIdProvider
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyLoanMigrationIncrementalTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var canonicalLoanDao: CanonicalLoanDao
    private lateinit var loanProjectionDao: LoanProjectionDao
    private lateinit var loanApplicationService: LoanApplicationService
    private lateinit var migration: LegacyLoanMigration
    private lateinit var deviceIdProvider: DeviceIdProvider
    private var databaseName: String = ""

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "loan-migration-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_12_13)
            .build()
        canonicalLoanDao = database.canonicalLoanDao()
        loanProjectionDao = database.loanProjectionDao()
        deviceIdProvider = DeviceIdProvider(context)
        loanApplicationService = buildLoanApplicationService()
        migration = LegacyLoanMigration(
            loanDao = database.loanDao(),
            loanMovementDao = database.loanMovementDao(),
            loanPaymentDao = database.loanPaymentDao(),
            transactionDao = database.transactionDao(),
            canonicalLoanDao = canonicalLoanDao,
            loanProjectionDao = loanProjectionDao,
            deviceIdProvider = deviceIdProvider,
            loanApplicationService = loanApplicationService,
            paymentReconciler = HistoricalLoanPaymentReconciler(
                loanDao = database.loanDao(),
                loanPaymentDao = database.loanPaymentDao(),
                transactionDao = database.transactionDao(),
                canonicalLoanDao = canonicalLoanDao,
                loanApplicationService = loanApplicationService
            ),
            historicalLoanReplayTool = HistoricalLoanReplayTool(
                database = database,
                loanDao = database.loanDao(),
                loanMovementDao = database.loanMovementDao(),
                loanPaymentDao = database.loanPaymentDao(),
                transactionDao = database.transactionDao(),
                canonicalLoanDao = canonicalLoanDao,
                loanProjectionDao = loanProjectionDao,
                loanApplicationService = loanApplicationService
            )
        )
        seedSupportRows()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun principalChangeTriggersReplayAndConverges() = runBlocking {
        seedLoan(principalCents = 100_000L, status = "OPEN", counterparty = "Counterparty", notes = null, updatedBy = REMOTE_DEVICE)
        seedMovement(movementId = "mov-create-1", type = "CREATION", amountCents = 100_000L, transactionId = null, occurredAt = 1_000L)
        migration.migrate(OWNER_ID)
        val before = canonicalLoanDao.getJournal(OWNER_ID, LOAN_ID).map { it.eventId }
        assertEquals(100_000L, requireSummary().principalCents)

        updateLoan(principalCents = 150_000L, counterparty = "Counterparty", notes = null, status = "OPEN", updatedBy = REMOTE_DEVICE)
        migration.migrate(OWNER_ID)

        val summary = requireSummary()
        val after = canonicalLoanDao.getJournal(OWNER_ID, LOAN_ID).map { it.eventId }
        assertEquals(150_000L, summary.principalCents)
        assertEquals(150_000L, summary.pendingCents)
        assertEquals(2, after.size)
        assertEquals(false, before == after)
    }

    @Test
    fun metadataChangeTriggersReplay() = runBlocking {
        seedLoan(principalCents = 100_000L, status = "OPEN", counterparty = "Counterparty", notes = "Original", updatedBy = REMOTE_DEVICE)
        migration.migrate(OWNER_ID)

        updateLoan(principalCents = 100_000L, counterparty = "Nuevo Nombre", notes = "nota remota", status = "OPEN", updatedBy = REMOTE_DEVICE)
        migration.migrate(OWNER_ID)

        val summary = requireSummary()
        assertEquals("Nuevo Nombre", summary.counterparty)
        assertEquals("nota remota", summary.notes)
        assertEquals(1, canonicalLoanDao.getJournal(OWNER_ID, LOAN_ID).size)
    }

    @Test
    fun paymentChangeConverges() = runBlocking {
        seedLoan(principalCents = 100_000L, status = "OPEN", counterparty = "Counterparty", notes = null, updatedBy = REMOTE_DEVICE)
        migration.migrate(OWNER_ID)

        seedPayment(paymentId = "pay-1", amountCents = 40_000L, transactionId = "tx-pay-1", occurredAt = 2_000L)
        seedTransaction(transactionId = "tx-pay-1", kind = "LOAN_REPAYMENT_PRINCIPAL_IN", amountCents = 40_000L, occurredAt = 2_000L)
        migration.migrate(OWNER_ID)

        val summary = requireSummary()
        assertEquals(1, summary.paymentCount)
        assertEquals(40_000L, summary.totalPaidCents)
        assertEquals(60_000L, summary.pendingCents)
        assertEquals(2, canonicalLoanDao.getJournal(OWNER_ID, LOAN_ID).size)
    }

    @Test
    fun closeChangeConverges() = runBlocking {
        seedLoan(principalCents = 100_000L, status = "OPEN", counterparty = "Counterparty", notes = null, updatedBy = REMOTE_DEVICE)
        migration.migrate(OWNER_ID)

        seedPayment(paymentId = "pay-1", amountCents = 100_000L, transactionId = null, occurredAt = 2_000L)
        seedMovement(movementId = "mov-close-1", type = "CLOSE", amountCents = 0L, transactionId = null, occurredAt = 3_000L)
        updateLoan(principalCents = 100_000L, counterparty = "Counterparty", notes = null, status = "CLOSED", updatedBy = REMOTE_DEVICE)
        migration.migrate(OWNER_ID)

        val summary = requireSummary()
        assertEquals("CLOSED", summary.status)
        assertEquals(0L, summary.pendingCents)
        assertEquals(1, summary.paymentCount)
        assertEquals(3, canonicalLoanDao.getJournal(OWNER_ID, LOAN_ID).size)
    }

    @Test
    fun ignoresForeignLoanTransactionsOnSameAccount() = runBlocking {
        // Otros préstamos en la misma cuenta dejaron transacciones LOAN_* no
        // referenciadas: no deben entrar al timeline de este préstamo.
        seedLoan(principalCents = 100_000L, status = "CLOSED", counterparty = "Counterparty", notes = null, updatedBy = REMOTE_DEVICE)
        seedMovement(movementId = "mov-create-1", type = "CREATION", amountCents = 100_000L, transactionId = null, occurredAt = 1_000L)
        seedPayment(paymentId = "pay-1", amountCents = 100_000L, transactionId = "tx-pay-1", occurredAt = 2_000L)
        seedTransaction(transactionId = "tx-pay-1", kind = "LOAN_REPAYMENT_PRINCIPAL_IN", amountCents = 100_000L, occurredAt = 2_000L)
        seedTransaction(transactionId = "tx-foreign-1", kind = "LOAN_REPAYMENT_PRINCIPAL_IN", amountCents = 500_000L, occurredAt = 1_500L)
        seedTransaction(transactionId = "tx-foreign-2", kind = "LOAN_LENT_OUT", amountCents = 900_000L, occurredAt = 900L)
        migration.migrate(OWNER_ID)

        val summary = requireSummary()
        assertEquals("CLOSED", summary.status)
        assertEquals(1, summary.paymentCount)
        assertEquals(100_000L, summary.totalPaidCents)
        assertEquals(0L, summary.pendingCents)
    }

    @Test
    fun normalizesInferredCreationBeforeHistoricalPayment() = runBlocking {
        seedLoan(principalCents = 100_000L, status = "CLOSED", counterparty = "Counterparty", notes = null, updatedBy = REMOTE_DEVICE, occurredAt = 2_000L)
        seedPayment(paymentId = "pay-1", amountCents = 100_000L, transactionId = "tx-pay-1", occurredAt = 1_000L)
        seedTransaction(transactionId = "tx-pay-1", kind = "LOAN_REPAYMENT_PRINCIPAL_IN", amountCents = 100_000L, occurredAt = 1_000L)
        seedTransaction(transactionId = "tx-foreign-create", kind = "LOAN_LENT_OUT", amountCents = 50_000L, occurredAt = 500L)

        migration.migrate(OWNER_ID)
        migration.migrate(OWNER_ID)

        val summary = requireSummary()
        assertEquals("CLOSED", summary.status)
        assertEquals(100_000L, summary.totalPaidCents)
        assertEquals(1, summary.paymentCount)
        assertEquals(0L, summary.pendingCents)
    }

    @Test
    fun unchangedLoanIsNotReplayed() = runBlocking {
        seedLoan(principalCents = 100_000L, status = "OPEN", counterparty = "Counterparty", notes = null, updatedBy = REMOTE_DEVICE)
        migration.migrate(OWNER_ID)
        val before = canonicalLoanDao.getJournal(OWNER_ID, LOAN_ID).map { it.eventId }

        migration.migrate(OWNER_ID)

        val after = canonicalLoanDao.getJournal(OWNER_ID, LOAN_ID).map { it.eventId }
        assertEquals(before, after)
    }

    private fun buildLoanApplicationService(): LoanApplicationService {
        val repository = RoomLoanRepositoryAdapter(database, canonicalLoanDao)
        val aggregate = RoomLoanAggregateExecutor(database, DefaultLoanAggregateService(repository, DefaultLoanReducer()))
        val projector = DefaultLoanProjector(database, loanProjectionDao)
        return LoanApplicationService(aggregate, projector)
    }

    private fun seedSupportRows() {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO users (uid, email, created_at_epoch_sec, updated_at_epoch_sec) VALUES " +
                "('$OWNER_ID', 'test@example.com', 0, 0) " +
                "ON CONFLICT(uid) DO NOTHING"
        )
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO accounts (id, user_uid, name, type, currency, created_at_epoch_sec, updated_at_epoch_sec) VALUES " +
                "('$ACCOUNT_ID', '$OWNER_ID', 'Cuenta', 'CASH', 'COP', 0, 0)"
        )
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO categories (id, user_uid, name, kind, parent_id, created_at_epoch_sec, updated_at_epoch_sec) VALUES " +
                "('cat-loan', '$OWNER_ID', 'Préstamos', 'BOTH', NULL, 0, 0)"
        )
    }

    private suspend fun seedLoan(
        principalCents: Long,
        status: String,
        counterparty: String,
        notes: String?,
        updatedBy: String,
        occurredAt: Long = 1_000L
    ) {
        database.loanDao().insert(
            LoanEntity(
                id = LOAN_ID,
                userUid = OWNER_ID,
                type = "LENT",
                counterpartyName = counterparty,
                accountId = ACCOUNT_ID,
                currency = "COP",
                principalCents = principalCents,
                status = status,
                notes = notes,
                createdAtEpochSec = occurredAt,
                updatedAtEpochSec = occurredAt,
                updatedBy = updatedBy
            )
        )
    }

    private suspend fun updateLoan(
        principalCents: Long,
        counterparty: String,
        notes: String?,
        status: String,
        updatedBy: String
    ) {
        database.loanDao().update(
            LoanEntity(
                id = LOAN_ID,
                userUid = OWNER_ID,
                type = "LENT",
                counterpartyName = counterparty,
                accountId = ACCOUNT_ID,
                currency = "COP",
                principalCents = principalCents,
                status = status,
                notes = notes,
                createdAtEpochSec = 1_000L,
                updatedAtEpochSec = 9_999L,
                updatedBy = updatedBy
            )
        )
    }

    private suspend fun seedPayment(paymentId: String, amountCents: Long, transactionId: String?, occurredAt: Long) {
        database.loanPaymentDao().insert(
            LoanPaymentEntity(
                id = paymentId,
                userUid = OWNER_ID,
                loanId = LOAN_ID,
                accountId = ACCOUNT_ID,
                principalCents = amountCents,
                occurredAtEpochSec = occurredAt,
                note = null,
                linkedTransactionId = transactionId,
                createdAtEpochSec = occurredAt,
                updatedAtEpochSec = occurredAt,
                updatedBy = REMOTE_DEVICE
            )
        )
    }

    private suspend fun seedMovement(movementId: String, type: String, amountCents: Long, transactionId: String?, occurredAt: Long) {
        database.loanMovementDao().insert(
            LoanMovementEntity(
                id = movementId,
                userUid = OWNER_ID,
                loanId = LOAN_ID,
                movementType = type,
                amountCents = amountCents,
                accountId = ACCOUNT_ID,
                linkedTransactionId = transactionId,
                note = null,
                occurredAtEpochSec = occurredAt,
                createdAtEpochSec = occurredAt,
                updatedAtEpochSec = occurredAt,
                updatedBy = REMOTE_DEVICE
            )
        )
    }

    private suspend fun seedTransaction(transactionId: String, kind: String, amountCents: Long, occurredAt: Long) {
        database.transactionDao().insert(
            TransactionEntity(
                id = transactionId,
                userUid = OWNER_ID,
                accountId = ACCOUNT_ID,
                categoryId = "cat-loan",
                kind = kind,
                amountCents = amountCents,
                occurredAtEpochSec = occurredAt,
                note = null,
                createdAtEpochSec = occurredAt,
                updatedAtEpochSec = occurredAt,
                updatedBy = REMOTE_DEVICE
            )
        )
    }

    private fun requireSummary() = checkNotNull(loanProjectionDao.getSummaryProjection(OWNER_ID, LOAN_ID))

    companion object {
        private const val OWNER_ID = "owner-1"
        private const val LOAN_ID = "loan-1"
        private const val ACCOUNT_ID = "account-1"
        private const val REMOTE_DEVICE = "android-device"
    }
}
