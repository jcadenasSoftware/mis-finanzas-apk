package com.jcadenas.xpendz.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jcadenas.xpendz.domain.loan.admin.LoanAdminState
import com.jcadenas.xpendz.infrastructure.loan.admin.LoanAdminStateDao
import com.jcadenas.xpendz.infrastructure.loan.admin.LoanAdminStateEntity
import com.jcadenas.xpendz.infrastructure.loan.sync.LoanMergePolicy
import com.jcadenas.xpendz.sync.DeviceIdProvider
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

interface LoanAdminStateRemotePublisher {
    suspend fun publish(state: LoanAdminStateEntity)
}

@Singleton
class FirestoreLoanAdminStateRemotePublisher @Inject constructor(
    private val firestore: FirebaseFirestore
) : LoanAdminStateRemotePublisher {
    override suspend fun publish(state: LoanAdminStateEntity) {
        val data = hashMapOf<String, Any>(
            "id" to state.loanId,
            "userUid" to state.ownerId,
            "archived" to state.archived,
            "updatedAtEpochSec" to state.updatedAtEpochSec
        )
        data["archivedAtEpochSec"] = state.archivedAtEpochSec ?: FieldValue.delete()
        data["updatedBy"] = state.updatedBy?.takeIf { it.isNotBlank() } ?: FieldValue.delete()

        firestore.collection("users")
            .document(state.ownerId)
            .collection("loans")
            .document(state.loanId)
            .set(data, SetOptions.merge())
            .await()
        firestore.waitForPendingWrites().await()
    }
}

@Singleton
class LoanAdminStateRepository @Inject constructor(
    private val loanAdminStateDao: LoanAdminStateDao,
    private val remotePublisher: LoanAdminStateRemotePublisher,
    private val deviceIdProvider: DeviceIdProvider
) {
    suspend fun archive(ownerId: String, loanId: String, updatedBy: String? = null): LoanAdminState {
        val now = System.currentTimeMillis() / 1000
        val state = LoanAdminStateEntity(
            loanId = loanId,
            ownerId = ownerId,
            archived = true,
            archivedAtEpochSec = now,
            updatedAtEpochSec = now,
            updatedBy = updatedBy ?: deviceIdProvider.get(),
            pendingSync = true
        )
        loanAdminStateDao.upsert(state)
        publishAndMarkSynced(state)
        return state.toDomain()
    }

    suspend fun upsertFromRemote(state: LoanAdminStateEntity): LoanAdminStateEntity {
        val local = loanAdminStateDao.getByLoan(state.ownerId, state.loanId)
        if (!LoanMergePolicy.shouldAcceptRemote(local, state)) {
            return local ?: state.copy(pendingSync = false)
        }
        val remote = state.copy(pendingSync = false)
        loanAdminStateDao.upsert(remote)
        return remote
    }

    suspend fun syncPendingToFirestore(ownerId: String) {
        val pending = loanAdminStateDao.listPendingForSync(ownerId)
        for (state in pending) {
            publishAndMarkSynced(state)
        }
    }

    suspend fun publishLoanAdminState(state: LoanAdminStateEntity) {
        remotePublisher.publish(state)
    }

    private suspend fun publishAndMarkSynced(state: LoanAdminStateEntity) {
        try {
            publishLoanAdminState(state)
            loanAdminStateDao.markSynced(state.ownerId, state.loanId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("LoanAdminStateRepo", "Admin state pendiente loanId=${state.loanId}", e)
        }
    }

    private fun LoanAdminStateEntity.toDomain(): LoanAdminState = LoanAdminState(
        loanId = loanId,
        ownerId = ownerId,
        archived = archived,
        archivedAtEpochSec = archivedAtEpochSec,
        updatedAtEpochSec = updatedAtEpochSec,
        updatedBy = updatedBy
    )
}
