package com.jcadenas.xpendz.infrastructure.loan.migration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jcadenas.xpendz.application.loan.LoanApplicationService
import com.jcadenas.xpendz.application.loan.LoanCommandFactory
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.data.local.entity.TransactionEntity
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.reducer.DefaultLoanReducer
import com.jcadenas.xpendz.domain.loan.service.DefaultLoanAggregateService
import com.jcadenas.xpendz.infrastructure.loan.projection.room.DefaultLoanProjector
import com.jcadenas.xpendz.infrastructure.loan.replay.HistoricalLoanReplayTool
import com.jcadenas.xpendz.infrastructure.loan.room.RoomLoanAggregateExecutor
import com.jcadenas.xpendz.infrastructure.loan.room.RoomLoanRepositoryAdapter
import com.jcadenas.xpendz.sync.DeviceIdProvider
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sprint 7C — simetría financiera Android ↔ Desktop.
 * Verifica que crear un préstamo y ajustar el principal lleven el
 * transactionId de la transacción financiera hasta el journal, y que la
 * migración/replay no recree ni duplique transacciones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoanFinancialSymmetryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var service: LoanApplicationService
    private lateinit var migration: LegacyLoanMigration
    private lateinit var replayTool: HistoricalLoanReplayTool
    private lateinit var factory: LoanCommandFactory
    private var databaseName: String = ""

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "symmetry-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()

        val canonicalDao = database.canonicalLoanDao()
        val projectionDao = database.loanProjectionDao()
        val deviceIdProvider = DeviceIdProvider(context)

        val repository = RoomLoanRepositoryAdapter(database, canonicalDao)
        val aggregate = RoomLoanAggregateExecutor(
            database, DefaultLoanAggregateService(repository, DefaultLoanReducer())
        )
        service = LoanApplicationService(aggregate, DefaultLoanProjector(database, projectionDao))
        factory = LoanCommandFactory()
        replayTool = HistoricalLoanReplayTool(
            database = database,
            loanDao = database.loanDao(),
            loanMovementDao = database.loanMovementDao(),
            loanPaymentDao = database.loanPaymentDao(),
            transactionDao = database.transactionDao(),
            canonicalLoanDao = canonicalDao,
            loanProjectionDao = projectionDao,
            loanApplicationService = service
        )
        migration = LegacyLoanMigration(
            loanDao = database.loanDao(),
            loanMovementDao = database.loanMovementDao(),
            loanPaymentDao = database.loanPaymentDao(),
            transactionDao = database.transactionDao(),
            canonicalLoanDao = canonicalDao,
            loanProjectionDao = projectionDao,
            deviceIdProvider = deviceIdProvider,
            loanApplicationService = service,
            paymentReconciler = HistoricalLoanPaymentReconciler(
                loanDao = database.loanDao(),
                loanPaymentDao = database.loanPaymentDao(),
                transactionDao = database.transactionDao(),
                canonicalLoanDao = canonicalDao,
                loanApplicationService = service
            ),
            historicalLoanReplayTool = replayTool
        )
        seedSupportRows()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun createLoanCarriesTransactionIdToJournal() = runBlocking {
        seedTransaction("tx-fund", "INCOME", 500_000L, 500L)
        seedTransaction("tx-create-1", "LOAN_LENT_OUT", 100_000L, 1_000L)

        val command = factory.createLoan(
            ownerId = OWNER_ID,
            loanType = LoanType.LENT,
            counterpartyName = "Ana",
            currency = "COP",
            defaultAccountId = ACCOUNT_ID,
            initialPrincipalCents = 100_000L,
            occurredAt = 1_000L,
            transactionId = "tx-create-1",
            notes = null
        )
        val result = service.process(command)

        val loanId = command.envelope.loanId
        val creation = database.canonicalLoanDao().getJournal(OWNER_ID, loanId)
            .single { it.eventType == "CREATION" }
        assertEquals("tx-create-1", creation.transactionId)
        assertEquals(100_000L, result.currentSnapshot.principalCents)
        // La transacción existe y el saldo refleja la salida inmediata.
        assertNotNull(database.transactionDao().getById("tx-create-1"))
        assertEquals(400_000L, database.accountDao().computeBalanceCents(OWNER_ID, ACCOUNT_ID))
    }

    @Test
    fun adjustPrincipalCarriesTransactionIdToJournal() = runBlocking {
        seedTransaction("tx-fund", "INCOME", 500_000L, 500L)
        seedTransaction("tx-create-1", "LOAN_LENT_OUT", 100_000L, 1_000L)
        val create = factory.createLoan(
            ownerId = OWNER_ID, loanType = LoanType.LENT, counterpartyName = "Ana",
            currency = "COP", defaultAccountId = ACCOUNT_ID,
            initialPrincipalCents = 100_000L, occurredAt = 1_000L,
            transactionId = "tx-create-1", notes = null
        )
        val created = service.process(create)
        val loanId = create.envelope.loanId

        // Aumento de principal en préstamo otorgado → salida de dinero.
        seedTransaction("tx-adj-1", "LOAN_LENT_CORRECTION_OUT", 50_000L, 2_000L)
        val adjust = factory.adjustPrincipal(
            ownerId = OWNER_ID, loanId = loanId,
            expectedJournalFingerprint = created.currentSnapshot.journalFingerprint,
            deltaCents = 50_000L, reason = "Ajuste de monto",
            accountId = ACCOUNT_ID, transactionId = "tx-adj-1", note = null
        )
        service.process(adjust)

        val adjustment = database.canonicalLoanDao().getJournal(OWNER_ID, loanId)
            .single { it.eventType == "ADJUSTMENT" }
        assertEquals("tx-adj-1", adjustment.transactionId)
        assertEquals(350_000L, database.accountDao().computeBalanceCents(OWNER_ID, ACCOUNT_ID))

        val summary = database.loanProjectionDao().getSummaryProjection(OWNER_ID, loanId)!!
        assertEquals(150_000L, summary.principalCents)
        assertEquals(150_000L, summary.pendingCents)
    }

    @Test
    fun topUpLentCarriesTransactionIdToJournal() = runBlocking {
        seedTransaction("tx-fund", "INCOME", 500_000L, 500L)
        seedTransaction("tx-create-1", "LOAN_LENT_OUT", 100_000L, 1_000L)
        val create = factory.createLoan(
            ownerId = OWNER_ID, loanType = LoanType.LENT, counterpartyName = "Ana",
            currency = "COP", defaultAccountId = ACCOUNT_ID,
            initialPrincipalCents = 100_000L, occurredAt = 1_000L,
            transactionId = "tx-create-1", notes = null
        )
        val created = service.process(create)
        val loanId = create.envelope.loanId

        // Top-up en préstamo otorgado → misma salida que Desktop (LOAN_LENT_TOPUP).
        seedTransaction("tx-topup-1", "LOAN_LENT_TOPUP", 50_000L, 2_000L)
        val topUp = factory.addPrincipal(
            ownerId = OWNER_ID, loanId = loanId,
            expectedJournalFingerprint = created.currentSnapshot.journalFingerprint,
            amountCents = 50_000L, accountId = ACCOUNT_ID,
            transactionId = "tx-topup-1", note = "Segunda entrega"
        )
        val result = service.process(topUp)

        val topUpEvent = database.canonicalLoanDao().getJournal(OWNER_ID, loanId)
            .single { it.eventType == "TOPUP" }
        assertEquals("tx-topup-1", topUpEvent.transactionId)
        assertEquals(150_000L, result.currentSnapshot.principalCents)
        assertEquals(350_000L, database.accountDao().computeBalanceCents(OWNER_ID, ACCOUNT_ID))

        val summary = database.loanProjectionDao().getSummaryProjection(OWNER_ID, loanId)!!
        assertEquals(150_000L, summary.principalCents)
        assertEquals(150_000L, summary.pendingCents)
    }

    @Test
    fun topUpBorrowedCarriesTransactionIdToJournal() = runBlocking {
        seedTransaction("tx-fund", "INCOME", 500_000L, 500L)
        seedTransaction("tx-create-1", "LOAN_BORROWED_IN", 100_000L, 1_000L)
        val create = factory.createLoan(
            ownerId = OWNER_ID, loanType = LoanType.BORROWED, counterpartyName = "Ana",
            currency = "COP", defaultAccountId = ACCOUNT_ID,
            initialPrincipalCents = 100_000L, occurredAt = 1_000L,
            transactionId = "tx-create-1", notes = null
        )
        val created = service.process(create)
        val loanId = create.envelope.loanId

        // Top-up en préstamo recibido → entrada de dinero (LOAN_BORROWED_TOPUP).
        seedTransaction("tx-topup-1", "LOAN_BORROWED_TOPUP", 50_000L, 2_000L)
        val topUp = factory.addPrincipal(
            ownerId = OWNER_ID, loanId = loanId,
            expectedJournalFingerprint = created.currentSnapshot.journalFingerprint,
            amountCents = 50_000L, accountId = ACCOUNT_ID,
            transactionId = "tx-topup-1", note = null
        )
        val result = service.process(topUp)

        val topUpEvent = database.canonicalLoanDao().getJournal(OWNER_ID, loanId)
            .single { it.eventType == "TOPUP" }
        assertEquals("tx-topup-1", topUpEvent.transactionId)
        assertEquals(150_000L, result.currentSnapshot.principalCents)
        assertEquals(650_000L, database.accountDao().computeBalanceCents(OWNER_ID, ACCOUNT_ID))

        val summary = database.loanProjectionDao().getSummaryProjection(OWNER_ID, loanId)!!
        assertEquals(150_000L, summary.principalCents)
        assertEquals(150_000L, summary.pendingCents)
    }

    @Test
    fun migrationAndReplayDoNotDuplicateLinkedTransactions() = runBlocking {
        seedTransaction("tx-fund", "INCOME", 500_000L, 500L)
        seedTransaction("tx-create-1", "LOAN_LENT_OUT", 100_000L, 1_000L)
        val create = factory.createLoan(
            ownerId = OWNER_ID, loanType = LoanType.LENT, counterpartyName = "Ana",
            currency = "COP", defaultAccountId = ACCOUNT_ID,
            initialPrincipalCents = 100_000L, occurredAt = 1_000L,
            transactionId = "tx-create-1", notes = null
        )
        service.process(create)
        val loanId = create.envelope.loanId
        // El replay histórico reconstruye desde el transporte: simular la fila
        // `loans` que produce el pull de Firestore para el mismo préstamo.
        seedTransportLoan(loanId)

        // Migración y replay repetidos: sin drift, sin duplicados.
        migration.migrate(OWNER_ID)
        migration.migrate(OWNER_ID)
        val replay = replayTool.replay(OWNER_ID, loanId)

        assertTrue(replay.success)
        val journal = database.canonicalLoanDao().getJournal(OWNER_ID, loanId)
        assertEquals(1, journal.size)
        assertEquals("CREATION", journal.single().eventType)
        val txs = database.transactionDao().getByUser(OWNER_ID)
        assertEquals(setOf("tx-fund", "tx-create-1"), txs.map { it.id }.toSet())
        assertEquals(400_000L, database.accountDao().computeBalanceCents(OWNER_ID, ACCOUNT_ID))
    }

    private fun seedSupportRows() {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO users (uid, email, created_at_epoch_sec, updated_at_epoch_sec) VALUES " +
                "('$OWNER_ID', 'test@example.com', 0, 0) ON CONFLICT(uid) DO NOTHING"
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

    private suspend fun seedTransportLoan(loanId: String) {
        database.loanDao().insert(
            com.jcadenas.xpendz.data.local.entity.LoanEntity(
                id = loanId,
                userUid = OWNER_ID,
                type = "LENT",
                counterpartyName = "Ana",
                accountId = ACCOUNT_ID,
                currency = "COP",
                principalCents = 100_000L,
                status = "OPEN",
                notes = null,
                createdAtEpochSec = 1_000L,
                updatedAtEpochSec = 1_000L,
                updatedBy = "android-device"
            )
        )
    }

    private suspend fun seedTransaction(
        transactionId: String, kind: String, amountCents: Long, occurredAt: Long
    ) {
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
                updatedBy = "android-device"
            )
        )
    }

    companion object {
        private const val OWNER_ID = "owner-1"
        private const val ACCOUNT_ID = "account-1"
    }
}
