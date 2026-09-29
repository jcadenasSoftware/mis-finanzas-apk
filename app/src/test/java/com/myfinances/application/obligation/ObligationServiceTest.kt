package com.jcadenas.xpendz.application.obligation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.data.local.entity.CategoryEntity
import com.jcadenas.xpendz.data.local.entity.ObligationEntity
import com.jcadenas.xpendz.data.local.entity.TransactionEntity
import com.jcadenas.xpendz.data.local.entity.UserEntity
import com.jcadenas.xpendz.data.repository.FakeObligationRemoteStore
import com.jcadenas.xpendz.data.repository.FakeObligationSettlementRemoteStore
import com.jcadenas.xpendz.data.repository.ObligationRepository
import com.jcadenas.xpendz.data.repository.ObligationSettlementRepository
import com.jcadenas.xpendz.sync.DeviceIdProvider
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ObligationServiceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var databaseName: String
    private lateinit var service: ObligationService

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "o.db"
        database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName
        ).allowMainThreadQueries().build()
        val deviceIdProvider = DeviceIdProvider(context)
        val prefs = context.getSharedPreferences("obligation-service-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        service = ObligationService(
            database = database,
            obligationRepository = ObligationRepository(
                database.obligationDao(),
                database.obligationSettlementDao(),
                FakeObligationRemoteStore(),
                deviceIdProvider,
                prefs
            ),
            obligationSettlementRepository = ObligationSettlementRepository(
                database.obligationSettlementDao(),
                database.obligationDao(),
                database.transactionDao(),
                FakeObligationSettlementRemoteStore(),
                deviceIdProvider,
                prefs
            ),
            obligationDao = database.obligationDao(),
            obligationSettlementDao = database.obligationSettlementDao(),
            transactionDao = database.transactionDao(),
            accountDao = database.accountDao(),
            deviceIdProvider = deviceIdProvider
        )

        database.userDao().upsert(UserEntity(UID, "service@test.dev", NOW, NOW))
        database.accountDao().insert(account(ACCOUNT_ID, "Banco principal"))
        database.accountDao().insert(account(SECOND_ACCOUNT_ID, "Caja"))
        database.categoryDao().insert(category(INCOME_CATEGORY_ID, "Ventas", "INCOME"))
        database.categoryDao().insert(category(EXPENSE_CATEGORY_ID, "Servicios", "EXPENSE"))
        database.categoryDao().insert(category(SECOND_EXPENSE_CATEGORY_ID, "Compras", "EXPENSE"))
        database.categoryDao().insert(category(OBLIGATION_CATEGORY_ID, "Clientes", "BOTH"))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun createObligationStartsPendingWithFullBalanceAndNoTransaction() = runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Factura A",
            counterpartyName = "Cliente A",
            currency = "COP",
            originalAmountCents = 500_000L,
            issuedAtEpochSec = NOW,
            dueAtEpochSec = NOW + 5_000L,
            obligationCategoryId = OBLIGATION_CATEGORY_ID,
            reference = "FAC-100",
            notes = "Crédito"
        )

        val state = service.getResolvedState(UID, obligation.id, NOW)

        assertEquals(0, database.transactionDao().getByUser(UID).size)
        assertEquals(ObligationResolvedStatus.PENDIENTE, state.status)
        assertEquals(500_000L, state.pendingAmountCents)
        assertEquals(0L, state.totalSettledCents)
    }

    @Test
    fun updateObligationMetadataDoesNotCreateOrModifyTransaction() = runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Factura B",
            counterpartyName = "Cliente B",
            currency = "COP",
            originalAmountCents = 400_000L,
            issuedAtEpochSec = NOW
        )
        val registered = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 100_000L,
            occurredAtEpochSec = NOW + 1L,
            note = "Abono"
        )
        val before = database.transactionDao().getById(registered.transaction.id)

        val updated = service.updateObligationMetadata(
            userUid = UID,
            obligationId = obligation.id,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Factura B2",
            counterpartyName = "Cliente B2",
            currency = "COP",
            originalAmountCents = 450_000L,
            issuedAtEpochSec = NOW,
            dueAtEpochSec = NOW + 9_000L,
            obligationCategoryId = OBLIGATION_CATEGORY_ID,
            reference = "FAC-101",
            notes = "Actualizada"
        )
        val after = database.transactionDao().getById(registered.transaction.id)

        assertEquals("Factura B2", updated.title)
        assertEquals(before, after)
        assertEquals(1, database.transactionDao().getByUser(UID).size)
    }

    @Test
    fun cancelObligationDoesNotCreateOrModifyTransaction() = runBlocking {
        seedIncome(ACCOUNT_ID, 300_000L)
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_PAYABLE,
            title = "Servicio contratado",
            counterpartyName = "Proveedor",
            currency = "COP",
            originalAmountCents = 300_000L,
            issuedAtEpochSec = NOW
        )
        val registered = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = EXPENSE_CATEGORY_ID,
            amountCents = 50_000L,
            occurredAtEpochSec = NOW + 2L,
            note = "Anticipo"
        )
        val before = database.transactionDao().getById(registered.transaction.id)

        val cancelled = service.cancelObligation(UID, obligation.id, NOW + 20L)
        val after = database.transactionDao().getById(registered.transaction.id)

        assertEquals(NOW + 20L, cancelled.cancelledAtEpochSec)
        assertEquals(before, after)
        assertEquals(2, database.transactionDao().getByUser(UID).size)
    }

    @Test
    fun validReceivableSettlementCreatesExactlyOneIncomeTransaction() = runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro 1",
            counterpartyName = "Cliente",
            currency = "COP",
            originalAmountCents = 250_000L,
            issuedAtEpochSec = NOW
        )

        val result = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 120_000L,
            occurredAtEpochSec = NOW + 1L,
            note = "Ingreso"
        )

        assertEquals(1, database.transactionDao().getByUser(UID).size)
        assertEquals("INCOME", result.transaction.kind)
        assertEquals(result.transaction.id, result.settlement.linkedTransactionId)
        assertEquals(result.transaction.id, database.obligationSettlementDao().getById(result.settlement.id)?.linkedTransactionId)
    }

    @Test
    fun payableSettlementCreatesExpenseTransaction() = runBlocking {
        seedIncome(ACCOUNT_ID, 500_000L)
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_PAYABLE,
            title = "Pago proveedor",
            counterpartyName = "Proveedor",
            currency = "COP",
            originalAmountCents = 200_000L,
            issuedAtEpochSec = NOW
        )

        val result = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = EXPENSE_CATEGORY_ID,
            amountCents = 80_000L,
            occurredAtEpochSec = NOW + 1L,
            note = "Pago parcial"
        )

        assertEquals("EXPENSE", result.transaction.kind)
        assertEquals(1, database.transactionDao().getByUser(UID).count { it.kind == "EXPENSE" })
    }

    @Test
    fun secondSettlementCreatesIndependentTransactionAndNeverReusesLinkedTransactionId() = runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro 2",
            counterpartyName = "Cliente",
            currency = "COP",
            originalAmountCents = 300_000L,
            issuedAtEpochSec = NOW
        )

        val first = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 100_000L,
            occurredAtEpochSec = NOW + 1L,
            note = null
        )
        val second = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = SECOND_ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 50_000L,
            occurredAtEpochSec = NOW + 2L,
            note = null
        )

        assertEquals(2, database.transactionDao().getByUser(UID).size)
        assertNotEquals(first.transaction.id, second.transaction.id)
        assertNotEquals(first.settlement.linkedTransactionId, second.settlement.linkedTransactionId)
    }

    @Test
    fun registerSettlementPreservesExactOccurredAtTimestampOnSettlementAndTransaction() = runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro timestamp",
            counterpartyName = "Cliente",
            currency = "COP",
            originalAmountCents = 200_000L,
            issuedAtEpochSec = NOW
        )

        // Timestamp con hora real (no medianoche): 07:13:42 del mismo día base.
        val realTs = NOW + 7L * 3600 + 13L * 60 + 42L
        val result = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 100_000L,
            occurredAtEpochSec = realTs,
            note = null
        )

        assertEquals(realTs, result.settlement.occurredAtEpochSec)
        assertEquals(realTs, result.transaction.occurredAtEpochSec)
        assertEquals(realTs, database.transactionDao().getById(result.transaction.id)?.occurredAtEpochSec)
        assertEquals(realTs, database.obligationSettlementDao().getById(result.settlement.id)?.occurredAtEpochSec)
    }

    @Test
    fun updateSettlementPreservesExactOccurredAtTimestampOnSettlementAndTransaction() = runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro timestamp edit",
            counterpartyName = "Cliente",
            currency = "COP",
            originalAmountCents = 200_000L,
            issuedAtEpochSec = NOW
        )
        val realTs = NOW + 16L * 3600 + 45L * 60 + 30L
        val created = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 100_000L,
            occurredAtEpochSec = realTs,
            note = null
        )

        val editedTs = realTs + 86_400L // mismo instante horario, día siguiente
        val updated = service.updateSettlement(
            userUid = UID,
            settlementId = created.settlement.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 120_000L,
            occurredAtEpochSec = editedTs,
            note = null
        )

        assertEquals(created.settlement.id, updated.settlement.id)
        assertEquals(created.transaction.id, updated.transaction.id)
        assertEquals(editedTs, updated.settlement.occurredAtEpochSec)
        assertEquals(editedTs, updated.transaction.occurredAtEpochSec)
    }

    @Test
    fun overpaySettlementIsRejected() {
        runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro 3",
            counterpartyName = "Cliente",
            currency = "COP",
            originalAmountCents = 100_000L,
            issuedAtEpochSec = NOW
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.registerSettlement(
                    userUid = UID,
                    obligationId = obligation.id,
                    accountId = ACCOUNT_ID,
                    financialCategoryId = INCOME_CATEGORY_ID,
                    amountCents = 100_001L,
                    occurredAtEpochSec = NOW + 1L,
                    note = null
                )
            }
        }
        }
    }

    @Test
    fun zeroAndNegativeSettlementAmountsAreRejected() {
        runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro 4",
            counterpartyName = "Cliente",
            currency = "COP",
            originalAmountCents = 100_000L,
            issuedAtEpochSec = NOW
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.registerSettlement(
                    userUid = UID,
                    obligationId = obligation.id,
                    accountId = ACCOUNT_ID,
                    financialCategoryId = INCOME_CATEGORY_ID,
                    amountCents = 0L,
                    occurredAtEpochSec = NOW + 1L,
                    note = null
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.registerSettlement(
                    userUid = UID,
                    obligationId = obligation.id,
                    accountId = ACCOUNT_ID,
                    financialCategoryId = INCOME_CATEGORY_ID,
                    amountCents = -1L,
                    occurredAtEpochSec = NOW + 1L,
                    note = null
                )
            }
        }
        }
    }

    @Test
    fun cancelledObligationRejectsNewSettlement() {
        runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro cancelado",
            counterpartyName = "Cliente",
            currency = "COP",
            originalAmountCents = 100_000L,
            issuedAtEpochSec = NOW
        )
        service.cancelObligation(UID, obligation.id, NOW + 10L)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.registerSettlement(
                    userUid = UID,
                    obligationId = obligation.id,
                    accountId = ACCOUNT_ID,
                    financialCategoryId = INCOME_CATEGORY_ID,
                    amountCents = 10_000L,
                    occurredAtEpochSec = NOW + 11L,
                    note = null
                )
            }
        }
        }
    }

    @Test
    fun paidObligationRejectsNewSettlement() {
        runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro pagado",
            counterpartyName = "Cliente",
            currency = "COP",
            originalAmountCents = 100_000L,
            issuedAtEpochSec = NOW
        )
        service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 100_000L,
            occurredAtEpochSec = NOW + 1L,
            note = null
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.registerSettlement(
                    userUid = UID,
                    obligationId = obligation.id,
                    accountId = ACCOUNT_ID,
                    financialCategoryId = INCOME_CATEGORY_ID,
                    amountCents = 1L,
                    occurredAtEpochSec = NOW + 2L,
                    note = null
                )
            }
        }
        }
    }

    @Test
    fun updateSettlementModifiesSameTransactionWithoutCreatingAnotherOne() = runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_PAYABLE,
            title = "Pago editable",
            counterpartyName = "Proveedor",
            currency = "COP",
            originalAmountCents = 300_000L,
            issuedAtEpochSec = NOW
        )
        seedIncome(ACCOUNT_ID, 500_000L)
        seedIncome(SECOND_ACCOUNT_ID, 300_000L)
        val first = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = EXPENSE_CATEGORY_ID,
            amountCents = 100_000L,
            occurredAtEpochSec = NOW + 1L,
            note = "Pago 1"
        )

        val updated = service.updateSettlement(
            userUid = UID,
            settlementId = first.settlement.id,
            accountId = SECOND_ACCOUNT_ID,
            financialCategoryId = SECOND_EXPENSE_CATEGORY_ID,
            amountCents = 120_000L,
            occurredAtEpochSec = NOW + 5L,
            note = "Pago ajustado"
        )
        val storedTx = database.transactionDao().getById(first.transaction.id)
        val state = service.getResolvedState(UID, obligation.id, NOW + 5L)

        assertEquals(first.transaction.id, updated.transaction.id)
        assertEquals(first.settlement.id, updated.settlement.id)
        assertEquals(1, database.transactionDao().getByUser(UID).count { it.kind == "EXPENSE" })
        assertEquals(SECOND_EXPENSE_CATEGORY_ID, storedTx?.categoryId)
        assertEquals(SECOND_ACCOUNT_ID, storedTx?.accountId)
        assertEquals(180_000L, state.pendingAmountCents)
    }

    @Test
    fun updateSettlementRollbackKeepsPreviousStateIfSomethingFails() = runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_PAYABLE,
            title = "Pago rollback",
            counterpartyName = "Proveedor",
            currency = "COP",
            originalAmountCents = 250_000L,
            issuedAtEpochSec = NOW
        )
        seedIncome(ACCOUNT_ID, 400_000L)
        seedIncome(SECOND_ACCOUNT_ID, 300_000L)
        val first = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = EXPENSE_CATEGORY_ID,
            amountCents = 90_000L,
            occurredAtEpochSec = NOW + 1L,
            note = "Inicial"
        )
        val txBefore = database.transactionDao().getById(first.transaction.id)
        val settlementBefore = database.obligationSettlementDao().getById(first.settlement.id)

        service.afterTransactionMutationHook = { throw IllegalStateException("forced_failure") }
        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    service.updateSettlement(
                        userUid = UID,
                        settlementId = first.settlement.id,
                        accountId = SECOND_ACCOUNT_ID,
                        financialCategoryId = SECOND_EXPENSE_CATEGORY_ID,
                        amountCents = 110_000L,
                        occurredAtEpochSec = NOW + 9L,
                        note = "No debe persistir"
                    )
                }
            }
        } finally {
            service.afterTransactionMutationHook = null
        }

        assertEquals(txBefore, database.transactionDao().getById(first.transaction.id))
        assertEquals(settlementBefore, database.obligationSettlementDao().getById(first.settlement.id))
    }

    @Test
    fun deleteSettlementRemovesLinkedTransactionAndKeepsOtherSettlementsUntouched() = runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro múltiple",
            counterpartyName = "Cliente",
            currency = "COP",
            originalAmountCents = 400_000L,
            issuedAtEpochSec = NOW
        )
        val first = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 100_000L,
            occurredAtEpochSec = NOW + 1L,
            note = "Abono 1"
        )
        val second = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = SECOND_ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 50_000L,
            occurredAtEpochSec = NOW + 2L,
            note = "Abono 2"
        )

        val deleted = service.deleteSettlement(UID, first.settlement.id)

        assertEquals(first.transaction.id, deleted.deletedTransactionId)
        assertNull(database.transactionDao().getById(first.transaction.id))
        assertNull(database.obligationSettlementDao().getById(first.settlement.id))
        assertNotNull(database.transactionDao().getById(second.transaction.id))
        assertNotNull(database.obligationSettlementDao().getById(second.settlement.id))
        assertEquals(350_000L, deleted.resolvedState.pendingAmountCents)
    }

    @Test
    fun resolvesAllVisibleStates() = runBlocking {
        val pending = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Pendiente",
            counterpartyName = "A",
            currency = "COP",
            originalAmountCents = 100_000L,
            issuedAtEpochSec = NOW,
            dueAtEpochSec = null
        )
        val partial = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Parcial",
            counterpartyName = "B",
            currency = "COP",
            originalAmountCents = 200_000L,
            issuedAtEpochSec = NOW,
            dueAtEpochSec = null
        )
        val paid = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Pagada",
            counterpartyName = "C",
            currency = "COP",
            originalAmountCents = 150_000L,
            issuedAtEpochSec = NOW,
            dueAtEpochSec = NOW + 50L
        )
        val overdue = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Vencida",
            counterpartyName = "D",
            currency = "COP",
            originalAmountCents = 180_000L,
            issuedAtEpochSec = NOW,
            dueAtEpochSec = NOW - 10L
        )
        val cancelled = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cancelada",
            counterpartyName = "E",
            currency = "COP",
            originalAmountCents = 220_000L,
            issuedAtEpochSec = NOW,
            dueAtEpochSec = NOW + 10L
        )

        service.registerSettlement(UID, partial.id, ACCOUNT_ID, INCOME_CATEGORY_ID, 50_000L, NOW + 1L, null)
        service.registerSettlement(UID, paid.id, ACCOUNT_ID, INCOME_CATEGORY_ID, 150_000L, NOW + 1L, null)
        service.cancelObligation(UID, cancelled.id, NOW + 5L)

        assertEquals(ObligationResolvedStatus.PENDIENTE, service.getResolvedState(UID, pending.id, NOW + 1L).status)
        assertEquals(ObligationResolvedStatus.PARCIAL, service.getResolvedState(UID, partial.id, NOW + 1L).status)
        assertEquals(ObligationResolvedStatus.PAGADA, service.getResolvedState(UID, paid.id, NOW + 1L).status)
        assertEquals(ObligationResolvedStatus.VENCIDA, service.getResolvedState(UID, overdue.id, NOW + 1L).status)
        assertEquals(ObligationResolvedStatus.CANCELADA, service.getResolvedState(UID, cancelled.id, NOW + 6L).status)
    }

    // ── Filtro de categoría financiera por kind del movimiento ─────

    @Test
    fun receivableSettlementRejectsExpenseCategory() = runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro con categoría inválida",
            counterpartyName = "Cliente",
            currency = "COP",
            originalAmountCents = 100_000L,
            issuedAtEpochSec = NOW
        )

        val error = runCatching {
            service.registerSettlement(
                userUid = UID,
                obligationId = obligation.id,
                accountId = ACCOUNT_ID,
                financialCategoryId = EXPENSE_CATEGORY_ID,
                amountCents = 10_000L,
                occurredAtEpochSec = NOW + 1L,
                note = null
            )
        }.exceptionOrNull()
        assertNotNull("Se esperaba settlement_category_kind_mismatch", error)

        assertEquals("settlement_category_kind_mismatch", error?.message)
        assertEquals(0, database.transactionDao().getByUser(UID).size)
        assertEquals(0, database.obligationSettlementDao().getByObligation(obligation.id).size)
    }

    @Test
    fun payableSettlementRejectsIncomeCategory() = runBlocking {
        seedIncome(ACCOUNT_ID, 200_000L)
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_PAYABLE,
            title = "Pago con categoría inválida",
            counterpartyName = "Proveedor",
            currency = "COP",
            originalAmountCents = 100_000L,
            issuedAtEpochSec = NOW
        )

        val error = runCatching {
            service.registerSettlement(
                userUid = UID,
                obligationId = obligation.id,
                accountId = ACCOUNT_ID,
                financialCategoryId = INCOME_CATEGORY_ID,
                amountCents = 10_000L,
                occurredAtEpochSec = NOW + 1L,
                note = null
            )
        }.exceptionOrNull()
        assertNotNull("Se esperaba settlement_category_kind_mismatch", error)

        assertEquals("settlement_category_kind_mismatch", error?.message)
        // Solo queda el seed de income; el abono no creó transacción ni settlement.
        assertEquals(1, database.transactionDao().getByUser(UID).size)
        assertEquals(0, database.obligationSettlementDao().getByObligation(obligation.id).size)
    }

    @Test
    fun settlementAcceptsCompatibleSubcategory() = runBlocking {
        val subIncomeId = "category-sub-income"
        database.categoryDao().insert(
            category(subIncomeId, "Ventas al por mayor", "INCOME").copy(parentId = INCOME_CATEGORY_ID)
        )
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro con subcategoría",
            counterpartyName = "Cliente",
            currency = "COP",
            originalAmountCents = 100_000L,
            issuedAtEpochSec = NOW
        )

        val result = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = subIncomeId,
            amountCents = 25_000L,
            occurredAtEpochSec = NOW + 1L,
            note = null
        )

        assertEquals(subIncomeId, result.transaction.categoryId)
        assertEquals("INCOME", result.transaction.kind)
    }

    @Test
    fun updateSettlementRejectsIncompatibleCategory() = runBlocking {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro",
            counterpartyName = "Cliente",
            currency = "COP",
            originalAmountCents = 100_000L,
            issuedAtEpochSec = NOW
        )
        val registered = service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 30_000L,
            occurredAtEpochSec = NOW + 1L,
            note = null
        )

        val error = runCatching {
            service.updateSettlement(
                userUid = UID,
                settlementId = registered.settlement.id,
                accountId = ACCOUNT_ID,
                financialCategoryId = EXPENSE_CATEGORY_ID,
                amountCents = 30_000L,
                occurredAtEpochSec = NOW + 2L,
                note = null
            )
        }.exceptionOrNull()
        assertNotNull("Se esperaba settlement_category_kind_mismatch", error)

        assertEquals("settlement_category_kind_mismatch", error?.message)
        val storedTx = database.transactionDao().getById(registered.transaction.id)
        assertEquals(INCOME_CATEGORY_ID, storedTx?.categoryId)
    }

    private suspend fun seedIncome(accountId: String, amountCents: Long) {
        database.transactionDao().insert(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                userUid = UID,
                accountId = accountId,
                categoryId = INCOME_CATEGORY_ID,
                kind = "INCOME",
                amountCents = amountCents,
                occurredAtEpochSec = NOW - 50L,
                note = "Seed income",
                createdAtEpochSec = NOW - 50L,
                updatedAtEpochSec = NOW - 50L,
                updatedBy = "test-device"
            )
        )
    }

    private fun account(id: String, name: String) = AccountEntity(
        id = id,
        userUid = UID,
        name = name,
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

    companion object {
        private const val UID = "obligation-service-user"
        private const val ACCOUNT_ID = "account-1"
        private const val SECOND_ACCOUNT_ID = "account-2"
        private const val INCOME_CATEGORY_ID = "category-income"
        private const val EXPENSE_CATEGORY_ID = "category-expense"
        private const val SECOND_EXPENSE_CATEGORY_ID = "category-expense-2"
        private const val OBLIGATION_CATEGORY_ID = "category-obligation"
        private const val NOW = 1_700_000_000L
    }
}
