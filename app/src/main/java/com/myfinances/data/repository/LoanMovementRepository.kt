package com.jcadenas.xpendz.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jcadenas.xpendz.data.local.dao.LoanMovementDao
import com.jcadenas.xpendz.data.local.entity.LoanMovementEntity
import com.jcadenas.xpendz.data.local.entity.LoanMovementType
import com.jcadenas.xpendz.sync.DeviceIdProvider
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
) : com.jcadenas.xpendz.infrastructure.loan.migration.ReversedLoanMovementStore {
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

            val remoteKeys = mutableSetOf<String>()
            val failedLoanIds = mutableSetOf<String>()
            val movements = snapshot.documents.flatMap { loanDoc ->
                val loanId = loanDoc.id
                try {
                    val movementsSnapshot = loanDoc.reference.collection("movements")
                        .get()
                        .await()
                    // Una subcolección servida desde caché puede estar
                    // incompleta: sus filas locales no son podables.
                    if (movementsSnapshot.metadata.isFromCache) {
                        failedLoanIds.add(loanId)
                    }
                    movementsSnapshot
                        .documents
                        .mapNotNull { movementDoc ->
                            // El doc existe en remoto aunque no se pueda
                            // parsear: registrar su id evita podar la fila local.
                            remoteKeys.add("$loanId/${movementDoc.id}")
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

                                Log.d(
                                    "LoanMovementRepository",
                                    "Parsed loanMovement doc=${movementDoc.id} loanId=$loanId movementType=$movementType amountCents=$amountCents accountId=$accountId linkedTransactionId=$linkedTransactionId createdAt=$createdAt updatedAt=$updatedAt"
                                )

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
                    failedLoanIds.add(loanId)
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

            // Poda simétrica con Desktop: una fila local ausente en remoto fue
            // revertida en otro dispositivo. Si la subcolección de un préstamo
            // falló al leerse o vino de caché, sus filas locales se conservan
            // (snapshot parcial). La lista de préstamos cacheada tampoco autoriza
            // poda: puede omitir préstamos completos.
            var pruned = 0
            if (snapshot.metadata.isFromCache) {
                Log.d("LoanMovementRepository", "Movements snapshot from cache; skipping prune")
            } else for (local in loanMovementDao.getAllByUser(userUid)) {
                if (local.loanId in failedLoanIds) continue
                if ("${local.loanId}/${local.id}" !in remoteKeys) {
                    try {
                        loanMovementDao.delete(local.id)
                        pruned++
                    } catch (e: Exception) {
                        Log.e("LoanMovementRepository", "Error pruning movement ${local.id}", e)
                    }
                }
            }
            if (pruned > 0) {
                Log.d("LoanMovementRepository", "Pruned $pruned local loanMovements absent in remote")
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
        deleteAllLocalByUser(userUid)
        try {
            deleteAllRemoteByUser(userUid)
        } catch (e: Exception) {
            Log.e("LoanMovementRepository", "Error al eliminar datos remotos en deleteAllByUser", e)
        }
    }

    internal suspend fun deleteAllLocalByUser(userUid: String) {
        loanMovementDao.deleteAllByUser(userUid)
    }

    internal suspend fun deleteAllRemoteByUser(userUid: String) {
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
    }

    suspend fun updateByTransaction(
        transactionId: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ): LoanMovementEntity? {
        val movement = loanMovementDao.getByLinkedTransactionId(transactionId) ?: return null
        
        val now = System.currentTimeMillis() / 1000
        val updatedMovement = movement.copy(
            amountCents = amountCents,
            occurredAtEpochSec = occurredAtEpochSec,
            note = note,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        loanMovementDao.update(updatedMovement)
        syncToFirestore(movement.userUid, updatedMovement)
        return updatedMovement
    }

    suspend fun deleteByTransaction(transactionId: String): LoanMovementEntity? {
        val movement = loanMovementDao.getByLinkedTransactionId(transactionId) ?: return null
        
        loanMovementDao.delete(movement.id)
        deleteFromFirestore(movement.userUid, movement.loanId, movement.id)
        return movement
    }

    /**
     * Elimina el movimiento de pago asociado a un pago revertido, localizado por
     * transactionId vinculado o por firma (mismo criterio que el reconciliador).
     */
    override suspend fun deletePaymentBySignature(
        userUid: String,
        loanId: String,
        accountId: String?,
        amountCents: Long,
        occurredAtEpochSec: Long,
        linkedTransactionId: String?
    ): LoanMovementEntity? {
        val movement = linkedTransactionId
            ?.let { loanMovementDao.getByLinkedTransactionId(it) }
            ?: loanMovementDao.getPaymentBySignature(userUid, loanId, accountId, amountCents, occurredAtEpochSec)
            ?: return null
        loanMovementDao.delete(movement.id)
        deleteFromFirestore(userUid, movement.loanId, movement.id)
        return movement
    }

    suspend fun deleteFromFirestore(userUid: String, loanId: String, movementId: String) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("loans")
                .document(loanId)
                .collection("movements")
                .document(movementId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("LoanMovementRepository", "Error deleting movement from Firestore", e)
        }
    }
}
