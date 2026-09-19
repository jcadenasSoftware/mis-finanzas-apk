package com.jcadenas.xpendz.infrastructure.loan.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jcadenas.xpendz.data.local.AppDatabase
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
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.reducer.DefaultLoanReducer
import com.jcadenas.xpendz.domain.loan.service.DefaultLoanAggregateService
import com.jcadenas.xpendz.domain.loan.service.LoanAggregateService
import com.jcadenas.xpendz.domain.loan.service.error.UnexpectedFailure
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomLoanRepositoryIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomLoanRepositoryAdapter
    private lateinit var executor: LoanAggregateService
    private lateinit var databaseName: String
    private var operationSequence = 1

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "loan-${UUID.randomUUID()}.db"
        database = openDatabase()
        configure(database)
        operationSequence = 1
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun persistsCompleteAggregateFlowAndReplaysAfterRestart() {
        val created = executor.process(create(nextOperationId()))
        val originalPayment = payment(nextOperationId(), created.currentSnapshot.journalFingerprint, 40_000)
        val paid = executor.process(originalPayment)

        database.close()
        database = openDatabase()
        configure(database)
        val replay = executor.process(originalPayment)

        assertEquals(Outcome.REPLAYED, replay.outcome)
        assertEquals(2, repository.getJournal(OWNER_ID, LOAN_ID).size)
        assertEquals(paid.currentSnapshot, replay.currentSnapshot)

        val reversed = executor.process(
            reversal(nextOperationId(), fingerprint(), originalPayment.envelope.operationId)
        )
        val toppedUp = executor.process(
            topup(nextOperationId(), reversed.currentSnapshot.journalFingerprint, 20_000)
        )
        val adjusted = executor.process(
            adjustment(nextOperationId(), toppedUp.currentSnapshot.journalFingerprint, -20_000)
        )
        val metadata = executor.process(
            metadata(nextOperationId(), adjusted.currentSnapshot.journalFingerprint)
        )
        val finalPayment = executor.process(
            payment(nextOperationId(), metadata.currentSnapshot.journalFingerprint, 100_000)
        )
        val closed = executor.process(
            close(nextOperationId(), finalPayment.currentSnapshot.journalFingerprint)
        )

        val journal = repository.getJournal(OWNER_ID, LOAN_ID)
        val persistedSnapshot = repository.loadSnapshot(OWNER_ID, LOAN_ID)!!
        val rebuilt = DefaultLoanReducer().reduceCanonical(journal)

        assertEquals(8, journal.size)
        assertTrue(journal.all { it.eventId == it.operationId })
        assertEquals(closed.currentSnapshot, persistedSnapshot)
        assertEquals(persistedSnapshot, rebuilt.snapshot)
        assertEquals(persistedSnapshot.journalFingerprint, rebuilt.snapshot!!.journalFingerprint)
        assertEquals(LoanStatus.CLOSED, persistedSnapshot.status)
        assertEquals("Ana Pérez", persistedSnapshot.counterpartyName)
        assertEquals("A2", persistedSnapshot.defaultAccountId)
        assertEquals("Acuerdo actualizado", persistedSnapshot.notes)
    }

    @Test
    fun rollsBackEventWhenSnapshotPersistenceFails() {
        val created = executor.process(create(nextOperationId()))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_loan_snapshot BEFORE INSERT ON loan_snapshots_v1 " +
                "BEGIN SELECT RAISE(ABORT, 'snapshot failure'); END"
        )

        assertThrows(UnexpectedFailure::class.java) {
            executor.process(topup(nextOperationId(), created.currentSnapshot.journalFingerprint, 10_000))
        }

        assertEquals(1, repository.getJournal(OWNER_ID, LOAN_ID).size)
        assertEquals(created.currentSnapshot, repository.loadSnapshot(OWNER_ID, LOAN_ID))
    }

    @Test
    fun migrationCreatesUsableCanonicalTables() {
        val writableDatabase = database.openHelper.writableDatabase
        writableDatabase.execSQL("DROP TABLE loan_journal_v1")
        writableDatabase.execSQL("DROP TABLE loan_snapshots_v1")
        AppDatabase.MIGRATION_12_13.migrate(writableDatabase)
        configure(database)

        val created = executor.process(create(nextOperationId()))

        assertEquals(1, repository.getJournal(OWNER_ID, LOAN_ID).size)
        assertEquals(created.currentSnapshot, repository.loadSnapshot(OWNER_ID, LOAN_ID))
    }

    @Test
    fun mapperRoundTripPreservesEveryPersistedEventField() {
        val created = executor.process(create(nextOperationId()))
        val original = created.event
        val restored = repository.findByEventId(OWNER_ID, original.eventId)

        assertEquals(original, restored)
        assertEquals(created.currentSnapshot, repository.loadSnapshot(OWNER_ID, LOAN_ID))
        assertEquals(original, repository.findByOperationId(OWNER_ID, original.operationId))
    }

    private fun openDatabase(): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        databaseName
    ).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_12_13).build()

    private fun configure(newDatabase: AppDatabase) {
        repository = RoomLoanRepositoryAdapter(newDatabase, newDatabase.canonicalLoanDao())
        executor = RoomLoanAggregateExecutor(
            newDatabase,
            DefaultLoanAggregateService(repository, DefaultLoanReducer())
        )
    }

    private fun fingerprint(): String = repository.loadSnapshot(OWNER_ID, LOAN_ID)!!.journalFingerprint

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

    private fun payment(operationId: String, fingerprint: String, amount: Long) =
        RegisterPaymentCommand(
            envelope(LoanCommandType.REGISTER_PAYMENT, operationId, fingerprint, 2_000L + operationSequence),
            amount,
            "A1",
            "tx-$operationId",
            "Pago"
        )

    private fun reversal(operationId: String, fingerprint: String, target: String) =
        ReversePaymentCommand(
            envelope(LoanCommandType.REVERSE_PAYMENT, operationId, fingerprint, 3_000L + operationSequence),
            target,
            "Error",
            null
        )

    private fun topup(operationId: String, fingerprint: String, amount: Long) =
        AddPrincipalCommand(
            envelope(LoanCommandType.ADD_PRINCIPAL, operationId, fingerprint, 4_000L + operationSequence),
            amount,
            "A1",
            "tx-$operationId",
            "Capital"
        )

    private fun adjustment(operationId: String, fingerprint: String, delta: Long) =
        AdjustPrincipalCommand(
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
        FieldChange(true, "A2"),
        FieldChange(true, "Acuerdo actualizado")
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
