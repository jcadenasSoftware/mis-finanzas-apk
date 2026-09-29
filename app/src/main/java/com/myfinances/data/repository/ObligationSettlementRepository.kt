package com.jcadenas.xpendz.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.jcadenas.xpendz.data.local.dao.ObligationDao
import com.jcadenas.xpendz.data.local.dao.ObligationSettlementDao
import com.jcadenas.xpendz.data.local.dao.TransactionDao
import com.jcadenas.xpendz.data.local.entity.ObligationSettlementEntity
import com.jcadenas.xpendz.data.local.entity.TransactionEntity
import com.jcadenas.xpendz.infrastructure.obligation.sync.ObligationMergePolicy
import com.jcadenas.xpendz.sync.DeviceIdProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

data class ObligationSettlementRemoteDoc(
    val id: String,
    val fields: Map<String, Any?>
)

data class ObligationSettlementRemoteSnapshot(
    val documents: List<ObligationSettlementRemoteDoc>,
    val isFromCache: Boolean
)

interface ObligationSettlementRemoteStore {
    suspend fun upsertSettlement(userUid: String, settlement: ObligationSettlementEntity)
    suspend fun upsertSettlementTransaction(userUid: String, transaction: TransactionEntity)
    suspend fun deleteSettlement(userUid: String, settlementId: String)
    suspend fun deleteSettlementTransaction(userUid: String, transactionId: String)
    suspend fun fetchSettlements(userUid: String): ObligationSettlementRemoteSnapshot
    suspend fun deleteAllSettlementsByUser(userUid: String)
}

@Singleton
class FirestoreObligationSettlementRemoteStore @Inject constructor(
    private val firestore: FirebaseFirestore
) : ObligationSettlementRemoteStore {

    private fun collectionRef(userUid: String) =
        firestore.collection("users").document(userUid).collection("obligationSettlements")

    private fun transactionsRef(userUid: String) =
        firestore.collection("users").document(userUid).collection("transactions")

    override suspend fun upsertSettlement(userUid: String, settlement: ObligationSettlementEntity) {
        collectionRef(userUid).document(settlement.id)
            .set(settlement, SetOptions.merge())
            .await()
    }

    override suspend fun upsertSettlementTransaction(userUid: String, transaction: TransactionEntity) {
        transactionsRef(userUid).document(transaction.id)
            .set(transaction, SetOptions.merge())
            .await()
    }

    override suspend fun deleteSettlement(userUid: String, settlementId: String) {
        collectionRef(userUid).document(settlementId).delete().await()
    }

    override suspend fun deleteSettlementTransaction(userUid: String, transactionId: String) {
        transactionsRef(userUid).document(transactionId).delete().await()
    }

    override suspend fun fetchSettlements(userUid: String): ObligationSettlementRemoteSnapshot {
        val snapshot = collectionRef(userUid).get().await()
        val docs = snapshot.documents.mapNotNull { doc ->
            val data = doc.data
            if (data == null) null else ObligationSettlementRemoteDoc(id = doc.id, fields = data)
        }
        return ObligationSettlementRemoteSnapshot(
            documents = docs,
            isFromCache = snapshot.metadata.isFromCache
        )
    }

    override suspend fun deleteAllSettlementsByUser(userUid: String) {
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
class ObligationSettlementRepository @Inject constructor(
    private val obligationSettlementDao: ObligationSettlementDao,
    private val obligationDao: ObligationDao,
    private val transactionDao: TransactionDao,
    private val remoteStore: ObligationSettlementRemoteStore,
    private val deviceIdProvider: DeviceIdProvider,
    private val sharedPreferences: SharedPreferences
) {
    fun observeByObligation(obligationId: String): Flow<List<ObligationSettlementEntity>> {
        return obligationSettlementDao.observeByObligation(obligationId)
    }

    suspend fun getByObligation(obligationId: String): List<ObligationSettlementEntity> {
        return obligationSettlementDao.getByObligation(obligationId)
    }

    suspend fun getByUser(userUid: String): List<ObligationSettlementEntity> {
        return obligationSettlementDao.getByUser(userUid)
    }

    suspend fun getById(id: String): ObligationSettlementEntity? = obligationSettlementDao.getById(id)

    suspend fun getByLinkedTransactionId(linkedTransactionId: String): ObligationSettlementEntity? {
        return obligationSettlementDao.getByLinkedTransactionId(linkedTransactionId)
    }

    suspend fun getTotalSettledCents(obligationId: String): Long {
        return obligationSettlementDao.getTotalSettledCents(obligationId)
    }

    suspend fun create(
        userUid: String,
        obligationId: String,
        accountId: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        linkedTransactionId: String,
        note: String? = null
    ): ObligationSettlementEntity {
        error("Use ObligationService.registerSettlement")
    }

    suspend fun update(
        settlementId: String,
        accountId: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        linkedTransactionId: String,
        note: String?
    ): ObligationSettlementEntity {
        error("Use ObligationService.updateSettlement")
    }

    suspend fun upsert(settlement: ObligationSettlementEntity) {
        if (obligationSettlementDao.getById(settlement.id) == null) {
            obligationSettlementDao.insert(settlement)
        } else {
            obligationSettlementDao.update(settlement)
        }
    }

    suspend fun deleteLocal(id: String) {
        error("Use ObligationService.deleteSettlement")
    }

    internal suspend fun insertDirect(settlement: ObligationSettlementEntity) {
        obligationSettlementDao.insert(settlement)
    }

    internal suspend fun updateDirect(settlement: ObligationSettlementEntity) {
        obligationSettlementDao.update(settlement)
    }

    internal suspend fun deleteDirect(id: String) {
        obligationSettlementDao.delete(id)
    }

    internal suspend fun publishSettlement(settlement: ObligationSettlementEntity) {
        markPendingRemoteOp(KEY_PENDING_PUSH, settlement.id)
        try {
            pushSettlementPair(settlement)
            clearPendingRemoteOp(KEY_PENDING_PUSH, settlement.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing settlement ${settlement.id}", e)
        }
    }

    internal suspend fun publishSettlementDeleted(userUid: String, settlementId: String, transactionId: String) {
        markPendingRemoteOp(KEY_PENDING_DELETE, "$settlementId|$transactionId")
        try {
            remoteStore.deleteSettlement(userUid, settlementId)
            remoteStore.deleteSettlementTransaction(userUid, transactionId)
            clearPendingRemoteOp(KEY_PENDING_DELETE, "$settlementId|$transactionId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting remote settlement $settlementId / transaction $transactionId", e)
        }
    }

    suspend fun syncFromFirestore(userUid: String) {
        retryPendingRemoteOps(userUid)
        val snapshot = try {
            remoteStore.fetchSettlements(userUid)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching settlements for user $userUid", e)
            return
        }

        val pendingDeletes = pendingRemoteOps(KEY_PENDING_DELETE)
            .map { it.substringBefore('|') }
            .toSet()
        val remoteIds = snapshot.documents.map { it.id }.toSet()
        var inserted = 0
        var updated = 0
        var deferred = 0
        var rejected = 0
        var invalid = 0

        snapshot.documents.forEach { remoteDoc ->
            if (pendingDeletes.contains(remoteDoc.id)) return@forEach
            val remoteSettlement = remoteDoc.toEntity(userUid)
            if (remoteSettlement == null) {
                invalid++
                return@forEach
            }
            try {
                val obligation = obligationDao.getById(remoteSettlement.obligationId)
                if (obligation == null) {
                    Log.e(TAG, "Settlement ${remoteDoc.id} references missing obligation ${remoteSettlement.obligationId}; deferred")
                    deferred++
                    return@forEach
                }
                val linkedTransaction = transactionDao.getById(remoteSettlement.linkedTransactionId)
                if (linkedTransaction == null) {
                    Log.e(TAG, "Settlement ${remoteDoc.id} references missing transaction ${remoteSettlement.linkedTransactionId}; deferred")
                    deferred++
                    return@forEach
                }
                val existing = obligationSettlementDao.getById(remoteSettlement.id)
                if (existing != null &&
                    (existing.linkedTransactionId != remoteSettlement.linkedTransactionId ||
                        existing.obligationId != remoteSettlement.obligationId)
                ) {
                    Log.e(TAG, "Settlement ${remoteDoc.id} remote changes stable references; rejected")
                    rejected++
                    return@forEach
                }
                val owner = obligationSettlementDao.getByLinkedTransactionId(remoteSettlement.linkedTransactionId)
                if (owner != null && owner.id != remoteSettlement.id) {
                    Log.e(TAG, "Settlement ${remoteDoc.id} linked transaction already owned by ${owner.id}; rejected")
                    rejected++
                    return@forEach
                }
                if (existing == null) {
                    obligationSettlementDao.insert(remoteSettlement)
                    inserted++
                } else if (ObligationMergePolicy.shouldAcceptRemote(existing, remoteSettlement)) {
                    obligationSettlementDao.update(remoteSettlement)
                    updated++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying remote settlement ${remoteDoc.id}", e)
            }
        }

        if (!snapshot.isFromCache) {
            val pendingPush = pendingRemoteOps(KEY_PENDING_PUSH)
            obligationSettlementDao.getByUser(userUid).forEach { local ->
                if (local.id in remoteIds || local.id in pendingPush || pendingDeletes.contains(local.id)) return@forEach
                try {
                    val linkedTx = transactionDao.getById(local.linkedTransactionId)
                    obligationSettlementDao.delete(local.id)
                    if (linkedTx != null) {
                        transactionDao.delete(linkedTx.id)
                        publishSettlementDeleted(local.userUid, local.id, linkedTx.id)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error pruning settlement ${local.id}", e)
                }
            }
        }

        Log.d(TAG, "Settlement sync for user $userUid inserted=$inserted updated=$updated deferred=$deferred rejected=$rejected invalid=$invalid fromCache=${snapshot.isFromCache}")
    }

    suspend fun deleteAllByUser(userUid: String) {
        obligationSettlementDao.deleteAllByUser(userUid)
        clearPendingRemoteOps()
        try {
            remoteStore.deleteAllSettlementsByUser(userUid)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing remote settlements for user $userUid", e)
        }
    }

    private suspend fun retryPendingRemoteOps(userUid: String) {
        pendingRemoteOps(KEY_PENDING_PUSH).forEach { settlementId ->
            val local = obligationSettlementDao.getById(settlementId)
            if (local == null) {
                clearPendingRemoteOp(KEY_PENDING_PUSH, settlementId)
                return@forEach
            }
            try {
                pushSettlementPair(local)
                clearPendingRemoteOp(KEY_PENDING_PUSH, settlementId)
            } catch (e: Exception) {
                Log.e(TAG, "Error retrying settlement push $settlementId", e)
            }
        }
        pendingRemoteOps(KEY_PENDING_DELETE).forEach { entry ->
            val settlementId = entry.substringBefore('|')
            val transactionId = entry.substringAfter('|', "")
            try {
                remoteStore.deleteSettlement(userUid, settlementId)
                if (transactionId.isNotEmpty()) {
                    remoteStore.deleteSettlementTransaction(userUid, transactionId)
                }
                clearPendingRemoteOp(KEY_PENDING_DELETE, entry)
            } catch (e: Exception) {
                Log.e(TAG, "Error retrying settlement delete $entry", e)
            }
        }
    }

    private suspend fun pushSettlementPair(settlement: ObligationSettlementEntity) {
        val linkedTransaction = transactionDao.getById(settlement.linkedTransactionId)
        if (linkedTransaction == null) {
            Log.e(TAG, "Linked transaction ${settlement.linkedTransactionId} missing for settlement ${settlement.id}; pushing settlement only")
        } else {
            remoteStore.upsertSettlementTransaction(settlement.userUid, linkedTransaction)
        }
        remoteStore.upsertSettlement(settlement.userUid, settlement)
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

    private fun ObligationSettlementRemoteDoc.toEntity(userUid: String): ObligationSettlementEntity? {
        val docId = fields["id"] as? String ?: id
        val obligationId = fields["obligationId"] as? String ?: return null
        val accountId = fields["accountId"] as? String ?: return null
        val amountCents = (fields["amountCents"] as? Number)?.toLong() ?: return null
        val occurredAtEpochSec = (fields["occurredAtEpochSec"] as? Number)?.toLong() ?: return null
        val linkedTransactionId = fields["linkedTransactionId"] as? String ?: return null
        val createdAtEpochSec = (fields["createdAtEpochSec"] as? Number)?.toLong() ?: occurredAtEpochSec
        val updatedAtEpochSec = (fields["updatedAtEpochSec"] as? Number)?.toLong() ?: createdAtEpochSec
        return ObligationSettlementEntity(
            id = docId,
            obligationId = obligationId,
            userUid = (fields["userUid"] as? String) ?: userUid,
            accountId = accountId,
            amountCents = amountCents,
            occurredAtEpochSec = occurredAtEpochSec,
            linkedTransactionId = linkedTransactionId,
            note = fields["note"] as? String,
            createdAtEpochSec = createdAtEpochSec,
            updatedAtEpochSec = updatedAtEpochSec,
            updatedBy = fields["updatedBy"] as? String
        )
    }

    private companion object {
        const val TAG = "ObligationSettlements"
        const val KEY_PENDING_PUSH = "obligation_settlement_pending_push_ids"
        const val KEY_PENDING_DELETE = "obligation_settlement_pending_delete_ids"
    }
}
