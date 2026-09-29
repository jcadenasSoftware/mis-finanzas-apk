package com.jcadenas.xpendz.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.data.local.entity.CategoryEntity
import com.jcadenas.xpendz.data.local.entity.ObligationEntity
import com.jcadenas.xpendz.data.local.entity.ObligationSettlementEntity
import com.jcadenas.xpendz.data.local.entity.TransactionEntity
import com.jcadenas.xpendz.data.local.entity.UserEntity
import com.jcadenas.xpendz.sync.DeviceIdProvider
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ObligationRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var databaseName: String
    private lateinit var obligationRepository: ObligationRepository
    private lateinit var settlementRepository: ObligationSettlementRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "obligations-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName
        ).allowMainThreadQueries().build()
        val deviceIdProvider = DeviceIdProvider(context)
        val prefs = context.getSharedPreferences("obligation-repo-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        obligationRepository = ObligationRepository(
            database.obligationDao(),
            database.obligationSettlementDao(),
            FakeObligationRemoteStore(),
            deviceIdProvider,
            prefs
        )
        settlementRepository = ObligationSettlementRepository(
            database.obligationSettlementDao(),
            database.obligationDao(),
            database.transactionDao(),
            FakeObligationSettlementRemoteStore(),
            deviceIdProvider,
            prefs
        )

        database.userDao().upsert(UserEntity(UID, "user@test.dev", NOW, NOW))
        database.accountDao().insert(account())
        database.categoryDao().insert(category(CATEGORY_ID, "Ventas", "INCOME"))
        database.categoryDao().insert(category(OBLIGATION_CATEGORY_ID, "Clientes", "BOTH"))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun createAndCancelObligationPersistWithoutCreatingTransactions() = runBlocking {
        val created = obligationRepository.create(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Factura pendiente",
            counterpartyName = "Pedro",
            currency = "COP",
            originalAmountCents = 500_000L,
            issuedAtEpochSec = NOW,
            dueAtEpochSec = NOW + 86_400L,
            obligationCategoryId = OBLIGATION_CATEGORY_ID,
            reference = "FAC-001",
            notes = "Venta a crédito"
        )

        assertEquals(0, database.transactionDao().getByUser(UID).size)
        assertEquals(OBLIGATION_CATEGORY_ID, database.obligationDao().getById(created.id)?.obligationCategoryId)

        val cancelled = obligationRepository.cancel(created.id, NOW + 10L)

        assertEquals(NOW + 10L, cancelled.cancelledAtEpochSec)
        assertNotNull(database.obligationDao().getById(created.id)?.cancelledAtEpochSec)
        assertEquals(0, database.transactionDao().getByUser(UID).size)
    }

    @Test
    fun settlementQueriesAreStrictlyLinkedToExistingTransaction() = runBlocking {
        val obligation = obligationRepository.create(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro parcial",
            counterpartyName = "Pedro",
            currency = "COP",
            originalAmountCents = 500_000L,
            issuedAtEpochSec = NOW
        )
        val transaction = transaction("tx-1", 200_000L)
        database.transactionDao().insert(transaction)

        val settlement = settlement(
            id = "settlement-1",
            obligationId = obligation.id,
            amountCents = 200_000L,
            occurredAtEpochSec = NOW + 1L,
            linkedTransactionId = transaction.id,
            note = "Abono 1"
        )
        settlementRepository.insertDirect(settlement)

        assertEquals(200_000L, settlementRepository.getTotalSettledCents(obligation.id))
        assertEquals(settlement.id, settlementRepository.getByLinkedTransactionId(transaction.id)?.id)
        assertEquals(1, settlementRepository.getByObligation(obligation.id).size)
    }

    @Test
    fun settlementRejectsDuplicateLinkedTransactionId() {
        runBlocking {
            val obligation = obligationRepository.create(
                userUid = UID,
                type = ObligationEntity.TYPE_PAYABLE,
                title = "Servicio pendiente",
                counterpartyName = "Proveedor",
                currency = "COP",
                originalAmountCents = 300_000L,
                issuedAtEpochSec = NOW
            )
            database.transactionDao().insert(transaction("tx-dup", 100_000L))

            settlementRepository.insertDirect(
                settlement(
                    id = "settlement-dup-1",
                    obligationId = obligation.id,
                    amountCents = 100_000L,
                    occurredAtEpochSec = NOW + 1L,
                    linkedTransactionId = "tx-dup"
                )
            )

            assertThrows(Exception::class.java) {
                runBlocking {
                    settlementRepository.insertDirect(
                        settlement(
                            id = "settlement-dup-2",
                            obligationId = obligation.id,
                            amountCents = 50_000L,
                            occurredAtEpochSec = NOW + 2L,
                            linkedTransactionId = "tx-dup"
                        )
                    )
                }
            }
        }
    }

    @Test
    fun settlementRejectsMissingLinkedTransaction() {
        runBlocking {
            val obligation = obligationRepository.create(
                userUid = UID,
                type = ObligationEntity.TYPE_RECEIVABLE,
                title = "Cobro inválido",
                counterpartyName = "Pedro",
                currency = "COP",
                originalAmountCents = 120_000L,
                issuedAtEpochSec = NOW
            )

            assertThrows(Exception::class.java) {
                runBlocking {
                    settlementRepository.insertDirect(
                        settlement(
                            id = "settlement-missing-tx",
                            obligationId = obligation.id,
                            amountCents = 50_000L,
                            occurredAtEpochSec = NOW + 3L,
                            linkedTransactionId = "tx-missing"
                        )
                    )
                }
            }
        }
    }

    private fun account() = AccountEntity(
        id = ACCOUNT_ID,
        userUid = UID,
        name = "Banco",
        type = "BANK",
        currency = "COP",
        createdAtEpochSec = NOW,
        updatedAtEpochSec = NOW,
        updatedBy = "test-device"
    )

    private fun category(id: String, name: String, kind: String) = CategoryEntity(
        id = id,
        userUid = UID,
        name = name,
        kind = kind,
        parentId = null,
        createdAtEpochSec = NOW,
        updatedAtEpochSec = NOW,
        updatedBy = "test-device"
    )

    private fun transaction(id: String, amountCents: Long, categoryId: String = CATEGORY_ID) = TransactionEntity(
        id = id,
        userUid = UID,
        accountId = ACCOUNT_ID,
        categoryId = categoryId,
        kind = "INCOME",
        amountCents = amountCents,
        occurredAtEpochSec = NOW,
        note = "Movimiento asociado",
        createdAtEpochSec = NOW,
        updatedAtEpochSec = NOW,
        updatedBy = "test-device"
    )

    private fun settlement(
        id: String,
        obligationId: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        linkedTransactionId: String,
        note: String? = null
    ) = ObligationSettlementEntity(
        id = id,
        userUid = UID,
        obligationId = obligationId,
        accountId = ACCOUNT_ID,
        amountCents = amountCents,
        occurredAtEpochSec = occurredAtEpochSec,
        linkedTransactionId = linkedTransactionId,
        note = note,
        createdAtEpochSec = NOW,
        updatedAtEpochSec = NOW,
        updatedBy = "test-device"
    )

    companion object {
        private const val UID = "obligation-user"
        private const val ACCOUNT_ID = "account-1"
        private const val CATEGORY_ID = "category-1"
        private const val OBLIGATION_CATEGORY_ID = "category-2"
        private const val NOW = 1_700_000_000L
    }
}
