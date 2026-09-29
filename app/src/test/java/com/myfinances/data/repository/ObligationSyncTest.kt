package com.jcadenas.xpendz.data.repository

import android.content.Context
import android.content.SharedPreferences
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ObligationSyncTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var databaseName: String
    private lateinit var prefs: SharedPreferences
    private lateinit var obligationRemote: FakeObligationRemoteStore
    private lateinit var settlementRemote: FakeObligationSettlementRemoteStore
    private lateinit var obligationRepository: ObligationRepository
    private lateinit var settlementRepository: ObligationSettlementRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "os.db"
        context.deleteDatabase(databaseName)
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        prefs = context.getSharedPreferences("obligation-sync-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        obligationRemote = FakeObligationRemoteStore()
        settlementRemote = FakeObligationSettlementRemoteStore()
        val deviceIdProvider = DeviceIdProvider(context)
        obligationRepository = ObligationRepository(
            database.obligationDao(),
            database.obligationSettlementDao(),
            obligationRemote,
            deviceIdProvider,
            prefs
        )
        settlementRepository = ObligationSettlementRepository(
            database.obligationSettlementDao(),
            database.obligationDao(),
            database.transactionDao(),
            settlementRemote,
            deviceIdProvider,
            prefs
        )

        database.userDao().upsert(UserEntity(UID, "sync@test.dev", NOW, NOW))
        database.accountDao().insert(account(ACCOUNT_ID))
        database.categoryDao().insert(category(CATEGORY_ID, "Ventas", "INCOME"))
        database.categoryDao().insert(category(OBLIGATION_CATEGORY_ID, "Clientes", "BOTH"))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
        prefs.edit().clear().commit()
    }

    // ---------- Obligaciones: push ----------

    @Test
    fun localObligationPushesToRemoteOnCreate() = runBlocking {
        val created = obligationRepository.create(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Factura",
            counterpartyName = "Pedro",
            currency = "COP",
            originalAmountCents = 500_000L,
            issuedAtEpochSec = NOW,
            dueAtEpochSec = NOW + 100,
            obligationCategoryId = OBLIGATION_CATEGORY_ID,
            reference = "FAC-1",
            notes = "nota"
        )

        val remoteDoc = obligationRemote.docs[created.id]
        assertNotNull(remoteDoc)
        assertEquals(created.id, remoteDoc?.get("id"))
        assertEquals("Factura", remoteDoc?.get("title"))
        assertEquals(500_000L, remoteDoc?.get("originalAmountCents"))
        assertEquals(0, database.transactionDao().getByUser(UID).size)
    }

    @Test
    fun failedObligationPushIsRetriedOnNextSync() = runBlocking {
        obligationRemote.failUpserts = true
        val created = obligationRepository.create(
            userUid = UID,
            type = ObligationEntity.TYPE_PAYABLE,
            title = "Pendiente",
            counterpartyName = "Proveedor",
            currency = "COP",
            originalAmountCents = 100_000L,
            issuedAtEpochSec = NOW
        )
        assertNull(obligationRemote.docs[created.id])

        obligationRemote.failUpserts = false
        obligationRepository.syncFromFirestore(UID)

        assertNotNull(obligationRemote.docs[created.id])
    }

    @Test
    fun pendingPushObligationSurvivesRemoteAbsence() = runBlocking {
        obligationRemote.failUpserts = true
        val created = obligationRepository.create(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Aun no publicada",
            counterpartyName = "Pedro",
            currency = "COP",
            originalAmountCents = 200_000L,
            issuedAtEpochSec = NOW
        )
        obligationRemote.failUpserts = false

        // El snapshot remoto (autoritativo, vacío) llega sin la obligación pendiente.
        obligationRepository.syncFromFirestore(UID)

        assertNotNull(database.obligationDao().getById(created.id))
        // Y el push pendiente la publicó antes de evaluar el snapshot.
        assertNotNull(obligationRemote.docs[created.id])
    }

    // ---------- Obligaciones: pull/merge ----------

    @Test
    fun remoteObligationAppliesLocallyWithSameId() = runBlocking {
        obligationRemote.docs["obl-1"] = obligationDoc(
            id = "obl-1",
            title = "Remota",
            counterpartyName = "Cliente",
            type = ObligationEntity.TYPE_RECEIVABLE,
            originalAmountCents = 750_000L,
            updatedAtEpochSec = NOW,
            updatedBy = "desktop-device"
        )

        obligationRepository.syncFromFirestore(UID)

        val local = database.obligationDao().getById("obl-1")
        assertNotNull(local)
        assertEquals("Remota", local?.title)
        assertEquals(750_000L, local?.originalAmountCents)
        assertEquals(ObligationEntity.TYPE_RECEIVABLE, local?.type)
    }

    @Test
    fun remoteNewerObligationWinsConflict() = runBlocking {
        database.obligationDao().insert(
            obligation(
                id = "obl-conflict",
                title = "Local viejo",
                updatedAtEpochSec = NOW,
                updatedBy = "device-local"
            )
        )
        obligationRemote.docs["obl-conflict"] = obligationDoc(
            id = "obl-conflict",
            title = "Remoto nuevo",
            updatedAtEpochSec = NOW + 50,
            updatedBy = "device-remote"
        )

        obligationRepository.syncFromFirestore(UID)

        assertEquals("Remoto nuevo", database.obligationDao().getById("obl-conflict")?.title)
    }

    @Test
    fun olderRemoteObligationLosesConflict() = runBlocking {
        database.obligationDao().insert(
            obligation(
                id = "obl-conflict",
                title = "Local nuevo",
                updatedAtEpochSec = NOW + 50,
                updatedBy = "device-local"
            )
        )
        obligationRemote.docs["obl-conflict"] = obligationDoc(
            id = "obl-conflict",
            title = "Remoto viejo",
            updatedAtEpochSec = NOW,
            updatedBy = "device-remote"
        )

        obligationRepository.syncFromFirestore(UID)

        assertEquals("Local nuevo", database.obligationDao().getById("obl-conflict")?.title)
    }

    @Test
    fun equalTimestampConflictResolvesByUpdatedByDeterministically() = runBlocking {
        database.obligationDao().insert(
            obligation(
                id = "obl-tie",
                title = "Autor A",
                updatedAtEpochSec = NOW,
                updatedBy = "aaa-device"
            )
        )
        obligationRemote.docs["obl-tie"] = obligationDoc(
            id = "obl-tie",
            title = "Autor Z",
            updatedAtEpochSec = NOW,
            updatedBy = "zzz-device"
        )
        obligationRepository.syncFromFirestore(UID)
        assertEquals("Autor Z", database.obligationDao().getById("obl-tie")?.title)

        obligationRemote.docs["obl-tie-2"] = obligationDoc(
            id = "obl-tie-2",
            title = "Autor A remoto",
            updatedAtEpochSec = NOW,
            updatedBy = "aaa-device"
        )
        database.obligationDao().insert(
            obligation(
                id = "obl-tie-2",
                title = "Autor Z local",
                updatedAtEpochSec = NOW,
                updatedBy = "zzz-device"
            )
        )
        obligationRepository.syncFromFirestore(UID)
        // zzz-device local gana el empate → remoto no sobrescribe
        assertEquals("Autor Z local", database.obligationDao().getById("obl-tie-2")?.title)
    }

    @Test
    fun cancellationPropagatesFromRemote() = runBlocking {
        database.obligationDao().insert(
            obligation(id = "obl-cancel", title = "Por cancelar", updatedAtEpochSec = NOW)
        )
        obligationRemote.docs["obl-cancel"] = obligationDoc(
            id = "obl-cancel",
            title = "Por cancelar",
            cancelledAtEpochSec = NOW + 30,
            updatedAtEpochSec = NOW + 30,
            updatedBy = "desktop"
        )

        obligationRepository.syncFromFirestore(UID)

        assertEquals(NOW + 30, database.obligationDao().getById("obl-cancel")?.cancelledAtEpochSec)
    }

    @Test
    fun repeatedSyncCyclesStayIdempotentForObligations() = runBlocking {
        obligationRepository.create(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Una",
            counterpartyName = "Pedro",
            currency = "COP",
            originalAmountCents = 100_000L,
            issuedAtEpochSec = NOW
        )
        obligationRemote.docs["obl-remote"] = obligationDoc(
            id = "obl-remote",
            title = "Remota",
            updatedAtEpochSec = NOW
        )

        repeat(5) { obligationRepository.syncFromFirestore(UID) }

        val locals = database.obligationDao().getByUser(UID)
        assertEquals(2, locals.size)
        assertEquals(1, locals.count { it.id == "obl-remote" })
    }

    @Test
    fun cachedSnapshotNeverPrunesLocalObligations() = runBlocking {
        val created = obligationRepository.create(
            userUid = UID,
            type = ObligationEntity.TYPE_RECEIVABLE,
            title = "Local",
            counterpartyName = "Pedro",
            currency = "COP",
            originalAmountCents = 100_000L,
            issuedAtEpochSec = NOW
        )
        obligationRemote.docs.clear()
        obligationRemote.isFromCache = true

        obligationRepository.syncFromFirestore(UID)

        assertNotNull(database.obligationDao().getById(created.id))
    }

    @Test
    fun obligationWithLocalSettlementsIsNotPruned() = runBlocking {
        database.obligationDao().insert(obligation(id = "obl-with-settlements", title = "Con abonos"))
        database.transactionDao().insert(transaction("tx-keep", 50_000L))
        database.obligationSettlementDao().insert(
            settlement("set-keep", "obl-with-settlements", 50_000L, "tx-keep")
        )

        obligationRepository.syncFromFirestore(UID)

        assertNotNull(database.obligationDao().getById("obl-with-settlements"))
    }

    // ---------- Settlements: pull/merge ----------

    @Test
    fun remoteSettlementAppliesWhenDependenciesExist() = runBlocking {
        database.obligationDao().insert(obligation(id = "obl-1", title = "Base"))
        database.transactionDao().insert(transaction("tx-1", 80_000L))
        settlementRemote.settlements["set-1"] = settlementDoc(
            id = "set-1",
            obligationId = "obl-1",
            accountId = ACCOUNT_ID,
            amountCents = 80_000L,
            linkedTransactionId = "tx-1",
            updatedAtEpochSec = NOW
        )

        settlementRepository.syncFromFirestore(UID)

        val local = database.obligationSettlementDao().getById("set-1")
        assertNotNull(local)
        assertEquals("tx-1", local?.linkedTransactionId)
        assertEquals("obl-1", local?.obligationId)
        assertEquals(1, database.transactionDao().getByUser(UID).size)
    }

    @Test
    fun settlementWithoutObligationIsDeferredNotMaterialized() = runBlocking {
        database.transactionDao().insert(transaction("tx-orphan-dep", 10_000L))
        settlementRemote.settlements["set-orphan"] = settlementDoc(
            id = "set-orphan",
            obligationId = "obl-missing",
            accountId = ACCOUNT_ID,
            amountCents = 10_000L,
            linkedTransactionId = "tx-orphan-dep",
            updatedAtEpochSec = NOW
        )

        settlementRepository.syncFromFirestore(UID)

        assertNull(database.obligationSettlementDao().getById("set-orphan"))
    }

    @Test
    fun settlementWithoutTransactionNeverCreatesOne() = runBlocking {
        database.obligationDao().insert(obligation(id = "obl-1", title = "Base"))
        settlementRemote.settlements["set-no-tx"] = settlementDoc(
            id = "set-no-tx",
            obligationId = "obl-1",
            accountId = ACCOUNT_ID,
            amountCents = 10_000L,
            linkedTransactionId = "tx-does-not-exist",
            updatedAtEpochSec = NOW
        )

        settlementRepository.syncFromFirestore(UID)

        assertNull(database.obligationSettlementDao().getById("set-no-tx"))
        assertEquals(0, database.transactionDao().getByUser(UID).size)
    }

    @Test
    fun settlementLinkedToTransactionOwnedByAnotherSettlementIsRejected() = runBlocking {
        database.obligationDao().insert(obligation(id = "obl-1", title = "Base"))
        database.transactionDao().insert(transaction("tx-shared", 40_000L))
        database.obligationSettlementDao().insert(
            settlement("set-owner", "obl-1", 40_000L, "tx-shared")
        )
        settlementRemote.settlements["set-owner"] = settlementDoc(
            id = "set-owner",
            obligationId = "obl-1",
            accountId = ACCOUNT_ID,
            amountCents = 40_000L,
            linkedTransactionId = "tx-shared",
            updatedAtEpochSec = NOW
        )
        settlementRemote.settlements["set-intruder"] = settlementDoc(
            id = "set-intruder",
            obligationId = "obl-1",
            accountId = ACCOUNT_ID,
            amountCents = 40_000L,
            linkedTransactionId = "tx-shared",
            updatedAtEpochSec = NOW + 100
        )

        settlementRepository.syncFromFirestore(UID)

        assertNull(database.obligationSettlementDao().getById("set-intruder"))
        assertEquals("set-owner", database.obligationSettlementDao().getByLinkedTransactionId("tx-shared")?.id)
    }

    @Test
    fun remoteCannotRepointLinkedTransactionOfExistingSettlement() = runBlocking {
        database.obligationDao().insert(obligation(id = "obl-1", title = "Base"))
        database.transactionDao().insert(transaction("tx-orig", 40_000L))
        database.transactionDao().insert(transaction("tx-other", 40_000L))
        database.obligationSettlementDao().insert(
            settlement("set-1", "obl-1", 40_000L, "tx-orig", updatedAtEpochSec = NOW)
        )
        settlementRemote.settlements["set-1"] = settlementDoc(
            id = "set-1",
            obligationId = "obl-1",
            accountId = ACCOUNT_ID,
            amountCents = 40_000L,
            linkedTransactionId = "tx-other",
            updatedAtEpochSec = NOW + 100
        )

        settlementRepository.syncFromFirestore(UID)

        assertEquals("tx-orig", database.obligationSettlementDao().getById("set-1")?.linkedTransactionId)
    }

    @Test
    fun remoteSettlementEditUpdatesSameSettlementAndKeepsLinkedTransaction() = runBlocking {
        database.obligationDao().insert(obligation(id = "obl-1", title = "Base"))
        database.transactionDao().insert(transaction("tx-1", 40_000L))
        database.obligationSettlementDao().insert(
            settlement("set-1", "obl-1", 40_000L, "tx-1", updatedAtEpochSec = NOW)
        )
        settlementRemote.settlements["set-1"] = settlementDoc(
            id = "set-1",
            obligationId = "obl-1",
            accountId = ACCOUNT_ID,
            amountCents = 60_000L,
            linkedTransactionId = "tx-1",
            updatedAtEpochSec = NOW + 10
        )

        settlementRepository.syncFromFirestore(UID)

        val updated = database.obligationSettlementDao().getById("set-1")
        assertEquals(60_000L, updated?.amountCents)
        assertEquals("tx-1", updated?.linkedTransactionId)
        assertEquals(1, database.transactionDao().getByUser(UID).size)
    }

    @Test
    fun remoteSettlementAbsenceDeletesSettlementAndLinkedTransaction() = runBlocking {
        database.obligationDao().insert(obligation(id = "obl-1", title = "Base"))
        database.transactionDao().insert(transaction("tx-1", 40_000L))
        database.obligationSettlementDao().insert(
            settlement("set-1", "obl-1", 40_000L, "tx-1")
        )
        settlementRemote.settlements.clear()
        settlementRemote.transactions.clear()

        settlementRepository.syncFromFirestore(UID)

        assertNull(database.obligationSettlementDao().getById("set-1"))
        assertNull(database.transactionDao().getById("tx-1"))
        assertEquals(1, settlementRemote.deleteSettlementCalls)
        assertEquals(1, settlementRemote.deleteTransactionCalls)
    }

    @Test
    fun repeatedSettlementSyncNeverCreatesTransactions() = runBlocking {
        database.obligationDao().insert(obligation(id = "obl-1", title = "Base"))
        database.transactionDao().insert(transaction("tx-1", 40_000L))
        settlementRemote.settlements["set-1"] = settlementDoc(
            id = "set-1",
            obligationId = "obl-1",
            accountId = ACCOUNT_ID,
            amountCents = 40_000L,
            linkedTransactionId = "tx-1",
            updatedAtEpochSec = NOW
        )

        repeat(5) { settlementRepository.syncFromFirestore(UID) }

        assertEquals(1, database.obligationSettlementDao().getByUser(UID).size)
        assertEquals(1, database.transactionDao().getByUser(UID).size)
    }

    @Test
    fun cachedSettlementSnapshotNeverPrunes() = runBlocking {
        database.obligationDao().insert(obligation(id = "obl-1", title = "Base"))
        database.transactionDao().insert(transaction("tx-1", 40_000L))
        database.obligationSettlementDao().insert(
            settlement("set-1", "obl-1", 40_000L, "tx-1")
        )
        settlementRemote.settlements.clear()
        settlementRemote.isFromCache = true

        settlementRepository.syncFromFirestore(UID)

        assertNotNull(database.obligationSettlementDao().getById("set-1"))
        assertNotNull(database.transactionDao().getById("tx-1"))
    }

    @Test
    fun settlementEditConflictResolvesByTimestamp() = runBlocking {
        database.obligationDao().insert(obligation(id = "obl-1", title = "Base"))
        database.transactionDao().insert(transaction("tx-1", 40_000L))
        database.obligationSettlementDao().insert(
            settlement("set-1", "obl-1", 40_000L, "tx-1", updatedAtEpochSec = NOW + 50)
        )
        settlementRemote.settlements["set-1"] = settlementDoc(
            id = "set-1",
            obligationId = "obl-1",
            accountId = ACCOUNT_ID,
            amountCents = 99_000L,
            linkedTransactionId = "tx-1",
            updatedAtEpochSec = NOW
        )

        settlementRepository.syncFromFirestore(UID)

        assertEquals(40_000L, database.obligationSettlementDao().getById("set-1")?.amountCents)
    }

    // ---------- Fixtures ----------

    private fun account(id: String) = AccountEntity(
        id = id,
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

    private fun transaction(id: String, amountCents: Long) = TransactionEntity(
        id = id,
        userUid = UID,
        accountId = ACCOUNT_ID,
        categoryId = CATEGORY_ID,
        kind = "INCOME",
        amountCents = amountCents,
        occurredAtEpochSec = NOW,
        note = null,
        createdAtEpochSec = NOW,
        updatedAtEpochSec = NOW,
        updatedBy = "test-device"
    )

    private fun obligation(
        id: String,
        title: String,
        updatedAtEpochSec: Long = NOW,
        updatedBy: String = "test-device"
    ) = ObligationEntity(
        id = id,
        userUid = UID,
        type = ObligationEntity.TYPE_RECEIVABLE,
        title = title,
        counterpartyName = "Pedro",
        notes = null,
        reference = null,
        obligationCategoryId = OBLIGATION_CATEGORY_ID,
        currency = "COP",
        originalAmountCents = 500_000L,
        issuedAtEpochSec = NOW,
        dueAtEpochSec = null,
        cancelledAtEpochSec = null,
        createdAtEpochSec = NOW,
        updatedAtEpochSec = updatedAtEpochSec,
        updatedBy = updatedBy
    )

    private fun settlement(
        id: String,
        obligationId: String,
        amountCents: Long,
        linkedTransactionId: String,
        updatedAtEpochSec: Long = NOW
    ) = ObligationSettlementEntity(
        id = id,
        userUid = UID,
        obligationId = obligationId,
        accountId = ACCOUNT_ID,
        amountCents = amountCents,
        occurredAtEpochSec = NOW,
        linkedTransactionId = linkedTransactionId,
        note = null,
        createdAtEpochSec = NOW,
        updatedAtEpochSec = updatedAtEpochSec,
        updatedBy = "test-device"
    )

    private fun obligationDoc(
        id: String,
        title: String,
        counterpartyName: String = "Pedro",
        type: String = ObligationEntity.TYPE_RECEIVABLE,
        originalAmountCents: Long = 500_000L,
        cancelledAtEpochSec: Long? = null,
        updatedAtEpochSec: Long,
        updatedBy: String = "remote-device"
    ): Map<String, Any?> = mapOf(
        "id" to id,
        "userUid" to UID,
        "type" to type,
        "title" to title,
        "counterpartyName" to counterpartyName,
        "notes" to null,
        "reference" to null,
        "obligationCategoryId" to OBLIGATION_CATEGORY_ID,
        "currency" to "COP",
        "originalAmountCents" to originalAmountCents,
        "issuedAtEpochSec" to NOW,
        "dueAtEpochSec" to null,
        "cancelledAtEpochSec" to cancelledAtEpochSec,
        "createdAtEpochSec" to NOW,
        "updatedAtEpochSec" to updatedAtEpochSec,
        "updatedBy" to updatedBy
    )

    private fun settlementDoc(
        id: String,
        obligationId: String,
        accountId: String,
        amountCents: Long,
        linkedTransactionId: String,
        updatedAtEpochSec: Long
    ): Map<String, Any?> = mapOf(
        "id" to id,
        "obligationId" to obligationId,
        "userUid" to UID,
        "accountId" to accountId,
        "amountCents" to amountCents,
        "occurredAtEpochSec" to NOW,
        "linkedTransactionId" to linkedTransactionId,
        "note" to null,
        "createdAtEpochSec" to NOW,
        "updatedAtEpochSec" to updatedAtEpochSec,
        "updatedBy" to "remote-device"
    )

    companion object {
        private const val UID = "obligation-sync-user"
        private const val ACCOUNT_ID = "account-1"
        private const val CATEGORY_ID = "category-1"
        private const val OBLIGATION_CATEGORY_ID = "category-2"
        private const val NOW = 1_700_000_000L
    }
}
