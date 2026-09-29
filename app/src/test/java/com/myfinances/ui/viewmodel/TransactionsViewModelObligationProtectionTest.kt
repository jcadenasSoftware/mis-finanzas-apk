package com.jcadenas.xpendz.ui.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
import com.jcadenas.xpendz.ui.transactions.ObligationTransactionPolicy
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
 * La tx generada por un settlement de obligación es una proyección del
 * servicio: desde la pantalla de Transactions no puede editarse ni
 * eliminarse — solo consultarse. Verifica los tres puntos de protección
 * del ViewModel (initForm, saveTransaction, deleteTransaction) y la
 * exposición del conjunto enlazado al estado de la lista.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionsViewModelObligationProtectionTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var service: ObligationService
    private lateinit var viewModel: TransactionsViewModel

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

        viewModel = TransactionsViewModel(
            authRepository = AuthRepository(FirebaseAuth.getInstance()),
            transactionRepository = TransactionRepository(
                database.transactionDao(), database.accountDao(),
                database.obligationSettlementDao(), firestore, deviceIdProvider
            ),
            accountRepository = AccountRepository(
                database.accountDao(), database.goalDao(), firestore, deviceIdProvider
            ),
            categoryRepository = CategoryRepository(
                database.categoryDao(), firestore, deviceIdProvider
            ),
            obligationSettlementRepository = obligationSettlementRepository
        )
        viewModel.userUidOverride = UID

        database.userDao().upsert(UserEntity(UID, "tx-vm@test.dev", NOW, NOW))
        database.accountDao().insert(account(ACCOUNT_ID, "Banco principal"))
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

    @Test
    fun linkedTransactionIdsAreExposedInListState() = runBlocking {
        val linkedTx = createLinkedSettlementTx()
        val normalTx = insertPlainExpense()

        viewModel.loadTransactions()
        await("lista cargada") { !viewModel.state.value.isLoading }

        val linkedIds = viewModel.state.value.obligationLinkedTransactionIds
        assertTrue(linkedIds.contains(linkedTx.id))
        assertTrue(!linkedIds.contains(normalTx.id))
    }

    @Test
    fun initFormOnLinkedTransactionShowsProtectedMessage() = runBlocking {
        val linkedTx = createLinkedSettlementTx()

        viewModel.initForm(linkedTx.id)
        await("formulario cargado") { !viewModel.formState.value.isLoading }

        assertEquals(
            ObligationTransactionPolicy.protectedMessage(),
            viewModel.formState.value.error
        )
        assertTrue(viewModel.formState.value.isLoanProtected)
    }

    @Test
    fun saveTransactionOnLinkedTransactionIsRejectedAndKeepsRow() = runBlocking {
        val linkedTx = createLinkedSettlementTx()

        viewModel.initForm(linkedTx.id)
        await("formulario cargado") { !viewModel.formState.value.isLoading }
        viewModel.clearError()

        viewModel.saveTransaction()
        await("error de protección") {
            viewModel.formState.value.error == ObligationTransactionPolicy.protectedMessage()
        }

        val stored = database.transactionDao().getById(linkedTx.id)
        assertNotNull(stored)
        assertEquals(linkedTx.amountCents, stored?.amountCents)
        assertEquals(linkedTx.note, stored?.note)
    }

    @Test
    fun deleteTransactionLinkedToSettlementIsRejectedAndKeepsPair() = runBlocking {
        val linkedTx = createLinkedSettlementTx()

        viewModel.deleteTransaction(linkedTx.id)
        await("error de protección") {
            viewModel.state.value.error == ObligationTransactionPolicy.protectedMessage()
        }

        assertNotNull(database.transactionDao().getById(linkedTx.id))
        assertNotNull(
            database.obligationSettlementDao().getByLinkedTransactionId(linkedTx.id)
        )
    }

    @Test
    fun unlinkedTransactionDeletesNormally() = runBlocking {
        val normalTx = insertPlainExpense()

        viewModel.deleteTransaction(normalTx.id)
        await("tx eliminada") { database.transactionDao().getById(normalTx.id) == null }

        assertNull(database.transactionDao().getById(normalTx.id))
        assertTrue(
            viewModel.state.value.error == null ||
                viewModel.state.value.error != ObligationTransactionPolicy.protectedMessage()
        )
    }

    // ── Helpers ────────────────────────────────────────────────────

    private suspend fun createLinkedSettlementTx(): TransactionEntity {
        val obligation = service.createObligation(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Cobro protegido",
            counterpartyName = "Cliente",
            currency = "COP",
            originalAmountCents = 300_000L,
            issuedAtEpochSec = NOW,
            dueAtEpochSec = null,
            obligationCategoryId = OBLIGATION_CATEGORY_ID,
            reference = null,
            notes = null
        )
        return service.registerSettlement(
            userUid = UID,
            obligationId = obligation.id,
            accountId = ACCOUNT_ID,
            financialCategoryId = INCOME_CATEGORY_ID,
            amountCents = 120_000L,
            occurredAtEpochSec = NOW + 1,
            note = "Abono"
        ).transaction
    }

    private suspend fun insertPlainExpense(): TransactionEntity {
        val tx = TransactionEntity(
            id = UUID.randomUUID().toString(),
            userUid = UID,
            accountId = ACCOUNT_ID,
            categoryId = EXPENSE_CATEGORY_ID,
            kind = "EXPENSE",
            amountCents = 5_000L,
            occurredAtEpochSec = NOW,
            note = "Gasto libre",
            createdAtEpochSec = NOW,
            updatedAtEpochSec = NOW,
            updatedBy = "test-device"
        )
        database.transactionDao().insert(tx)
        return tx
    }

    private fun await(desc: String, timeoutMs: Long = 10_000, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (runBlocking { condition() }) return
            if (System.currentTimeMillis() > deadline) {
                fail("timeout esperando: $desc")
            }
            Thread.sleep(15)
        }
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
        private const val UID = "tx-protection-user"
        private const val ACCOUNT_ID = "account-1"
        private const val INCOME_CATEGORY_ID = "category-income"
        private const val EXPENSE_CATEGORY_ID = "category-expense"
        private const val OBLIGATION_CATEGORY_ID = "category-obligation"
        private const val NOW = 1_700_000_000L
        private const val DB_NAME = "tx-protection-vm.db"
        private const val PREFS_NAME = "tx-protection-test"
    }
}
