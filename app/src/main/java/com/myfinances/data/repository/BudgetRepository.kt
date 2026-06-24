package com.jcadenas.xpendz.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jcadenas.xpendz.data.local.dao.BudgetDao
import com.jcadenas.xpendz.data.local.entity.BudgetEntity
import com.jcadenas.xpendz.sync.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider
) {
    fun observeByMonth(userUid: String, month: String, currency: String): Flow<List<BudgetEntity>> {
        return budgetDao.observeByMonth(userUid, month, currency)
    }

    suspend fun getByMonth(userUid: String, month: String, currency: String): List<BudgetEntity> {
        return budgetDao.getByMonth(userUid, month, currency)
    }

    suspend fun upsert(
        userUid: String,
        month: String,
        categoryId: String,
        currency: String,
        limitCents: Long
    ): BudgetEntity {
        val now = System.currentTimeMillis() / 1000
        val existing = budgetDao.getByUnique(userUid, month, currency, categoryId)

        val entity = if (existing == null) {
            BudgetEntity(
                id = UUID.randomUUID().toString(),
                userUid = userUid,
                month = month,
                categoryId = categoryId,
                currency = currency,
                limitCents = limitCents,
                createdAtEpochSec = now,
                updatedAtEpochSec = now,
                updatedBy = deviceIdProvider.get()
            )
        } else {
            existing.copy(
                limitCents = limitCents,
                updatedAtEpochSec = now,
                updatedBy = deviceIdProvider.get()
            )
        }

        budgetDao.insert(entity)
        syncToFirestore(userUid, entity)
        return entity
    }

    suspend fun delete(userUid: String, budgetId: String) {
        budgetDao.delete(budgetId)
        deleteFromFirestore(userUid, budgetId)
    }

    suspend fun deleteAllByUser(userUid: String) {
        budgetDao.deleteAllByUser(userUid)
        deleteAllFromFirestore(userUid)
    }

    private suspend fun deleteAllFromFirestore(userUid: String) {
        try {
            val batch = firestore.batch()
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("budgets")
            val snapshot = collectionRef.get().await()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("BudgetRepository", "Error deleting all from Firestore", e)
        }
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("BudgetRepository", "Syncing budgets from Firestore user=$userUid")
            val lastUpdatedAt = budgetDao.getMaxUpdatedAtEpochSec(userUid)
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("budgets")
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

            val budgets = snapshot.documents.mapNotNull { doc ->
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

                    val month = anyString("month") ?: return@mapNotNull null
                    val categoryId = anyString("categoryId", "category_id") ?: return@mapNotNull null
                    val currency = anyString("currency") ?: return@mapNotNull null
                    val limitCents = anyLong("limitCents", "limit_cents") ?: return@mapNotNull null
                    val createdAt = anyLong("createdAtEpochSec", "created_at_epoch_sec") ?: (System.currentTimeMillis() / 1000)
                    val updatedAt = anyLong("updatedAtEpochSec", "updated_at_epoch_sec") ?: createdAt
                    val updatedBy = anyString("updatedBy", "updated_by")

                    BudgetEntity(
                        id = doc.id,
                        userUid = userUid,
                        month = month,
                        categoryId = categoryId,
                        currency = currency,
                        limitCents = limitCents,
                        createdAtEpochSec = createdAt,
                        updatedAtEpochSec = updatedAt,
                        updatedBy = updatedBy
                    )
                } catch (e: Exception) {
                    Log.e("BudgetRepository", "Error parsing budget doc=${doc.id}", e)
                    null
                }
            }

            var inserted = 0
            var updated = 0
            var skipped = 0

            for (b in budgets) {
                try {
                    val existing = budgetDao.getById(b.id)
                    if (existing == null) {
                        budgetDao.insert(b)
                        inserted++
                    } else {
                        if (b.updatedAtEpochSec <= existing.updatedAtEpochSec) {
                            continue
                        }
                        budgetDao.update(b)
                        updated++
                    }
                } catch (e: SQLiteConstraintException) {
                    skipped++
                    Log.e("BudgetRepository", "FK error upserting budget id=${b.id}", e)
                } catch (e: Exception) {
                    skipped++
                    Log.e("BudgetRepository", "Error upserting budget id=${b.id}", e)
                }
            }

            Log.d("BudgetRepository", "Budgets upserted inserted=$inserted updated=$updated skipped=$skipped")
        } catch (e: Exception) {
            Log.e("BudgetRepository", "Error syncing budgets", e)
        }
    }

    private suspend fun syncToFirestore(userUid: String, budget: BudgetEntity) {
        try {
            val docRef = firestore.collection("users")
                .document(userUid)
                .collection("budgets")
                .document(budget.id)

            val remoteSnap = runCatching { docRef.get().await() }.getOrNull()
            val remoteUpdatedAt = remoteSnap
                ?.takeIf { it.exists() }
                ?.let { snap ->
                    (snap.getLong("updatedAtEpochSec")
                        ?: snap.getLong("updated_at_epoch_sec")
                        ?: snap.getLong("updatedAt")
                        ?: snap.getLong("updated_at"))
                }

            if (remoteUpdatedAt != null && remoteUpdatedAt >= budget.updatedAtEpochSec) {
                return
            }

            docRef
                .set(budget, SetOptions.merge())
                .await()
        } catch (_: Exception) {
        }
    }

    private suspend fun deleteFromFirestore(userUid: String, budgetId: String) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("budgets")
                .document(budgetId)
                .delete()
                .await()
        } catch (_: Exception) {
        }
    }
}
