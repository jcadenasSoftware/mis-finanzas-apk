package com.jcadenas.xpendz.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.infrastructure.loan.admin.LoanAdminStateEntity
import com.jcadenas.xpendz.sync.DeviceIdProvider
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoanAdminStateRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var databaseName: String
    private lateinit var publisher: RecordingPublisher
    private lateinit var repository: LoanAdminStateRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "loan-admin-${UUID.randomUUID()}.db"
        database = openDatabase()
        configureRepository()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun archiveMarksPendingPublishesAndMarksSynced() = runBlocking {
        repository.archive(OWNER_ID, LOAN_ID, "device-1")

        val published = publisher.states.single()
        assertTrue(published.pendingSync)
        assertTrue(published.archived)
        assertEquals("device-1", published.updatedBy)

        val local = database.loanAdminStateDao().getByLoan(OWNER_ID, LOAN_ID)!!
        assertTrue(local.archived)
        assertFalse(local.pendingSync)
        assertEquals(published.archivedAtEpochSec, local.archivedAtEpochSec)
    }

    @Test
    fun failedPublishLeavesPendingAndNextSyncRetriesWithoutDuplicates() = runBlocking {
        publisher.fail = true
        repository.archive(OWNER_ID, LOAN_ID, "device-1")

        var local = database.loanAdminStateDao().getByLoan(OWNER_ID, LOAN_ID)!!
        assertTrue(local.pendingSync)
        assertEquals(1, database.loanAdminStateDao().listPendingForSync(OWNER_ID).size)

        publisher.fail = false
        repository.syncPendingToFirestore(OWNER_ID)
        repository.syncPendingToFirestore(OWNER_ID)

        local = database.loanAdminStateDao().getByLoan(OWNER_ID, LOAN_ID)!!
        assertTrue(local.archived)
        assertFalse(local.pendingSync)
        assertTrue(database.loanAdminStateDao().listPendingForSync(OWNER_ID).isEmpty())
        assertEquals(2, publisher.states.size)
    }

    @Test
    fun remoteUpsertRespectsTimestampOrderingAndIsIdempotent() = runBlocking {
        val localPending = LoanAdminStateEntity(
            loanId = LOAN_ID,
            ownerId = OWNER_ID,
            archived = true,
            archivedAtEpochSec = 200L,
            updatedAtEpochSec = 200L,
            updatedBy = "android",
            pendingSync = true
        )
        database.loanAdminStateDao().upsert(localPending)

        repository.upsertFromRemote(
            localPending.copy(
                archived = false,
                archivedAtEpochSec = null,
                updatedAtEpochSec = 100L,
                updatedBy = "desktop",
                pendingSync = false
            )
        )
        assertEquals(localPending, database.loanAdminStateDao().getByLoan(OWNER_ID, LOAN_ID))

        val remote = localPending.copy(
            updatedBy = "desktop",
            pendingSync = false
        )
        repository.upsertFromRemote(remote)
        repository.upsertFromRemote(remote)

        val local = database.loanAdminStateDao().getByLoan(OWNER_ID, LOAN_ID)!!
        assertEquals(localPending, local)
        assertTrue(local.pendingSync)
    }

    @Test
    fun archivePersistsAfterDatabaseReopen() = runBlocking {
        repository.archive(OWNER_ID, LOAN_ID, "device-1")

        database.close()
        database = openDatabase()

        val restored = database.loanAdminStateDao().getByLoan(OWNER_ID, LOAN_ID)!!
        assertTrue(restored.archived)
        assertFalse(restored.pendingSync)
        assertEquals("device-1", restored.updatedBy)
    }

    private fun openDatabase(): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        databaseName
    ).allowMainThreadQueries().build()

    private fun configureRepository() {
        publisher = RecordingPublisher()
        repository = LoanAdminStateRepository(
            database.loanAdminStateDao(),
            publisher,
            DeviceIdProvider(context)
        )
    }

    private class RecordingPublisher : LoanAdminStateRemotePublisher {
        val states = mutableListOf<LoanAdminStateEntity>()
        var fail = false

        override suspend fun publish(state: LoanAdminStateEntity) {
            states += state
            if (fail) throw IOException("offline")
        }
    }

    companion object {
        private const val OWNER_ID = "owner-1"
        private const val LOAN_ID = "loan-1"
    }
}
