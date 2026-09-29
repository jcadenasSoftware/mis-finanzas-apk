package com.jcadenas.xpendz.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.jcadenas.xpendz.data.local.dao.ObligationDao
import com.jcadenas.xpendz.data.local.dao.ObligationSettlementDao
import com.jcadenas.xpendz.data.local.entity.ObligationEntity
import com.jcadenas.xpendz.infrastructure.obligation.sync.ObligationMergePolicy
import com.jcadenas.xpendz.sync.DeviceIdProvider
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

data class ObligationRemoteDoc(
    val id: String,
    val fields: Map<String, Any?>
)

data class ObligationRemoteSnapshot(
    val documents: List<ObligationRemoteDoc>,
    val isFromCache: Boolean
)

interface ObligationRemoteStore {
    suspend fun upsertObligation(userUid: String, obligation: ObligationEntity)
    suspend fun deleteObligation(userUid: String, obligationId: String)
    suspend fun fetchObligations(userUid: String): ObligationRemoteSnapshot
    suspend fun deleteAllObligationsByUser(userUid: String)
}

@Singleton
class FirestoreObligationRemoteStore @Inject constructor(
    private val firestore: FirebaseFirestore
) : ObligationRemoteStore {

    private fun collectionRef(userUid: String) =
        firestore.collection("users").document(userUid).collection("obligations")

    override suspend fun upsertObligation(userUid: String, obligation: ObligationEntity) {
        collectionRef(userUid).document(obligation.id)
            .set(obligation, SetOptions.merge())
            .await()
    }

    override suspend fun deleteObligation(userUid: String, obligationId: String) {
        collectionRef(userUid).document(obligationId).delete().await()
    }

    override suspend fun fetchObligations(userUid: String): ObligationRemoteSnapshot {
        val snapshot = collectionRef(userUid).get().await()
        val docs = snapshot.documents.mapNotNull { doc ->
            val data = doc.data
            if (data == null) null else ObligationRemoteDoc(id = doc.id, fields = data)
        }
        return ObligationRemoteSnapshot(
            documents = docs,
            isFromCache = snapshot.metadata.isFromCache
        )
    }

    override suspend fun deleteAllObligationsByUser(userUid: String) {
        val collectionRef = collectionRef(userUid)
        val batchSize = 200
        while (true) {
            val snapshot = collectionRef.limit(batchSize.toLong()).get(Source.SERVER).await()
            if (snapshot.isEmpty) break
            val batch = firestore.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            if (snapshot.size() < batchSize) break
        }
    }
}

@Singleton
class ObligationRepository @Inject constructor(
    private val obligationDao: ObligationDao,
    private val obligationSettlementDao: ObligationSettlementDao,
    private val remoteStore: ObligationRemoteStore,
    private val deviceIdProvider: DeviceIdProvider,
    private val sharedPreferences: SharedPreferences
) {
    fun observeByUser(userUid: String): Flow<List<ObligationEntity>> = obligationDao.observeByUser(userUid)

    suspend fun getByUser(userUid: String): List<ObligationEntity> = obligationDao.getByUser(userUid)

    suspend fun getById(id: String): ObligationEntity? = obligationDao.getById(id)

    suspend fun create(
        userUid: String,
        type: String,
        title: String,
        counterpartyName: String,
        currency: String,
        originalAmountCents: Long,
        issuedAtEpochSec: Long,
        dueAtEpochSec: Long? = null,
        obligationCategoryId: String? = null,
        reference: String? = null,
        notes: String? = null
    ): ObligationEntity {
        require(userUid.isNotBlank()) { "userUid" }
        require(title.isNotBlank()) { "title" }
        require(counterpartyName.isNotBlank()) { "counterpartyName" }
        require(currency.isNotBlank()) { "currency" }
        require(originalAmountCents > 0L) { "originalAmountCents" }

        val now = System.currentTimeMillis() / 1000
        val obligation = ObligationEntity(
            id = UUID.randomUUID().toString(),
            userUid = userUid,
            type = ObligationEntity.normalizeType(type),
            title = title.trim(),
            counterpartyName = counterpartyName.trim(),
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            reference = reference?.trim()?.takeIf { it.isNotEmpty() },
            obligationCategoryId = obligationCategoryId?.trim()?.takeIf { it.isNotEmpty() },
            currency = currency.trim(),
            originalAmountCents = originalAmountCents,
            issuedAtEpochSec = issuedAtEpochSec.takeIf { it > 0L } ?: now,
            dueAtEpochSec = dueAtEpochSec,
            cancelledAtEpochSec = null,
            createdAtEpochSec = now,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        obligationDao.insert(obligation)
        pushObligation(userUid, obligation)
        return obligation
    }

    suspend fun update(
        obligationId: String,
        type: String,
        title: String,
        counterpartyName: String,
        currency: String,
        originalAmountCents: Long,
        issuedAtEpochSec: Long,
        dueAtEpochSec: Long?,
        obligationCategoryId: String?,
        reference: String?,
        notes: String?
    ): ObligationEntity {
        val existing = obligationDao.getById(obligationId) ?: throw IllegalArgumentException("obligation")
        require(title.isNotBlank()) { "title" }
        require(counterpartyName.isNotBlank()) { "counterpartyName" }
        require(currency.isNotBlank()) { "currency" }
        require(originalAmountCents > 0L) { "originalAmountCents" }

        val updated = existing.copy(
            type = ObligationEntity.normalizeType(type),
            title = title.trim(),
            counterpartyName = counterpartyName.trim(),
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            reference = reference?.trim()?.takeIf { it.isNotEmpty() },
            obligationCategoryId = obligationCategoryId?.trim()?.takeIf { it.isNotEmpty() },
            currency = currency.trim(),
            originalAmountCents = originalAmountCents,
            issuedAtEpochSec = issuedAtEpochSec,
            dueAtEpochSec = dueAtEpochSec,
            updatedAtEpochSec = nextUpdatedAt(existing.updatedAtEpochSec),
            updatedBy = deviceIdProvider.get()
        )
        obligationDao.update(updated)
        pushObligation(updated.userUid, updated)
        return updated
    }

    suspend fun cancel(obligationId: String, cancelledAtEpochSec: Long = System.currentTimeMillis() / 1000): ObligationEntity {
        val existing = obligationDao.getById(obligationId) ?: throw IllegalArgumentException("obligation")
        val updated = existing.copy(
            cancelledAtEpochSec = cancelledAtEpochSec,
            updatedAtEpochSec = nextUpdatedAt(existing.updatedAtEpochSec),
            updatedBy = deviceIdProvider.get()
        )
        obligationDao.update(updated)
        pushObligation(updated.userUid, updated)
        return updated
    }

    suspend fun upsert(obligation: ObligationEntity) {
        obligationDao.insert(obligation)
    }

    suspend fun deleteLocal(id: String) {
        obligationDao.delete(id)
    }

    suspend fun syncFromFirestore(userUid: String) {
        retryPendingRemoteOps(userUid)
        val snapshot = try {
            remoteStore.fetchObligations(userUid)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching obligations for user $userUid", e)
            return
        }

        val pendingDeletes = pendingRemoteOps(KEY_PENDING_DELETE).toMutableSet()
        val remoteIds = snapshot.documents.map { it.id }.toSet()
        var inserted = 0
        var updated = 0
        var invalid = 0

        snapshot.documents.forEach { remoteDoc ->
            if (pendingDeletes.contains(remoteDoc.id)) return@forEach
            val remoteObligation = remoteDoc.toEntity(userUid)
            if (remoteObligation == null) {
                invalid++
                return@forEach
            }
            try {
                val existing = obligationDao.getById(remoteObligation.id)
                if (existing == null) {
                    obligationDao.insert(remoteObligation)
                    inserted++
                } else if (ObligationMergePolicy.shouldAcceptRemote(existing, remoteObligation)) {
                    obligationDao.update(remoteObligation)
                    updated++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying remote obligation ${remoteDoc.id}", e)
            }
        }

        if (!snapshot.isFromCache) {
            val pendingPush = pendingRemoteOps(KEY_PENDING_PUSH)
            obligationDao.getByUser(userUid).forEach { local ->
                if (local.id in remoteIds || local.id in pendingPush) return@forEach
                val hasSettlements = obligationSettlementDao.getByObligation(local.id).isNotEmpty()
                if (hasSettlements) {
                    Log.e(TAG, "Obligation ${local.id} absent remotely but has settlements; deferred")
                    return@forEach
                }
                try {
                    obligationDao.delete(local.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Error pruning obligation ${local.id}", e)
                }
            }
        }

        Log.d(TAG, "Obligation sync for user $userUid inserted=$inserted updated=$updated invalid=$invalid fromCache=${snapshot.isFromCache}")
    }

    suspend fun deleteAllByUser(userUid: String) {
        obligationDao.deleteAllByUser(userUid)
        clearPendingRemoteOps()
        try {
            remoteStore.deleteAllObligationsByUser(userUid)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing remote obligations for user $userUid", e)
        }
    }

    private suspend fun pushObligation(userUid: String, obligation: ObligationEntity) {
        markPendingRemoteOp(KEY_PENDING_PUSH, obligation.id)
        try {
            remoteStore.upsertObligation(userUid, obligation)
            clearPendingRemoteOp(KEY_PENDING_PUSH, obligation.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing obligation ${obligation.id}", e)
        }
    }

    private suspend fun retryPendingRemoteOps(userUid: String) {
        pendingRemoteOps(KEY_PENDING_PUSH).forEach { obligationId ->
            val local = obligationDao.getById(obligationId)
            if (local == null) {
                clearPendingRemoteOp(KEY_PENDING_PUSH, obligationId)
                return@forEach
            }
            try {
                remoteStore.upsertObligation(userUid, local)
                clearPendingRemoteOp(KEY_PENDING_PUSH, obligationId)
            } catch (e: Exception) {
                Log.e(TAG, "Error retrying obligation push $obligationId", e)
            }
        }
        pendingRemoteOps(KEY_PENDING_DELETE).forEach { obligationId ->
            try {
                remoteStore.deleteObligation(userUid, obligationId)
                clearPendingRemoteOp(KEY_PENDING_DELETE, obligationId)
            } catch (e: Exception) {
                Log.e(TAG, "Error retrying obligation delete $obligationId", e)
            }
        }
    }

    private fun pendingRemoteOps(key: String): Set<String> {
        return sharedPreferences.getStringSet(key, emptySet())?.toSet().orEmpty()
    }

    private fun markPendingRemoteOp(key: String, id: String) {
        val updated = pendingRemoteOps(key) + id
        sharedPreferences.edit().putStringSet(key, updated).apply()
    }

    private fun clearPendingRemoteOp(key: String, id: String) {
        val updated = pendingRemoteOps(key) - id
        sharedPreferences.edit().putStringSet(key, updated).apply()
    }

    private fun clearPendingRemoteOps() {
        sharedPreferences.edit()
            .remove(KEY_PENDING_PUSH)
            .remove(KEY_PENDING_DELETE)
            .apply()
    }

    private fun nextUpdatedAt(previous: Long): Long {
        val now = System.currentTimeMillis() / 1000
        return if (now > previous) now else previous + 1
    }

    private fun ObligationRemoteDoc.toEntity(userUid: String): ObligationEntity? {
        val docId = fields["id"] as? String ?: id
        val title = fields["title"] as? String ?: return null
        val counterpartyName = fields["counterpartyName"] as? String ?: return null
        val currency = fields["currency"] as? String ?: return null
        val originalAmountCents = (fields["originalAmountCents"] as? Number)?.toLong() ?: return null
        val issuedAtEpochSec = (fields["issuedAtEpochSec"] as? Number)?.toLong() ?: return null
        val createdAtEpochSec = (fields["createdAtEpochSec"] as? Number)?.toLong() ?: issuedAtEpochSec
        val updatedAtEpochSec = (fields["updatedAtEpochSec"] as? Number)?.toLong() ?: createdAtEpochSec
        val type = try {
            ObligationEntity.normalizeType(fields["type"] as? String ?: "")
        } catch (e: Exception) {
            return null
        }
        return ObligationEntity(
            id = docId,
            userUid = (fields["userUid"] as? String) ?: userUid,
            type = type,
            title = title,
            counterpartyName = counterpartyName,
            notes = fields["notes"] as? String,
            reference = fields["reference"] as? String,
            obligationCategoryId = fields["obligationCategoryId"] as? String,
            currency = currency,
            originalAmountCents = originalAmountCents,
            issuedAtEpochSec = issuedAtEpochSec,
            dueAtEpochSec = (fields["dueAtEpochSec"] as? Number)?.toLong(),
            cancelledAtEpochSec = (fields["cancelledAtEpochSec"] as? Number)?.toLong(),
            createdAtEpochSec = createdAtEpochSec,
            updatedAtEpochSec = updatedAtEpochSec,
            updatedBy = fields["updatedBy"] as? String
        )
    }

    private companion object {
        const val TAG = "ObligationRepository"
        const val KEY_PENDING_PUSH = "obligation_pending_push_ids"
        const val KEY_PENDING_DELETE = "obligation_pending_delete_ids"
    }
}
