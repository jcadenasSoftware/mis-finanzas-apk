package com.jcadenas.xpendz.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.data.local.entity.GoalEntity
import com.jcadenas.xpendz.data.local.entity.TransferEntity
import com.jcadenas.xpendz.data.local.entity.UserEntity
import com.jcadenas.xpendz.sync.DeviceIdProvider
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoalRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var databaseName: String
    private lateinit var prefs: SharedPreferences
    private lateinit var remoteStore: FakeGoalRemoteStore
    private lateinit var repository: GoalRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "goals-${UUID.randomUUID()}.db"
        database = openDatabase()
        prefs = context.getSharedPreferences("goal-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        remoteStore = FakeGoalRemoteStore()
        configureRepository()
        runBlocking { database.userDao().upsert(user(UID)) }
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    // --- Creación -----------------------------------------------------------

    @Test
    fun createWithAccountCreatesOpenGoalLinkedToSavingsAccountAtomically() = runBlocking {
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)

        val local = database.goalDao().getById(goal.id)!!
        assertEquals(GoalEntity.STATUS_OPEN, local.status)

        val account = database.accountDao().getById(local.accountId)!!
        assertEquals("SAVINGS", account.type)
        assertEquals("Meta: Viaje", account.name)
        assertEquals(UID, account.userUid)

        assertTrue(remoteStore.upsertedGoals.any { it.id == goal.id })
        assertTrue(remoteStore.upsertedAccounts.any { it.id == account.id })
    }

    @Test
    fun createWithAccountRejectsInvalidData() = runBlocking {
        expectFailure("name") {
            repository.createWithAccount(UID, "  ", "COP", 500_000L, 999L)
        }
        expectFailure("targetCents") {
            repository.createWithAccount(UID, "Viaje", "COP", 0L, 999L)
        }
        assertTrue(database.goalDao().getByUser(UID).isEmpty())
        assertTrue(database.accountDao().getByUser(UID).isEmpty())
    }

    // --- Edición ------------------------------------------------------------

    @Test
    fun updateAllowedOnOpenGoal() = runBlocking {
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)

        val updated = repository.update(UID, goal.id, "Fondo", "USD", 700_000L, 1234L)

        assertEquals("Fondo", updated.name)
        assertEquals("USD", updated.currency)
        assertEquals(700_000L, updated.targetCents)
        assertEquals(1234L, updated.targetDateEpochSec)
        assertEquals(GoalEntity.STATUS_OPEN, updated.status)
        assertTrue(updated.updatedAtEpochSec > goal.updatedAtEpochSec)
    }

    @Test
    fun updateRejectedOnClosedGoal() = runBlocking {
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)
        repository.close(UID, goal.id)

        expectFailure("goal_not_open") {
            repository.update(UID, goal.id, "Fondo", "COP", 700_000L, 1234L)
        }
    }

    // --- Cierre / reapertura ------------------------------------------------

    @Test
    fun closeTransitionsOpenToClosed() = runBlocking {
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)

        val closed = repository.close(UID, goal.id)

        assertEquals(GoalEntity.STATUS_CLOSED, closed.status)
        assertEquals(GoalEntity.STATUS_CLOSED, database.goalDao().getById(goal.id)!!.status)
    }

    @Test
    fun closeRejectedOnAlreadyClosedGoal() = runBlocking {
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)
        repository.close(UID, goal.id)

        expectFailure("goal_not_open") { repository.close(UID, goal.id) }
    }

    @Test
    fun reopenTransitionsClosedToOpen() = runBlocking {
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)
        repository.close(UID, goal.id)

        val reopened = repository.reopen(UID, goal.id)

        assertEquals(GoalEntity.STATUS_OPEN, reopened.status)
        assertEquals(GoalEntity.STATUS_OPEN, database.goalDao().getById(goal.id)!!.status)
    }

    @Test
    fun reopenRejectedOnOpenGoal() = runBlocking {
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)

        expectFailure("goal_not_archived") { repository.reopen(UID, goal.id) }
    }

    // --- Guardas de operaciones ---------------------------------------------

    @Test
    fun requireOpenRejectsClosedGoal() = runBlocking {
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)
        repository.close(UID, goal.id)

        expectFailure("goal_not_open") { repository.requireOpen(goal.id) }
    }

    @Test
    fun requireOpenAllowsOpenGoal() = runBlocking {
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)

        assertEquals(goal.id, repository.requireOpen(goal.id).id)
    }

    // --- Eliminación §8 ------------------------------------------------------

    @Test
    fun deleteGoalWithPositiveBalanceIsRejected() = runBlocking {
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)
        insertSourceAccount()
        insertTransfer(SOURCE_ACCOUNT_ID, goal.accountId, 100_000L)

        expectFailure("goal_has_balance") { repository.deleteGoal(UID, goal.id) }
        assertNotNull(database.goalDao().getById(goal.id))
    }

    @Test
    fun deleteGoalWithZeroBalanceAndNoHistoryPhysicallyDeletesGoalAndAccount() = runBlocking {
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)
        val accountId = goal.accountId

        val outcome = repository.deleteGoal(UID, goal.id)

        assertEquals(GoalDeletionOutcome.DELETED, outcome)
        assertNull(database.goalDao().getById(goal.id))
        assertNull(database.accountDao().getById(accountId))
        assertTrue(remoteStore.deletedGoalIds.contains(goal.id))
        assertTrue(remoteStore.deletedAccountIds.contains(accountId))
    }

    @Test
    fun deleteGoalWithZeroBalanceAndHistoryArchivesInstead() = runBlocking {
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)
        insertSourceAccount()
        insertTransfer(SOURCE_ACCOUNT_ID, goal.accountId, 100_000L)
        insertTransfer(goal.accountId, SOURCE_ACCOUNT_ID, 100_000L)

        val outcome = repository.deleteGoal(UID, goal.id)

        assertEquals(GoalDeletionOutcome.ARCHIVED, outcome)
        val local = database.goalDao().getById(goal.id)!!
        assertEquals(GoalEntity.STATUS_CLOSED, local.status)
        assertNotNull(database.accountDao().getById(goal.accountId))
    }

    // --- Estados --------------------------------------------------------------

    @Test
    fun unknownStatusesNormalizeToOpen() {
        assertEquals(GoalEntity.STATUS_OPEN, GoalEntity.normalizeStatus("FOO"))
        assertEquals(GoalEntity.STATUS_OPEN, GoalEntity.normalizeStatus("DELETED"))
        assertEquals(GoalEntity.STATUS_OPEN, GoalEntity.normalizeStatus(null))
        assertEquals(GoalEntity.STATUS_OPEN, GoalEntity.normalizeStatus(""))
        assertEquals(GoalEntity.STATUS_OPEN, GoalEntity.normalizeStatus("OPEN"))
        assertEquals(GoalEntity.STATUS_CLOSED, GoalEntity.normalizeStatus("CLOSED"))
    }

    @Test
    fun remoteDocWithUnknownStatusIsPersistedAsOpen() = runBlocking {
        insertLinkedAccount(REMOTE_ACCOUNT_ID)
        remoteStore.remoteDocs += remoteDoc(
            id = "remote-1",
            accountId = REMOTE_ACCOUNT_ID,
            status = "WEIRD_STATUS",
            updatedAt = 500L
        )

        repository.syncFromFirestore(UID)

        val local = database.goalDao().getById("remote-1")!!
        assertEquals(GoalEntity.STATUS_OPEN, local.status)
    }

    // --- Pull / pruning -------------------------------------------------------

    @Test
    fun pullAppliesNewerRemoteDocAndSkipsOlderOrEqual() = runBlocking {
        insertLinkedAccount(REMOTE_ACCOUNT_ID)
        val local = goal(id = "local-1", accountId = REMOTE_ACCOUNT_ID, updatedAt = 100L, status = GoalEntity.STATUS_OPEN)
        database.goalDao().insert(local)

        // Remote older: skipped
        remoteStore.remoteDocs += remoteDoc(
            id = "local-1", accountId = REMOTE_ACCOUNT_ID,
            status = GoalEntity.STATUS_CLOSED, updatedAt = 50L
        )
        repository.syncFromFirestore(UID)
        assertEquals(GoalEntity.STATUS_OPEN, database.goalDao().getById("local-1")!!.status)

        // Remote equal timestamp: local wins
        remoteStore.remoteDocs[0] = remoteDoc(
            id = "local-1", accountId = REMOTE_ACCOUNT_ID,
            status = GoalEntity.STATUS_CLOSED, updatedAt = 100L
        )
        repository.syncFromFirestore(UID)
        assertEquals(GoalEntity.STATUS_OPEN, database.goalDao().getById("local-1")!!.status)

        // Remote newer: applied
        remoteStore.remoteDocs[0] = remoteDoc(
            id = "local-1", accountId = REMOTE_ACCOUNT_ID,
            status = GoalEntity.STATUS_CLOSED, updatedAt = 200L
        )
        repository.syncFromFirestore(UID)
        assertEquals(GoalEntity.STATUS_CLOSED, database.goalDao().getById("local-1")!!.status)
    }

    @Test
    fun pullPreservesLocalGoalWithPendingPushWhenAbsentRemotely() = runBlocking {
        remoteStore.failUpsert = true
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)

        // Pull succeeds but remote does not contain the goal
        repository.syncFromFirestore(UID)

        assertNotNull(database.goalDao().getById(goal.id))
    }

    @Test
    fun pullPrunesLocalGoalAbsentRemotelyWhenNoPendingPush() = runBlocking {
        val goal = repository.createWithAccount(UID, "Viaje", "COP", 500_000L, 999L)

        repository.syncFromFirestore(UID)

        assertNull(database.goalDao().getById(goal.id))
    }

    @Test
    fun pullDoesNotTreatUnparseableRemoteDocAsAbsent() = runBlocking {
        insertLinkedAccount(REMOTE_ACCOUNT_ID)
        val local = goal(id = "local-1", accountId = REMOTE_ACCOUNT_ID, updatedAt = 100L, status = GoalEntity.STATUS_OPEN)
        database.goalDao().insert(local)

        // Remote doc exists for the same id but is invalid (missing name)
        remoteStore.remoteDocs += GoalRemoteDoc("local-1", mapOf("currency" to "COP"))

        repository.syncFromFirestore(UID)

        assertNotNull(database.goalDao().getById("local-1"))
    }

    @Test
    fun pullPropagatesFetchFailureToCaller() = runBlocking {
        remoteStore.failFetch = true

        try {
            repository.syncFromFirestore(UID)
            fail("Expected syncFromFirestore to propagate the fetch failure")
        } catch (e: IOException) {
            // expected
        }
    }

    // --- Helpers --------------------------------------------------------------

    private fun openDatabase(): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        databaseName
    ).allowMainThreadQueries().build()

    private fun configureRepository() {
        repository = GoalRepository(
            database,
            database.goalDao(),
            database.accountDao(),
            remoteStore,
            DeviceIdProvider(context),
            prefs
        )
    }

    private fun user(uid: String) = UserEntity(
        uid = uid,
        email = "test@example.com",
        createdAtEpochSec = 0L,
        updatedAtEpochSec = 0L
    )

    private fun account(id: String, type: String = "SAVINGS") = AccountEntity(
        id = id,
        userUid = UID,
        name = id,
        type = type,
        currency = "COP",
        iconKey = null,
        colorHex = null,
        createdAtEpochSec = 0L,
        updatedAtEpochSec = 0L,
        updatedBy = "test"
    )

    private fun goal(id: String, accountId: String, updatedAt: Long, status: String) = GoalEntity(
        id = id,
        userUid = UID,
        name = "Meta $id",
        currency = "COP",
        targetCents = 500_000L,
        targetDateEpochSec = 999L,
        accountId = accountId,
        status = status,
        createdAtEpochSec = 0L,
        updatedAtEpochSec = updatedAt,
        updatedBy = "test"
    )

    private fun remoteDoc(
        id: String,
        accountId: String,
        status: String,
        updatedAt: Long
    ) = GoalRemoteDoc(
        id = id,
        fields = mapOf(
            "name" to "Meta $id",
            "currency" to "COP",
            "targetCents" to 500_000L,
            "targetDateEpochSec" to 999L,
            "accountId" to accountId,
            "status" to status,
            "createdAtEpochSec" to 0L,
            "updatedAtEpochSec" to updatedAt,
            "updatedBy" to "remote"
        )
    )

    private suspend fun insertSourceAccount() {
        database.accountDao().insert(account(SOURCE_ACCOUNT_ID, type = "BANK"))
    }

    private suspend fun insertLinkedAccount(accountId: String) {
        database.accountDao().insert(account(accountId, type = "SAVINGS"))
    }

    private suspend fun insertTransfer(fromAccountId: String, toAccountId: String, cents: Long) {
        database.transferDao().insert(
            TransferEntity(
                id = UUID.randomUUID().toString(),
                userUid = UID,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amountCents = cents,
                occurredAtEpochSec = 1000L,
                note = null,
                createdAtEpochSec = 1000L,
                updatedAtEpochSec = 1000L,
                updatedBy = "test"
            )
        )
    }

    private suspend fun expectFailure(expectedMessage: String, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected failure with message '$expectedMessage'")
        } catch (e: Exception) {
            assertEquals(expectedMessage, e.message)
        }
    }

    private class FakeGoalRemoteStore : GoalRemoteStore {
        val upsertedGoals = mutableListOf<GoalEntity>()
        val upsertedAccounts = mutableListOf<AccountEntity>()
        val deletedGoalIds = mutableListOf<String>()
        val deletedAccountIds = mutableListOf<String>()
        var remoteDocs = mutableListOf<GoalRemoteDoc>()
        var failUpsert = false
        var failFetch = false

        override suspend fun upsertGoal(userUid: String, goal: GoalEntity) {
            if (failUpsert) throw IOException("offline")
            upsertedGoals += goal
        }

        override suspend fun deleteGoal(userUid: String, goalId: String) {
            deletedGoalIds += goalId
        }

        override suspend fun fetchGoals(userUid: String): List<GoalRemoteDoc> {
            if (failFetch) throw IOException("network")
            return remoteDocs
        }

        override suspend fun deleteAllGoalsByUser(userUid: String) {
            remoteDocs.clear()
        }

        override suspend fun upsertGoalAccount(userUid: String, account: AccountEntity) {
            if (failUpsert) throw IOException("offline")
            upsertedAccounts += account
        }

        override suspend fun deleteGoalAccount(userUid: String, accountId: String) {
            deletedAccountIds += accountId
        }
    }

    companion object {
        private const val UID = "user-1"
        private const val SOURCE_ACCOUNT_ID = "source-account"
        private const val REMOTE_ACCOUNT_ID = "remote-account"
    }
}
