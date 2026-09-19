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
import com.jcadenas.xpendz.domain.loan.aggregate.Outcome
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandEnvelope
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandType
import com.jcadenas.xpendz.domain.loan.commands.ReversePaymentCommand
import com.jcadenas.xpendz.domain.loan.reducer.DefaultLoanReducer
import com.jcadenas.xpendz.domain.loan.service.DefaultLoanAggregateService
import com.jcadenas.xpendz.domain.loan.service.error.LoanAggregateErrorCode
import com.jcadenas.xpendz.domain.loan.service.error.LoanAggregateException
import com.jcadenas.xpendz.infrastructure.loan.projection.room.DefaultLoanProjector
import com.jcadenas.xpendz.infrastructure.loan.replay.HistoricalLoanReplayTool
import com.jcadenas.xpendz.infrastructure.loan.room.RoomLoanAggregateExecutor
import com.jcadenas.xpendz.infrastructure.loan.room.RoomLoanRepositoryAdapter
import com.jcadenas.xpendz.sync.DeviceIdProvider
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sprint 7A — convergencia de reversión de pagos.
 * Cubre: identidad compartida docId↔eventId, limpieza completa de artefactos
 * de transporte tras revertir, idempotencia del reconciliador y no
 * resurrección del pago en replay histórico.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReversalConvergenceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var service: LoanApplicationService
    private lateinit var migration: LegacyLoanMigration
    private lateinit var replayTool: HistoricalLoanReplayTool
    private lateinit var reconciler: ReversedLoanPaymentReconciler
    private lateinit var deviceIdProvider: DeviceIdProvider
    private var databaseName: String = ""

    private val remoteDeletes = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "reversal-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()

        val canonicalDao = database.canonicalLoanDao()
        val projectionDao = database.loanProjectionDao()
        deviceIdProvider = DeviceIdProvider(context)

        val repository = RoomLoanRepositoryAdapter(database, canonicalDao)
        val aggregate = RoomLoanAggregateExecutor(database, DefaultLoanAggregateService(repository, DefaultLoanReducer()))
        service = LoanApplicationService(aggregate, DefaultLoanProjector(database, projectionDao))

        // Stores DAO-backed: misma lógica de localización por firma/txId que
        // los repositorios reales; los borrados remotos se registran para
        // poder asertar el docId eliminado.
        reconciler = ReversedLoanPaymentReconciler(
            canonicalDao,
            object : ReversedLoanPaymentStore {
                override suspend fun deleteByPaymentSignature(
                    userUid: String, loanId: String, accountId: String?,
                    principalCents: Long, occurredAtEpochSec: Long, linkedTransactionId: String?
                ): LoanPaymentEntity? {
                    val row = linkedTransactionId
                        ?.let { database.loanPaymentDao().getByLinkedTransactionId(it) }
                        ?: database.loanPaymentDao().getBySignature(
                            userUid, loanId, accountId, principalCents, occurredAtEpochSec
                        ) ?: return null
                    database.loanPaymentDao().delete(row.id)
                    remoteDeletes += "loanPayments/${row.id}"
                    return row
                }

                override suspend fun deleteFromFirestore(userUid: String, paymentId: String) {
                    remoteDeletes += "loanPayments/$paymentId"
                }
            },
            object : ReversedLoanMovementStore {
                override suspend fun deletePaymentBySignature(
                    userUid: String, loanId: String, accountId: String?,
                    amountCents: Long, occurredAtEpochSec: Long, linkedTransactionId: String?
                ): LoanMovementEntity? {
                    val row = linkedTransactionId
                        ?.let { database.loanMovementDao().getByLinkedTransactionId(it) }
                        ?: database.loanMovementDao().getPaymentBySignature(
                            userUid, loanId, accountId, amountCents, occurredAtEpochSec
                        ) ?: return null
                    database.loanMovementDao().delete(row.id)
                    remoteDeletes += "movements/${row.id}"
                    return row
                }
            },
            object : ReversedLoanTransactionStore {
                override suspend fun deleteFailedLoanTransaction(userUid: String, transactionId: String) {
                    database.transactionDao().delete(transactionId)
                    remoteDeletes += "transactions/$transactionId"
                }
            }
        )
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

    /**
     * El docId remoto (UUID del dispositivo origen) debe convertirse en el
     * eventId/operationId del journal local: es la identidad compartida que
     * permite que cualquier dispositivo elimine el documento correcto al
     * revertir.
     */
    @Test
    fun transportedPaymentDocIdBecomesCanonicalEventId() = runBlocking {
        val docId = "11111111-2222-4333-8444-555555555555"
        seedLoan()
        seedPayment(paymentId = docId, amountCents = 40_000L, transactionId = "tx-pay-1", occurredAt = 2_000L)
        seedTransaction("tx-pay-1", 40_000L, 2_000L)

        migration.migrate(OWNER_ID)

        val paymentEvent = database.canonicalLoanDao().getJournal(OWNER_ID, LOAN_ID)
            .single { it.eventType == "PAYMENT" }
        assertEquals(docId, paymentEvent.eventId)
        assertEquals(docId, paymentEvent.operationId)
        assertEquals(1, requireSummary().paymentCount)
    }

    /** Filas legacy con id no UUID derivan el operationId determinístico compartido. */
    @Test
    fun legacyPaymentIdDerivesDeterministicSharedEventId() = runBlocking {
        seedLoan()
        seedPayment(paymentId = "pay-legacy-1", amountCents = 40_000L, transactionId = "tx-pay-1", occurredAt = 2_000L)
        seedTransaction("tx-pay-1", 40_000L, 2_000L)

        migration.migrate(OWNER_ID)

        val expected = CanonicalLoanEventIds.deterministic(LOAN_ID, "PAYMENT", "pay:pay-legacy-1", 2_000L)
        val paymentEvent = database.canonicalLoanDao().getJournal(OWNER_ID, LOAN_ID)
            .single { it.eventType == "PAYMENT" }
        assertEquals(expected, paymentEvent.eventId)
        assertEquals(4, UUID.fromString(expected).version())
    }

    /**
     * Revertir elimina el pago, el movimiento y la transacción de transporte,
     * y el journal conserva PAYMENT + REVERSAL (historial intacto).
     */
    @Test
    fun reversalRemovesAllTransportArtifactsAndPreservesJournal() = runBlocking {
        val docId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        seedLoan()
        seedPayment(docId, 40_000L, "tx-pay-1", 2_000L)
        seedPaymentMovement("mov-pay-1", 40_000L, "tx-pay-1", 2_000L)
        seedTransaction("tx-pay-1", 40_000L, 2_000L)
        migration.migrate(OWNER_ID)
        assertEquals(1, requireSummary().paymentCount)

        // Con movimiento transportado el evento PAYMENT deriva del sourceKey
        // "mov:<id>" (identidad determinística compartida), no del docId.
        val paymentEventId = database.canonicalLoanDao().getJournal(OWNER_ID, LOAN_ID)
            .single { it.eventType == "PAYMENT" }.eventId

        val summary = requireSummary()
        val reversed = service.process(reverseCommand(UUID.randomUUID().toString(), summary.journalFingerprint, paymentEventId))
        assertEquals(Outcome.APPLIED, reversed.outcome)

        reconciler.reconcile(OWNER_ID)

        assertTrue(database.loanPaymentDao().getByUser(OWNER_ID).isEmpty())
        assertTrue(database.loanMovementDao().getByLoan(OWNER_ID, LOAN_ID).none { it.movementType != "CREATION" })
        assertNull(database.transactionDao().getById("tx-pay-1"))
        // El doc remoto se elimina por el id real de la fila (docId), aunque el
        // evento del journal derive del sourceKey "mov:".
        assertTrue("loanPayments/$docId" in remoteDeletes)
        assertTrue("movements/mov-pay-1" in remoteDeletes)
        assertTrue("transactions/tx-pay-1" in remoteDeletes)

        val after = requireSummary()
        assertEquals(0, after.paymentCount)
        assertEquals(0L, after.totalPaidCents)
        assertEquals(100_000L, after.pendingCents)

        val eventTypes = database.canonicalLoanDao().getJournal(OWNER_ID, LOAN_ID).map { it.eventType }
        assertEquals(listOf("CREATION", "PAYMENT", "REVERSAL"), eventTypes)
    }

    /**
     * Tras la limpieza, ni la migración ni un replay forzado reconstruyen el
     * pago revertido. El reconciliador es idempotente en syncs repetidos.
     */
    @Test
    fun replayDoesNotResurrectReversedPayment() = runBlocking {
        val docId = "99999999-8888-4777-8666-555555555555"
        seedLoan()
        seedPayment(docId, 40_000L, "tx-pay-1", 2_000L)
        seedTransaction("tx-pay-1", 40_000L, 2_000L)
        migration.migrate(OWNER_ID)

        val summary = requireSummary()
        service.process(reverseCommand(UUID.randomUUID().toString(), summary.journalFingerprint, docId))
        reconciler.reconcile(OWNER_ID)
        // Idempotencia: reconciliaciones y migraciones repetidas no cambian nada.
        reconciler.reconcile(OWNER_ID)
        migration.migrate(OWNER_ID)
        migration.migrate(OWNER_ID)

        // Replay histórico forzado: el transporte ya no contiene el pago.
        val replay = replayTool.replay(OWNER_ID, LOAN_ID)
        assertTrue(replay.success)

        val after = requireSummary()
        assertEquals(0, after.paymentCount)
        assertEquals(0L, after.totalPaidCents)
        assertEquals(100_000L, after.pendingCents)
        assertTrue(database.canonicalLoanDao().getJournal(OWNER_ID, LOAN_ID).none { it.eventType == "PAYMENT" })
    }

    /**
     * Sprint 7H — aunque la UI/guardia impidan la doble ejecución, la capa de
     * aplicación también rechaza un segundo ReversePaymentCommand sobre el
     * mismo pago: el journal conserva una única REVERSAL y el pending no se
     * restaura dos veces.
     */
    @Test
    fun secondReverseCommandForSamePaymentIsRejected() = runBlocking {
        seedLoan()
        seedPayment("pay-1", 40_000L, "tx-pay-1", 2_000L)
        seedTransaction("tx-pay-1", 40_000L, 2_000L)
        migration.migrate(OWNER_ID)

        val paymentEventId = database.canonicalLoanDao().getJournal(OWNER_ID, LOAN_ID)
            .single { it.eventType == "PAYMENT" }.eventId
        val first = service.process(
            reverseCommand(UUID.randomUUID().toString(), requireSummary().journalFingerprint, paymentEventId)
        )
        assertEquals(Outcome.APPLIED, first.outcome)

        // Segundo comando con fingerprint fresco: el aggregate lo rechaza.
        val error = try {
            service.process(
                reverseCommand(UUID.randomUUID().toString(), requireSummary().journalFingerprint, paymentEventId)
            )
            null
        } catch (e: LoanAggregateException) {
            e
        }
        assertEquals(LoanAggregateErrorCode.PAYMENT_ALREADY_REVERSED, error?.code)

        // Una sola reversión en el journal; pending restaurado una única vez.
        val eventTypes = database.canonicalLoanDao().getJournal(OWNER_ID, LOAN_ID).map { it.eventType }
        assertEquals(listOf("CREATION", "PAYMENT", "REVERSAL"), eventTypes)
        assertEquals(100_000L, requireSummary().pendingCents)
    }

    private fun reverseCommand(operationId: String, fingerprint: String, targetEventId: String) =
        ReversePaymentCommand(
            LoanCommandEnvelope(
                LoanCommandType.REVERSE_PAYMENT,
                operationId,
                LOAN_ID,
                OWNER_ID,
                fingerprint,
                9_000L,
                "actor-1",
                deviceIdProvider.get()
            ),
            targetEventId,
            "Error",
            null
        )

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

    private suspend fun seedLoan() {
        database.loanDao().insert(
            LoanEntity(
                id = LOAN_ID,
                userUid = OWNER_ID,
                type = "LENT",
                counterpartyName = "Counterparty",
                accountId = ACCOUNT_ID,
                currency = "COP",
                principalCents = 100_000L,
                status = "OPEN",
                notes = null,
                createdAtEpochSec = 1_000L,
                updatedAtEpochSec = 1_000L,
                updatedBy = REMOTE_DEVICE
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

    private suspend fun seedPaymentMovement(movementId: String, amountCents: Long, transactionId: String?, occurredAt: Long) {
        database.loanMovementDao().insert(
            LoanMovementEntity(
                id = movementId,
                userUid = OWNER_ID,
                loanId = LOAN_ID,
                movementType = "PAYMENT_IN",
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

    private suspend fun seedTransaction(transactionId: String, amountCents: Long, occurredAt: Long) {
        database.transactionDao().insert(
            TransactionEntity(
                id = transactionId,
                userUid = OWNER_ID,
                accountId = ACCOUNT_ID,
                categoryId = "cat-loan",
                kind = "LOAN_REPAYMENT_PRINCIPAL_IN",
                amountCents = amountCents,
                occurredAtEpochSec = occurredAt,
                note = null,
                createdAtEpochSec = occurredAt,
                updatedAtEpochSec = occurredAt,
                updatedBy = REMOTE_DEVICE
            )
        )
    }

    private fun requireSummary() = checkNotNull(database.loanProjectionDao().getSummaryProjection(OWNER_ID, LOAN_ID))

    companion object {
        private const val OWNER_ID = "owner-1"
        private const val LOAN_ID = "loan-1"
        private const val ACCOUNT_ID = "account-1"
        private const val REMOTE_DEVICE = "desktop-device"
    }
}
