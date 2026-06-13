package com.myfinances.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myfinances.data.local.dao.LoanMovementDao
import com.myfinances.data.local.entity.LoanMovementEntity
import com.myfinances.data.local.entity.LoanMovementType
import com.myfinances.sync.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanMovementRepository @Inject constructor(
    private val loanMovementDao: LoanMovementDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider
) {
    fun observeByLoan(userUid: String, loanId: String): Flow<List<LoanMovementEntity>> {
        return loanMovementDao.observeByLoan(userUid, loanId)
    }

    suspend fun getByLoan(userUid: String, loanId: String): List<LoanMovementEntity> {
        return loanMovementDao.getByLoan(userUid, loanId)
    }

    suspend fun getAllByUser(userUid: String): List<LoanMovementEntity> {
        return loanMovementDao.getAllByUser(userUid)
    }

    suspend fun sumTopupCents(userUid: String, loanId: String): Long {
        return loanMovementDao.sumTopupCents(userUid, loanId)
    }

    suspend fun create(
        userUid: String,
        loanId: String,
        movementType: String,
        amountCents: Long,
        accountId: String?,
        linkedTransactionId: String?,
        note: String?,
        occurredAtEpochSec: Long
    ): LoanMovementEntity {
        val now = System.currentTimeMillis() / 1000
        val occ = occurredAtEpochSec.takeIf { it > 0 } ?: now
        val normalizedMovementType = LoanMovementType.requireValid(movementType)

        val movement = LoanMovementEntity(
            id = UUID.randomUUID().toString(),
            userUid = userUid,
            loanId = loanId,
            movementType = normalizedMovementType,
            amountCents = amountCents,
            accountId = accountId,
            linkedTransactionId = linkedTransactionId,
            note = note,
            occurredAtEpochSec = occ,
            createdAtEpochSec = now,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        loanMovementDao.insert(movement)
        syncToFirestore(userUid, movement)
        return movement
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("LoanMovementRepository", "Syncing loan movements from Firestore user=$userUid")
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("loans")
            val snapshot = collectionRef
                .get()
                .await()

            val movements = snapshot.documents.flatMap { loanDoc ->
                val loanId = loanDoc.id
                try {
                    loanDoc.reference.collection("movements")
                        .get()
                        .await()
                        .documents
                        .mapNotNull { movementDoc ->
                            try {
                                val data = movementDoc.data ?: return@mapNotNull null

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

                                val movementType = LoanMovementType.normalizeOrNull(anyString("movementType", "movement_type"))
                                    ?: return@mapNotNull null
                                val amountCents = anyLong("amountCents", "amount_cents") ?: return@mapNotNull null
                                val accountId = anyString("accountId", "account_id")
                                val linkedTransactionId = anyString("linkedTransactionId", "linked_transaction_id")
                                val note = (data["note"] as? String)
                                val occurredAt = anyLong("occurredAtEpochSec", "occurred_at_epoch_sec") ?: (System.currentTimeMillis() / 1000)
                                val createdAt = anyLong("createdAtEpochSec", "created_at_epoch_sec") ?: (System.currentTimeMillis() / 1000)
                                val updatedAt = anyLong("updatedAtEpochSec", "updated_at_epoch_sec") ?: createdAt
                                val updatedBy = anyString("updatedBy", "updated_by")

                                LoanMovementEntity(
                                    id = movementDoc.id,
                                    userUid = userUid,
                                    loanId = loanId,
                                    movementType = movementType,
                                    amountCents = amountCents,
                                    accountId = accountId,
                                    linkedTransactionId = linkedTransactionId,
                                    note = note,
                                    occurredAtEpochSec = occurredAt,
                                    createdAtEpochSec = createdAt,
                                    updatedAtEpochSec = updatedAt,
                                    updatedBy = updatedBy
                                )
                            } catch (e: Exception) {
                                Log.e("LoanMovementRepository", "Error parsing loan movement doc=${movementDoc.id}", e)
                                null
                            }
                        }
                } catch (e: Exception) {
                    Log.e("LoanMovementRepository", "Error reading movements for loan=$loanId", e)
                    emptyList()
                }
            }

            for (movement in movements) {
                val existing = loanMovementDao.getById(movement.id)
                if (existing == null) {
                    loanMovementDao.insert(movement)
                } else if (movement.updatedAtEpochSec > existing.updatedAtEpochSec) {
                    loanMovementDao.update(movement)
                }
            }
        } catch (e: Exception) {
            Log.e("LoanMovementRepository", "Error syncing loan movements", e)
        }
    }

    private suspend fun syncToFirestore(userUid: String, movement: LoanMovementEntity) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("loans")
                .document(movement.loanId)
                .collection("movements")
                .document(movement.id)
                .set(movement, SetOptions.merge())
                .await()
        } catch (_: Exception) {
        }
    }

    suspend fun deleteAllByUser(userUid: String) {
        loanMovementDao.deleteAllByUser(userUid)
        deleteAllFromFirestore(userUid)
    }

    private suspend fun deleteAllFromFirestore(userUid: String) {
        try {
            val batch = firestore.batch()
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("loans")
            val snapshot = collectionRef.get().await()
            snapshot.documents.forEach { loanDoc ->
                val movementsSnapshot = loanDoc.reference.collection("movements").get().await()
                movementsSnapshot.documents.forEach { movementDoc ->
                    batch.delete(movementDoc.reference)
                }
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("LoanMovementRepository", "Error deleting all from Firestore", e)
        }
    }
}
