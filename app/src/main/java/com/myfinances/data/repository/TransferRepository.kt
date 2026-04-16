package com.myfinances.data.repository

import android.util.Log
import android.database.sqlite.SQLiteConstraintException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myfinances.data.local.dao.AccountDao
import com.myfinances.data.local.dao.TransferDao
import com.myfinances.data.local.dao.TransferWithDetails
import com.myfinances.data.local.entity.TransferEntity
import com.myfinances.sync.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepository @Inject constructor(
    private val transferDao: TransferDao,
    private val accountDao: AccountDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider
) {
    fun observeRecent(userUid: String, limit: Int = 50): Flow<List<TransferWithDetails>> {
        return transferDao.observeRecent(userUid, limit)
    }

    fun observeMaxUpdatedAtEpochSec(userUid: String): Flow<Long?> {
        return transferDao.observeMaxUpdatedAtEpochSec(userUid)
    }

    suspend fun getRecent(userUid: String, limit: Int = 50): List<TransferWithDetails> {
        return transferDao.getRecent(userUid, limit)
    }

    suspend fun getFiltered(
        userUid: String,
        accountId: String? = null,
        fromEpochSec: Long? = null,
        toEpochSec: Long? = null,
        limit: Int = 100
    ): List<TransferWithDetails> {
        return transferDao.getFiltered(userUid, accountId, fromEpochSec, toEpochSec, limit)
    }

    suspend fun getById(id: String): TransferEntity? {
        return transferDao.getById(id)
    }

    suspend fun create(
        userUid: String,
        fromAccountId: String,
        toAccountId: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ): TransferEntity {
        require(fromAccountId != toAccountId) { "Las cuentas deben ser diferentes" }

        val fromBalance = accountDao.computeBalanceCents(userUid, fromAccountId)
        require(fromBalance - amountCents >= 0L) { "Saldo insuficiente" }
        
        val now = System.currentTimeMillis() / 1000
        val transfer = TransferEntity(
            id = UUID.randomUUID().toString(),
            userUid = userUid,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amountCents = amountCents,
            occurredAtEpochSec = occurredAtEpochSec,
            note = note,
            createdAtEpochSec = now,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        transferDao.insert(transfer)
        syncToFirestore(userUid, transfer)
        return transfer
    }

    suspend fun update(
        userUid: String,
        transferId: String,
        fromAccountId: String,
        toAccountId: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ): TransferEntity? {
        require(fromAccountId != toAccountId) { "Las cuentas deben ser diferentes" }
        
        val existing = transferDao.getById(transferId) ?: return null

        // Enforce non-negative balances by simulating: (current balance) + revert(old) + apply(new)
        // Balance is computed including the existing transfer.
        run {
            val affectedFromIds = linkedSetOf(existing.fromAccountId, fromAccountId)
            for (accId in affectedFromIds) {
                val currentBalance = accountDao.computeBalanceCents(userUid, accId)
                val revertOld = if (accId == existing.fromAccountId) existing.amountCents else 0L
                val applyNew = if (accId == fromAccountId) -amountCents else 0L
                val projected = currentBalance + revertOld + applyNew
                require(projected >= 0L) { "Saldo insuficiente" }
            }
        }

        var now = System.currentTimeMillis() / 1000
        if (now <= existing.updatedAtEpochSec) {
            now = existing.updatedAtEpochSec + 1
        }
        val updated = existing.copy(
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amountCents = amountCents,
            occurredAtEpochSec = occurredAtEpochSec,
            note = note,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        transferDao.update(updated)
        syncToFirestore(userUid, updated)
        return updated
    }

    suspend fun delete(userUid: String, transferId: String) {
        transferDao.delete(transferId)
        deleteFromFirestore(userUid, transferId)
    }

    suspend fun deleteAllByUser(userUid: String) {
        transferDao.deleteAllByUser(userUid)
        deleteAllFromFirestore(userUid)
    }

    private suspend fun deleteAllFromFirestore(userUid: String) {
        try {
            val batch = firestore.batch()
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("transfers")
            val snapshot = collectionRef.get().await()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("TransferRepository", "Error deleting all from Firestore", e)
        }
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("TransferRepository", "Syncing transfers from Firestore for user: $userUid")
            val lastUpdatedAt = transferDao.getMaxUpdatedAtEpochSec(userUid)
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("transfers")
            val snapshot = if (lastUpdatedAt != null && lastUpdatedAt > 0L) {
                collectionRef
                    .whereGreaterThan("updatedAtEpochSec", lastUpdatedAt)
                    .get()
                    .await()
            } else {
                collectionRef
                    .get()
                    .await()
            }

            Log.d("TransferRepository", "Snapshot size: ${snapshot.size()}")

            val transfers = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null

                    fun anyLong(vararg keys: String): Long? {
                        for (k in keys) {
                            val v = data[k]
                            when (v) {
                                is Number -> return v.toLong()
                                is String -> v.toLongOrNull()?.let { return it }
                            }
                        }
                        return null
                    }

                    fun anyString(vararg keys: String): String? {
                        for (k in keys) {
                            val v = data[k]
                            if (v is String && v.isNotBlank()) return v
                        }
                        return null
                    }

                    val fromAccountId = anyString("fromAccountId", "from_account_id") ?: return@mapNotNull null
                    val toAccountId = anyString("toAccountId", "to_account_id") ?: return@mapNotNull null
                    val amountCents = anyLong("amountCents", "amount_cents") ?: return@mapNotNull null
                    val occurredAt = anyLong("occurredAtEpochSec", "occurred_at_epoch_sec") ?: return@mapNotNull null
                    val createdAt = anyLong("createdAtEpochSec", "created_at_epoch_sec") ?: (System.currentTimeMillis() / 1000)
                    val updatedAt = anyLong("updatedAtEpochSec", "updated_at_epoch_sec") ?: createdAt
                    val note = (data["note"] as? String)
                    val updatedBy = anyString("updatedBy", "updated_by")

                    TransferEntity(
                        id = doc.id,
                        userUid = userUid,
                        fromAccountId = fromAccountId,
                        toAccountId = toAccountId,
                        amountCents = amountCents,
                        occurredAtEpochSec = occurredAt,
                        note = note,
                        createdAtEpochSec = createdAt,
                        updatedAtEpochSec = updatedAt,
                        updatedBy = updatedBy
                    )
                } catch (e: Exception) {
                    Log.e("TransferRepository", "Error parsing transfer doc ${doc.id}", e)
                    null
                }
            }

            Log.d("TransferRepository", "Parsed ${transfers.size} valid transfers")

            var inserted = 0
            var updated = 0
            var skipped = 0

            for (tr in transfers) {
                try {
                    val existing = transferDao.getById(tr.id)
                    if (existing == null) {
                        transferDao.insert(tr)
                        inserted++
                    } else {
                        // last-write-wins
                        if (tr.updatedAtEpochSec < existing.updatedAtEpochSec) {
                            continue
                        }
                        if (tr.updatedAtEpochSec == existing.updatedAtEpochSec) {
                            val same =
                                tr.fromAccountId == existing.fromAccountId &&
                                    tr.toAccountId == existing.toAccountId &&
                                    tr.amountCents == existing.amountCents &&
                                    tr.occurredAtEpochSec == existing.occurredAtEpochSec &&
                                    tr.note == existing.note
                            if (same) {
                                continue
                            }
                        }
                        transferDao.update(tr)
                        updated++
                    }
                } catch (e: SQLiteConstraintException) {
                    skipped++
                    Log.e(
                        "TransferRepository",
                        "FK error upserting transfer ${tr.id} from=${tr.fromAccountId} to=${tr.toAccountId}",
                        e
                    )
                } catch (e: Exception) {
                    skipped++
                    Log.e("TransferRepository", "Error upserting transfer ${tr.id}", e)
                }
            }

            Log.d("TransferRepository", "Transfers upserted inserted=$inserted updated=$updated skipped=$skipped")
        } catch (e: Exception) {
            Log.e("TransferRepository", "Error syncing transfers from Firestore", e)
        }
    }

    private suspend fun syncToFirestore(userUid: String, transfer: TransferEntity) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("transfers")
                .document(transfer.id)
                .set(transfer, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            // Log error
        }
    }

    private suspend fun deleteFromFirestore(userUid: String, transferId: String) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("transfers")
                .document(transferId)
                .delete()
                .await()
        } catch (e: Exception) {
            // Log error
        }
    }
}
