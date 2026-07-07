package com.jcadenas.xpendz.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jcadenas.xpendz.data.local.dao.GoalDao
import com.jcadenas.xpendz.data.local.entity.GoalEntity
import com.jcadenas.xpendz.sync.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(
    private val goalDao: GoalDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider
) {
    fun observeByUser(userUid: String): Flow<List<GoalEntity>> {
        return goalDao.observeByUser(userUid)
    }

    suspend fun getByUser(userUid: String): List<GoalEntity> {
        return goalDao.getByUser(userUid)
    }

    suspend fun getById(id: String): GoalEntity? {
        return goalDao.getById(id)
    }

    suspend fun create(
        userUid: String,
        name: String,
        currency: String,
        targetCents: Long,
        targetDateEpochSec: Long,
        accountId: String
    ): GoalEntity {
        val now = System.currentTimeMillis() / 1000
        val goal = GoalEntity(
            id = UUID.randomUUID().toString(),
            userUid = userUid,
            name = name,
            currency = currency,
            targetCents = targetCents,
            targetDateEpochSec = targetDateEpochSec,
            accountId = accountId,
            status = "OPEN",
            createdAtEpochSec = now,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        goalDao.insert(goal)
        syncToFirestore(userUid, goal)
        return goal
    }

    suspend fun close(userUid: String, goalId: String): GoalEntity? {
        val existing = goalDao.getById(goalId) ?: return null
        val now = System.currentTimeMillis() / 1000
        val updated = existing.copy(
            status = "CLOSED",
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        goalDao.update(updated)
        syncToFirestore(userUid, updated)
        return updated
    }

    suspend fun delete(userUid: String, goalId: String) {
        goalDao.delete(goalId)
        deleteFromFirestore(userUid, goalId)
    }

    suspend fun deleteAllByUser(userUid: String) {
        deleteAllLocalByUser(userUid)
        try {
            deleteAllRemoteByUser(userUid)
        } catch (e: Exception) {
            Log.e("GoalRepository", "Error al eliminar datos remotos en deleteAllByUser", e)
        }
    }

    internal suspend fun deleteAllLocalByUser(userUid: String) {
        goalDao.deleteAllByUser(userUid)
    }

    internal suspend fun deleteAllRemoteByUser(userUid: String) {
        val batch = firestore.batch()
        val collectionRef = firestore.collection("users")
            .document(userUid)
            .collection("goals")
        val snapshot = collectionRef.get().await()
        snapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("GoalRepository", "Syncing goals from Firestore user=$userUid")
            val snapshot = firestore.collection("users")
                .document(userUid)
                .collection("goals")
                .get()
                .await()

            val goals = snapshot.documents.mapNotNull { doc ->
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

                    val name = anyString("name") ?: return@mapNotNull null
                    val currency = anyString("currency") ?: return@mapNotNull null
                    val targetCents = anyLong("targetCents", "target_cents") ?: return@mapNotNull null
                    val targetDate = anyLong("targetDateEpochSec", "target_date_epoch_sec") ?: return@mapNotNull null
                    val accountId = anyString("accountId", "account_id") ?: return@mapNotNull null
                    val status = anyString("status") ?: "OPEN"
                    val createdAt = anyLong("createdAtEpochSec", "created_at_epoch_sec") ?: (System.currentTimeMillis() / 1000)
                    val updatedAt = anyLong("updatedAtEpochSec", "updated_at_epoch_sec") ?: createdAt
                    val updatedBy = anyString("updatedBy", "updated_by")

                    GoalEntity(
                        id = doc.id,
                        userUid = userUid,
                        name = name,
                        currency = currency,
                        targetCents = targetCents,
                        targetDateEpochSec = targetDate,
                        accountId = accountId,
                        status = status,
                        createdAtEpochSec = createdAt,
                        updatedAtEpochSec = updatedAt,
                        updatedBy = updatedBy
                    )
                } catch (e: Exception) {
                    Log.e("GoalRepository", "Error parsing goal doc=${doc.id}", e)
                    null
                }
            }

            var inserted = 0
            var updated = 0
            var skipped = 0

            for (g in goals) {
                try {
                    val existing = goalDao.getById(g.id)
                    if (existing == null) {
                        goalDao.insert(g)
                        inserted++
                    } else {
                        if (g.updatedAtEpochSec <= existing.updatedAtEpochSec) {
                            continue
                        }
                        goalDao.update(g)
                        updated++
                    }
                } catch (e: SQLiteConstraintException) {
                    skipped++
                    Log.e("GoalRepository", "FK error upserting goal id=${g.id} account=${g.accountId}", e)
                } catch (e: Exception) {
                    skipped++
                    Log.e("GoalRepository", "Error upserting goal id=${g.id}", e)
                }
            }

            Log.d("GoalRepository", "Goals upserted inserted=$inserted updated=$updated skipped=$skipped")

            val remoteIds = goals.map { it.id }.toSet()
            val localAll = goalDao.getByUser(userUid)
            var deleted = 0
            for (local in localAll) {
                if (!remoteIds.contains(local.id)) {
                    try {
                        goalDao.delete(local.id)
                        deleted++
                    } catch (e: Exception) {
                        Log.w("GoalRepository", "Could not prune goal ${local.id}: ${e.message}")
                    }
                }
            }
            if (deleted > 0) Log.d("GoalRepository", "Goals pruned deleted=$deleted")
        } catch (e: Exception) {
            Log.e("GoalRepository", "Error syncing goals", e)
        }
    }

    private suspend fun syncToFirestore(userUid: String, goal: GoalEntity) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("goals")
                .document(goal.id)
                .set(goal, SetOptions.merge())
                .await()
        } catch (_: Exception) {
        }
    }

    private suspend fun deleteFromFirestore(userUid: String, goalId: String) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("goals")
                .document(goalId)
                .delete()
                .await()
        } catch (_: Exception) {
        }
    }
}
