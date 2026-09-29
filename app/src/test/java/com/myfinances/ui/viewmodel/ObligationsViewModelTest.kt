package com.jcadenas.xpendz.ui.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jcadenas.xpendz.application.obligation.ObligationResolvedStatus
import com.jcadenas.xpendz.application.obligation.ObligationService
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.data.local.entity.CategoryEntity
import com.jcadenas.xpendz.data.local.entity.ObligationEntity
import com.jcadenas.xpendz.data.local.entity.TransactionEntity
import com.jcadenas.xpendz.data.local.entity.UserEntity
import com.jcadenas.xpendz.data.repository.AccountRepository
import com.jcadenas.xpendz.data.repository.AuthRepository
import com.jcadenas.xpendz.data.repository.CategoryRepository
import com.jcadenas.xpendz.data.repository.FakeObligationRemoteStore
import com.jcadenas.xpendz.data.repository.FakeObligationSettlementRemoteStore
import com.jcadenas.xpendz.data.repository.ObligationRepository
import com.jcadenas.xpendz.data.repository.ObligationSettlementRepository
import com.jcadenas.xpendz.data.repository.TransactionRepository
import com.jcadenas.xpendz.sync.DeviceIdProvider
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifica que el ViewModel de Obligaciones solo representa el dominio:
 * todas las mutaciones pasan por ObligationService (creación de la tx la
 * hace el servicio), los flags de estado reflejan loading/saving/error y
 * el guard bloquea envíos duplicados.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ObligationsViewModelTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var service: ObligationService
    private lateinit var viewModel: ObligationsViewModel

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:000:test")
                    .setApiKey("fake-api-key")
                    .setProjectId("test-project")
                    .build()
            )
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())

        database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .allowMainThreadQueries()
            .build()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val deviceIdProvider = DeviceIdProvider(context)
        val firestore = FirebaseFirestore.getInstance()

        val obligationRepository = ObligationRepository(
            database.obligationDao(),
            database.obligationSettlementDao(),
            FakeObligationRemoteStore(),
            deviceIdProvider,
            prefs
        )
        val obligationSettlementRepository = ObligationSettlementRepository(
            database.obligationSettlementDao(),
            database.obligationDao(),
            database.transactionDao(),
            FakeObligationSettlementRemoteStore(),
            deviceIdProvider,
            prefs
        )
        service = ObligationService(
            database = database,
            obligationRepository = obligationRepository,
            obligationSettlementRepository = obligationSettlementRepository,
            obligationDao = database.obligationDao(),
            obligationSettlementDao = database.obligationSettlementDao(),
            transactionDao = database.transactionDao(),
            accountDao = database.accountDao(),
            deviceIdProvider = deviceIdProvider
        )

        viewModel = ObligationsViewModel(
            authRepository = AuthRepository(FirebaseAuth.getInstance()),
            obligationRepository = obligationRepository,
            obligationSettlementRepository = obligationSettlementRepository,
            accountRepository = AccountRepository(
                database.accountDao(), database.goalDao(), firestore, deviceIdProvider
            ),
            categoryRepository = CategoryRepository(
                database.categoryDao(), firestore, deviceIdProvider
            ),
            transactionRepository = TransactionRepository(
                database.transactionDao(), database.accountDao(),
                database.obligationSettlementDao(), firestore, deviceIdProvider
            ),
            obligationService = service
        )
        viewModel.userUidOverride = UID

        database.userDao().upsert(UserEntity(UID, "vm@test.dev", NOW, NOW))
        database.accountDao().insert(account(ACCOUNT_ID, "Banco principal"))
        database.accountDao().insert(account(SECOND_ACCOUNT_ID, "Caja"))
        database.categoryDao().insert(category(INCOME_CATEGORY_ID, "Ventas", "INCOME"))
        database.categoryDao().insert(category(EXPENSE_CATEGORY_ID, "Servicios", "EXPENSE"))
        database.categoryDao().insert(category(OBLIGATION_CATEGORY_ID, "Clientes", "BOTH"))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DB_NAME)
        Dispatchers.resetMain()
    }

    // ── Estados de pantalla ────────────────────────────────────────

    @Test
    fun loadingThenEmptyStateWhenNoObligations() = runBlocking {
        viewModel.refresh()
        // Tras refresh: isLoading cae a false con lista vacía = estado "empty".
        await("lista cargada") { !viewModel.state.value.isLoading }

        assertTrue(viewModel.state.value.obligations.isEmpty())
        assertNull(viewModel.state.value.error)
        assertEquals(0L, viewModel.state.value.receivablePendingCents)
        assertEquals(0L, viewModel.state.value.payablePendingCents)
    }

    @Test
    fun summaryDerivesFromResolvedStatesAndSkipsCancelled() = runBlocking {
        // Fechas relativas al tiempo real: el servicio resuelve el estado con now actual.
        val futureDue = System.currentTimeMillis() / 1000 + 86_400
        val pastDue = System.currentTimeMillis() / 1000 - 86_400
        val receivable = createViaService(ObligationEntity.TYPE_RECEIVABLE, 500_000L, due = futureDue)
        val payable = createViaService(ObligationEntity.TYPE_PAYABLE, 300_000L)
        val overdue = createViaService(ObligationEntity.TYPE_RECEIVABLE, 100_000L, due = pastDue)
        val cancelled = createViaService(ObligationEntity.TYPE_RECEIVABLE, 999_000L, due = futureDue)
        service.cancelObligation(UID, cancelled.id)

        viewModel.refresh()
        await("resumen calculado") {
            !viewModel.state.value.isLoading &&
                viewModel.state.value.obligations.size == 4 &&
                viewModel.state.value.resolvedStates.size == 4
        }

        assertEquals(600_000L, viewModel.state.value.receivablePendingCents)
        assertEquals(300_000L, viewModel.state.value.payablePendingCents)
        assertEquals(100_000L, viewModel.state.value.overduePendingCents)
        assertEquals(
            ObligationResolvedStatus.CANCELADA,
            viewModel.state.value.resolvedStates.getValue(cancelled.id).status
        )
        assertEquals(
            ObligationResolvedStatus.VENCIDA,
            viewModel.state.value.resolvedStates.getValue(overdue.id).status
        )
        assertTrue(receivable.id != payable.id)
    }

    // ── Creación / edición / cancelación ───────────────────────────

    @Test
    fun createObligationCreatesObligationWithoutAnyTransaction() = runBlocking {
        viewModel.refresh()
        await("lista inicial") { !viewModel.state.value.isLoading }

        viewModel.createObligation(
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Factura A",
            counterpartyName = "Cliente A",
            originalAmountCents = 500_000L,
            issuedAtEpochSec = NOW,
            dueAtEpochSec = NOW + 5_000,
            obligationCategoryId = OBLIGATION_CATEGORY_ID,
            reference = "FAC-1",
            notes = "Crédito"
        )
        await("obligación guardada") { viewModel.state.value.obligationSaved }

        val stored = database.obligationDao().getByUser(UID)
        assertEquals(1, stored.size)
        assertEquals("Factura A", stored[0].title)
        assertEquals(0, database.transactionDao().getByUser(UID).size)
        assertFalse(viewModel.state.value.isSavingObligation)
        assertNull(viewModel.state.value.error)
        await("resumen actualizado") {
            viewModel.state.value.receivablePendingCents == 500_000L
        }
    }

    @Test
    fun updateObligationMetadataKeepsSettlementsAndTransactionsUntouched() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_RECEIVABLE, 300_000L)
        val settled = service.registerSettlement(
            userUid = UID, obligationId = obligation.id, accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID, amountCents = 100_000L,
            occurredAtEpochSec = NOW + 1, note = "Abono"
        )
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }

        viewModel.updateObligationMetadata(
            obligationId = obligation.id,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Título actualizado",
            counterpartyName = "Cliente B",
            originalAmountCents = 300_000L,
            issuedAtEpochSec = NOW,
            dueAtEpochSec = null,
            obligationCategoryId = null,
            reference = "REF-2",
            notes = "Editada"
        )
        await("obligación editada") { viewModel.state.value.obligationSaved }

        assertEquals("Título actualizado", database.obligationDao().getById(obligation.id)?.title)
        assertNotNull(database.obligationSettlementDao().getById(settled.settlement.id))
        assertNotNull(database.transactionDao().getById(settled.transaction.id))
        assertEquals(1, database.transactionDao().getByUser(UID).size)
        assertEquals(
            200_000L,
            service.getResolvedState(UID, obligation.id).pendingAmountCents
        )
    }

    @Test
    fun cancelObligationMarksCancelledWithoutCreatingTransaction() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_PAYABLE, 200_000L)
        seedIncome(ACCOUNT_ID, 500_000L)
        val settled = service.registerSettlement(
            userUid = UID, obligationId = obligation.id, accountId = ACCOUNT_ID,
            financialCategoryId = EXPENSE_CATEGORY_ID, amountCents = 50_000L,
            occurredAtEpochSec = NOW + 1, note = "Parcial"
        )
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }

        viewModel.cancelObligation(obligation.id)
        await("obligación cancelada") { viewModel.state.value.obligationCancelled }

        assertEquals(
            ObligationResolvedStatus.CANCELADA,
            service.getResolvedState(UID, obligation.id).status
        )
        // Cancelar no crea ni borra movimientos: seed + tx enlazada permanecen.
        assertEquals(2, database.transactionDao().getByUser(UID).size)
        assertNotNull(database.transactionDao().getById(settled.transaction.id))
        assertFalse(viewModel.state.value.isCancellingObligation)
    }

    // ── Settlements ────────────────────────────────────────────────

    @Test
    fun registerSettlementCreatesExactlyOneLinkedTransaction() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_RECEIVABLE, 400_000L)
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }

        viewModel.registerSettlement(
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 150_000L,
            occurredAtEpochSec = NOW + 1,
            note = "Cobro parcial"
        )
        await("settlement guardado") { viewModel.state.value.settlementSaved }

        val settlements = database.obligationSettlementDao().getByObligation(obligation.id)
        assertEquals(1, settlements.size)
        assertEquals(1, database.transactionDao().getByUser(UID).size)
        val tx = database.transactionDao().getById(settlements[0].linkedTransactionId)
        assertNotNull(tx)
        assertEquals("INCOME", tx?.kind)
        assertEquals(150_000L, tx?.amountCents)
        assertEquals(ACCOUNT_ID, tx?.accountId)
        assertFalse(viewModel.state.value.isSavingSettlement)
        assertEquals(
            250_000L,
            service.getResolvedState(UID, obligation.id).pendingAmountCents
        )
    }

    @Test
    fun accountBalancesRefreshAfterSettlementMutation() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_RECEIVABLE, 400_000L)
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }
        assertEquals(0L, viewModel.state.value.accountBalancesCents[ACCOUNT_ID])

        viewModel.registerSettlement(
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 150_000L,
            occurredAtEpochSec = NOW + 1,
            note = null
        )
        await("settlement guardado") { viewModel.state.value.settlementSaved }
        await("balance actualizado") {
            viewModel.state.value.accountBalancesCents[ACCOUNT_ID] == 150_000L
        }
    }

    @Test
    fun registerSettlementExceedingPendingSurfacesErrorAndCreatesNothing() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_RECEIVABLE, 100_000L)
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }

        viewModel.registerSettlement(
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 100_001L,
            occurredAtEpochSec = NOW + 1,
            note = null
        )
        await("error de formulario") { viewModel.state.value.settlementFormError != null }

        assertEquals(
            "El monto excede el saldo pendiente de la obligación",
            viewModel.state.value.settlementFormError
        )
        assertNull(viewModel.state.value.error)
        assertTrue(database.obligationSettlementDao().getByObligation(obligation.id).isEmpty())
        assertEquals(0, database.transactionDao().getByUser(UID).size)
        assertFalse(viewModel.state.value.settlementSaved)
        assertFalse(viewModel.state.value.isSavingSettlement)
        assertFalse(viewModel.guard().isInFlight(ObligationsViewModel.OP_SETTLEMENT_SAVE))
    }

    @Test
    fun insufficientBalanceSurfacesInSettlementFormError() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_PAYABLE, 500_000L)
        seedIncome(ACCOUNT_ID, 150_000L)
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }

        viewModel.registerSettlement(
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = EXPENSE_CATEGORY_ID,
            amountCents = 200_000L,
            occurredAtEpochSec = NOW + 1,
            note = null
        )
        await("error de formulario") { viewModel.state.value.settlementFormError != null }

        assertEquals(
            "Saldo insuficiente en la cuenta seleccionada",
            viewModel.state.value.settlementFormError
        )
        assertTrue(database.obligationSettlementDao().getByObligation(obligation.id).isEmpty())
        assertEquals(1, database.transactionDao().getByUser(UID).size) // solo el seed
    }

    @Test
    fun registerSettlementOnCancelledObligationSurfacesFormError() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_RECEIVABLE, 300_000L)
        service.cancelObligation(UID, obligation.id, NOW + 1)
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }

        viewModel.registerSettlement(
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 50_000L,
            occurredAtEpochSec = NOW + 2,
            note = null
        )
        await("error de formulario") { viewModel.state.value.settlementFormError != null }

        assertEquals("La obligación está cancelada", viewModel.state.value.settlementFormError)
        assertTrue(database.obligationSettlementDao().getByObligation(obligation.id).isEmpty())
        assertEquals(0, database.transactionDao().getByUser(UID).size)
    }

    @Test
    fun registerSettlementOnPaidObligationSurfacesFormError() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_PAYABLE, 100_000L)
        seedIncome(ACCOUNT_ID, 500_000L)
        service.registerSettlement(
            userUid = UID, obligationId = obligation.id, accountId = ACCOUNT_ID,
            financialCategoryId = EXPENSE_CATEGORY_ID, amountCents = 100_000L,
            occurredAtEpochSec = NOW + 1, note = null
        )
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }

        viewModel.registerSettlement(
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = EXPENSE_CATEGORY_ID,
            amountCents = 10_000L,
            occurredAtEpochSec = NOW + 2,
            note = null
        )
        await("error de formulario") { viewModel.state.value.settlementFormError != null }

        assertEquals(
            "La obligación ya está pagada por completo",
            viewModel.state.value.settlementFormError
        )
        assertEquals(1, database.obligationSettlementDao().getByObligation(obligation.id).size)
        assertEquals(2, database.transactionDao().getByUser(UID).size) // seed + settlement
    }

    @Test
    fun retryAfterFormErrorSucceedsAndCreatesExactlyOnePair() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_RECEIVABLE, 200_000L)
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }

        viewModel.registerSettlement(
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 250_000L,
            occurredAtEpochSec = NOW + 1,
            note = null
        )
        await("error de formulario") { viewModel.state.value.settlementFormError != null }
        assertFalse(viewModel.state.value.settlementSaved)

        viewModel.registerSettlement(
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 150_000L,
            occurredAtEpochSec = NOW + 1,
            note = null
        )
        await("settlement guardado") { viewModel.state.value.settlementSaved }

        assertNull(viewModel.state.value.settlementFormError)
        assertEquals(1, database.obligationSettlementDao().getByObligation(obligation.id).size)
        assertEquals(1, database.transactionDao().getByUser(UID).size)
    }

    @Test
    fun updateSettlementModifiesSameSettlementAndSameTransaction() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_PAYABLE, 300_000L)
        seedIncome(ACCOUNT_ID, 500_000L)
        seedIncome(SECOND_ACCOUNT_ID, 400_000L)
        val settled = service.registerSettlement(
            userUid = UID, obligationId = obligation.id, accountId = ACCOUNT_ID,
            financialCategoryId = EXPENSE_CATEGORY_ID, amountCents = 100_000L,
            occurredAtEpochSec = NOW + 1, note = "Pago 1"
        )
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }

        viewModel.updateSettlement(
            settlementId = settled.settlement.id,
            accountId = SECOND_ACCOUNT_ID,
            financialCategoryId = EXPENSE_CATEGORY_ID,
            amountCents = 120_000L,
            occurredAtEpochSec = NOW + 5,
            note = "Pago ajustado"
        )
        await("settlement actualizado") { viewModel.state.value.settlementSaved }

        assertEquals(1, database.obligationSettlementDao().getByObligation(obligation.id).size)
        // 2 seeds + 1 tx enlazada: no se creó ninguna tx nueva.
        assertEquals(3, database.transactionDao().getByUser(UID).size)
        val tx = database.transactionDao().getById(settled.transaction.id)
        assertNotNull(tx)
        assertEquals(120_000L, tx?.amountCents)
        assertEquals(SECOND_ACCOUNT_ID, tx?.accountId)
        assertEquals(settled.settlement.id, database.obligationSettlementDao()
            .getByObligation(obligation.id)[0].id)
        assertEquals(
            180_000L,
            service.getResolvedState(UID, obligation.id).pendingAmountCents
        )
    }

    @Test
    fun deleteSettlementRemovesSettlementAndLinkedTransaction() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_RECEIVABLE, 400_000L)
        val settled = service.registerSettlement(
            userUid = UID, obligationId = obligation.id, accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID, amountCents = 100_000L,
            occurredAtEpochSec = NOW + 1, note = "Abono"
        )
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }

        viewModel.deleteSettlement(settled.settlement.id)
        await("settlement eliminado") { viewModel.state.value.settlementDeleted }

        assertNull(database.obligationSettlementDao().getById(settled.settlement.id))
        assertNull(database.transactionDao().getById(settled.transaction.id))
        assertFalse(viewModel.state.value.isDeletingSettlement)
        assertEquals(
            400_000L,
            service.getResolvedState(UID, obligation.id).pendingAmountCents
        )
    }

    // ── Detalle / selección ────────────────────────────────────────

    @Test
    fun openDetailExposesSettlementHistoryWithLinkedTransactions() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_RECEIVABLE, 500_000L)
        val settled = service.registerSettlement(
            userUid = UID, obligationId = obligation.id, accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID, amountCents = 100_000L,
            occurredAtEpochSec = NOW + 1, note = "Abono 1"
        )
        viewModel.refresh()
        await("lista") {
            !viewModel.state.value.isLoading && viewModel.state.value.obligations.isNotEmpty()
        }

        viewModel.openDetail(obligation.id)
        await("detalle con historial") {
            viewModel.state.value.selectedSettlements.size == 1
        }

        assertEquals(obligation.id, viewModel.state.value.selectedObligation?.id)
        assertEquals("Clientes", viewModel.state.value.selectedCategoryName)
        assertEquals(
            settled.transaction.id,
            viewModel.state.value.selectedTransactions[settled.settlement.id]?.id
        )
        assertEquals(
            400_000L,
            viewModel.state.value.selectedResolvedState?.pendingAmountCents
        )

        viewModel.closeDetail()
        assertNull(viewModel.state.value.selectedObligation)
        assertTrue(viewModel.state.value.selectedSettlements.isEmpty())
    }

    // ── Protección doble-submit ────────────────────────────────────

    @Test
    fun duplicateRegisterSettlementWhileInFlightIsRejectedByGuard() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_RECEIVABLE, 300_000L)
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }

        // Simula una operación ya en vuelo: la segunda invocación debe ser no-op.
        assertTrue(viewModel.guard().tryAcquire(ObligationsViewModel.OP_SETTLEMENT_SAVE))
        viewModel.registerSettlement(
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 10_000L,
            occurredAtEpochSec = NOW + 1,
            note = null
        )
        // El early-return es sincrónico: nada se lanzó.
        assertFalse(viewModel.state.value.isSavingSettlement)
        assertFalse(viewModel.state.value.settlementSaved)
        assertTrue(database.obligationSettlementDao().getByObligation(obligation.id).isEmpty())
        assertEquals(0, database.transactionDao().getByUser(UID).size)

        // Tras liberar el guard, la operación vuelve a ser posible.
        viewModel.guard().release(ObligationsViewModel.OP_SETTLEMENT_SAVE)
        viewModel.registerSettlement(
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 10_000L,
            occurredAtEpochSec = NOW + 1,
            note = null
        )
        await("settlement guardado tras liberar") { viewModel.state.value.settlementSaved }
        assertEquals(1, database.obligationSettlementDao().getByObligation(obligation.id).size)
    }

    @Test
    fun duplicateObligationSaveWhileInFlightIsRejectedByGuard() = runBlocking {
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }

        assertTrue(viewModel.guard().tryAcquire(ObligationsViewModel.OP_OBLIGATION_SAVE))
        viewModel.createObligation(
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "No debe crearse",
            counterpartyName = "X",
            originalAmountCents = 1_000L,
            issuedAtEpochSec = NOW,
            dueAtEpochSec = null,
            obligationCategoryId = null,
            reference = null,
            notes = null
        )
        assertFalse(viewModel.state.value.isSavingObligation)
        assertTrue(database.obligationDao().getByUser(UID).isEmpty())
        viewModel.guard().release(ObligationsViewModel.OP_OBLIGATION_SAVE)
    }

    @Test
    fun duplicateSettlementDeleteWhileInFlightIsRejectedByGuard() = runBlocking {
        val obligation = createViaService(ObligationEntity.TYPE_RECEIVABLE, 200_000L)
        val settled = service.registerSettlement(
            userUid = UID, obligationId = obligation.id, accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID, amountCents = 50_000L,
            occurredAtEpochSec = NOW + 1, note = null
        )
        viewModel.refresh()
        await("lista") { !viewModel.state.value.isLoading }

        assertTrue(viewModel.guard().tryAcquire(ObligationsViewModel.OP_SETTLEMENT_DELETE))
        viewModel.deleteSettlement(settled.settlement.id)
        assertFalse(viewModel.state.value.isDeletingSettlement)
        assertNotNull(database.obligationSettlementDao().getById(settled.settlement.id))
        assertNotNull(database.transactionDao().getById(settled.transaction.id))
        viewModel.guard().release(ObligationsViewModel.OP_SETTLEMENT_DELETE)
    }

    // ── Helpers ────────────────────────────────────────────────────

    private suspend fun createViaService(
        type: String,
        amountCents: Long,
        due: Long? = null
    ): ObligationEntity = service.createObligation(
        userUid = UID,
        type = type,
        title = "Obligación ${UUID.randomUUID().toString().take(6)}",
        counterpartyName = "Contraparte",
        currency = "COP",
        originalAmountCents = amountCents,
        issuedAtEpochSec = NOW,
        dueAtEpochSec = due,
        obligationCategoryId = OBLIGATION_CATEGORY_ID,
        reference = null,
        notes = null
    )

    private fun await(desc: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                fail("timeout esperando: $desc")
            }
            Thread.sleep(15)
        }
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
                occurredAtEpochSec = NOW - 100,
                note = "Seed income",
                createdAtEpochSec = NOW - 100,
                updatedAtEpochSec = NOW - 100,
                updatedBy = "test-device"
            )
        )
    }

    private fun account(id: String, name: String) = AccountEntity(
        id = id, userUid = UID, name = name, type = "BANK", currency = "COP",
        createdAtEpochSec = NOW, updatedAtEpochSec = NOW, updatedBy = "test-device"
    )

    private fun category(id: String, name: String, kind: String) = CategoryEntity(
        id = id, userUid = UID, name = name, kind = kind, parentId = null,
        createdAtEpochSec = NOW, updatedAtEpochSec = NOW, updatedBy = "test-device"
    )

    companion object {
        private const val UID = "obligations-vm-user"
        private const val ACCOUNT_ID = "account-1"
        private const val SECOND_ACCOUNT_ID = "account-2"
        private const val INCOME_CATEGORY_ID = "category-income"
        private const val EXPENSE_CATEGORY_ID = "category-expense"
        private const val OBLIGATION_CATEGORY_ID = "category-obligation"
        private const val NOW = 1_700_000_000L
        private const val DB_NAME = "obligations-vm.db"
        private const val PREFS_NAME = "obligations-vm-test"
    }
}
