package com.jcadenas.xpendz.infrastructure.loan.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.domain.loan.aggregate.LoanCommandResult
import com.jcadenas.xpendz.domain.loan.commands.AddPrincipalCommand
import com.jcadenas.xpendz.domain.loan.commands.CreateLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.FieldChange
import com.jcadenas.xpendz.domain.loan.commands.LoanCommand
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandEnvelope
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandType
import com.jcadenas.xpendz.domain.loan.commands.RegisterPaymentCommand
import com.jcadenas.xpendz.domain.loan.commands.UpdateMetadataCommand
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.reducer.DefaultLoanReducer
import com.jcadenas.xpendz.domain.loan.repository.FakeLoanRepository
import com.jcadenas.xpendz.domain.loan.service.DefaultLoanAggregateService
import com.jcadenas.xpendz.domain.loan.service.LoanAggregateService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FakeRepositoryCompatibilityTest {
    private lateinit var database: AppDatabase
    private lateinit var fakeRepository: FakeLoanRepository
    private lateinit var roomRepository: RoomLoanRepositoryAdapter
    private lateinit var fakeService: LoanAggregateService
    private lateinit var roomService: LoanAggregateService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeRepository = FakeLoanRepository()
        roomRepository = RoomLoanRepositoryAdapter(database, database.canonicalLoanDao())
        fakeService = DefaultLoanAggregateService(fakeRepository, DefaultLoanReducer())
        roomService = RoomLoanAggregateExecutor(
            database,
            DefaultLoanAggregateService(roomRepository, DefaultLoanReducer())
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun fakeAndRoomAdapterHaveIdenticalObservableBehavior() {
        val create = CreateLoanCommand(
            envelope(LoanCommandType.CREATE_LOAN, operation(1), null, 1_000),
            LoanType.LENT,
            100_000,
            "Ana",
            "USD",
            "A1",
            "tx-create",
            null
        )
        assertEquivalent(create)

        var fingerprint = fakeRepository.loadSnapshot(OWNER_ID, LOAN_ID)!!.journalFingerprint
        val payment = RegisterPaymentCommand(
            envelope(LoanCommandType.REGISTER_PAYMENT, operation(2), fingerprint, 2_000),
            20_000,
            "A1",
            "tx-payment",
            null
        )
        assertEquivalent(payment)

        fingerprint = fakeRepository.loadSnapshot(OWNER_ID, LOAN_ID)!!.journalFingerprint
        val topup = AddPrincipalCommand(
            envelope(LoanCommandType.ADD_PRINCIPAL, operation(3), fingerprint, 3_000),
            10_000,
            "A1",
            "tx-topup",
            null
        )
        assertEquivalent(topup)

        fingerprint = fakeRepository.loadSnapshot(OWNER_ID, LOAN_ID)!!.journalFingerprint
        val metadata = UpdateMetadataCommand(
            envelope(LoanCommandType.UPDATE_METADATA, operation(4), fingerprint, 4_000),
            FieldChange(true, "Ana Pérez"),
            FieldChange(false, null),
            FieldChange(true, "Actualizado")
        )
        assertEquivalent(metadata)

        assertEquals(
            fakeRepository.getJournal(OWNER_ID, LOAN_ID),
            roomRepository.getJournal(OWNER_ID, LOAN_ID)
        )
        assertEquals(
            fakeRepository.loadSnapshot(OWNER_ID, LOAN_ID),
            roomRepository.loadSnapshot(OWNER_ID, LOAN_ID)
        )
    }

    private fun assertEquivalent(command: LoanCommand) {
        val expected: LoanCommandResult = fakeService.process(command)
        val actual: LoanCommandResult = roomService.process(command)
        assertEquals(expected, actual)
    }

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

    private fun operation(value: Int): String =
        "00000000-0000-4000-8000-${value.toString().padStart(12, '0')}"

    companion object {
        private const val OWNER_ID = "owner-1"
        private const val LOAN_ID = "loan-1"
    }
}
