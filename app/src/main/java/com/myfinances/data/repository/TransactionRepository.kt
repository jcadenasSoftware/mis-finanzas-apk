package com.myfinances.data.repository

import android.util.Log
import android.database.sqlite.SQLiteConstraintException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myfinances.data.local.dao.TransactionDao
import com.myfinances.data.local.dao.MonthlyCategoryDetailTotal
import com.myfinances.data.local.dao.MonthlyCategoryTotal
import com.myfinances.data.local.dao.RootCategorySpentTotal
import com.myfinances.data.local.dao.TransactionWithDetails
import com.myfinances.data.local.entity.TransactionEntity
import com.myfinances.sync.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider
) {
    fun observeRecent(userUid: String, limit: Int = 50): Flow<List<TransactionWithDetails>> {
        return transactionDao.observeRecent(userUid, limit)
    }

    suspend fun getRecent(userUid: String, limit: Int = 50): List<TransactionWithDetails> {
        return transactionDao.getRecent(userUid, limit)
    }

    suspend fun getFiltered(
        userUid: String,
        accountId: String? = null,
        categoryId: String? = null,
        fromEpochSec: Long? = null,
        toEpochSec: Long? = null,
        limit: Int = 100
    ): List<TransactionWithDetails> {
        return transactionDao.getFiltered(userUid, accountId, categoryId, fromEpochSec, toEpochSec, limit)
    }

    suspend fun getMonthlyTotalsByRootCategory(
        userUid: String,
        accountId: String?,
        year: Int,
        kind: String
    ): List<MonthlyCategoryTotal> {
        return transactionDao.getMonthlyTotalsByRootCategory(userUid, accountId, year, kind)
    }

    suspend fun getMonthlyTotalsBySubcategory(
        userUid: String,
        accountId: String?,
        year: Int,
        kind: String
    ): List<MonthlyCategoryDetailTotal> {
        return transactionDao.getMonthlyTotalsBySubcategory(userUid, accountId, year, kind)
    }

    suspend fun getExpenseTotalsByRootCategoryInRange(
        userUid: String,
        currency: String,
        fromEpochSec: Long,
        toEpochSec: Long
    ): List<RootCategorySpentTotal> {
        return transactionDao.getExpenseTotalsByRootCategoryInRange(userUid, currency, fromEpochSec, toEpochSec)
    }

    suspend fun getById(id: String): TransactionEntity? {
        return transactionDao.getById(id)
    }

    suspend fun create(
        userUid: String,
        accountId: String,
        categoryId: String,
        kind: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ): TransactionEntity {
        val now = System.currentTimeMillis() / 1000
        val transaction = TransactionEntity(
            id = UUID.randomUUID().toString(),
            userUid = userUid,
            accountId = accountId,
            categoryId = categoryId,
            kind = kind,
            amountCents = amountCents,
            occurredAtEpochSec = occurredAtEpochSec,
            note = note,
            createdAtEpochSec = now,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        transactionDao.insert(transaction)
        syncToFirestore(userUid, transaction)
        return transaction
    }

    suspend fun update(
        userUid: String,
        transactionId: String,
        accountId: String,
        categoryId: String,
        kind: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ): TransactionEntity? {
        val existing = transactionDao.getById(transactionId) ?: return null
        val now = System.currentTimeMillis() / 1000
        val updated = existing.copy(
            accountId = accountId,
            categoryId = categoryId,
            kind = kind,
            amountCents = amountCents,
            occurredAtEpochSec = occurredAtEpochSec,
            note = note,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        transactionDao.update(updated)
        syncToFirestore(userUid, updated)
        return updated
    }

    suspend fun delete(userUid: String, transactionId: String) {
        transactionDao.delete(transactionId)
        deleteFromFirestore(userUid, transactionId)
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("TransactionRepository", "Syncing transactions from Firestore for user: $userUid")
            val snapshot = firestore.collection("users")
                .document(userUid)
                .collection("transactions")
                .get()
                .await()

            Log.d("TransactionRepository", "Snapshot size: ${snapshot.size()}")

            val transactions = snapshot.documents.mapNotNull { doc ->
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

                    val accountId = anyString("accountId", "account_id") ?: return@mapNotNull null
                    val categoryId = anyString("categoryId", "category_id") ?: return@mapNotNull null
                    val kind = anyString("kind") ?: return@mapNotNull null
                    val amountCents = anyLong("amountCents", "amount_cents") ?: return@mapNotNull null
                    val occurredAt = anyLong("occurredAtEpochSec", "occurred_at_epoch_sec") ?: return@mapNotNull null
                    val createdAt = anyLong("createdAtEpochSec", "created_at_epoch_sec") ?: (System.currentTimeMillis() / 1000)
                    val updatedAt = anyLong("updatedAtEpochSec", "updated_at_epoch_sec") ?: createdAt
                    val note = (data["note"] as? String)
                    val updatedBy = anyString("updatedBy", "updated_by")

                    TransactionEntity(
                        id = doc.id,
                        userUid = userUid,
                        accountId = accountId,
                        categoryId = categoryId,
                        kind = kind,
                        amountCents = amountCents,
                        occurredAtEpochSec = occurredAt,
                        note = note,
                        createdAtEpochSec = createdAt,
                        updatedAtEpochSec = updatedAt,
                        updatedBy = updatedBy
                    )
                } catch (e: Exception) {
                    Log.e("TransactionRepository", "Error parsing transaction doc ${doc.id}", e)
                    null
                }
            }

            Log.d("TransactionRepository", "Parsed ${transactions.size} valid transactions")
            if (transactions.isEmpty()) {
                return
            }

            var inserted = 0
            var skipped = 0
            for (t in transactions) {
                try {
                    transactionDao.insert(t)
                    inserted++
                } catch (e: SQLiteConstraintException) {
                    skipped++
                    Log.e(
                        "TransactionRepository",
                        "FK error inserting transaction ${t.id} accountId=${t.accountId} categoryId=${t.categoryId}",
                        e
                    )
                }
            }

            Log.d("TransactionRepository", "Transactions inserted=$inserted skipped=$skipped")
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error syncing transactions from Firestore", e)
        }
    }

    private suspend fun syncToFirestore(userUid: String, transaction: TransactionEntity) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("transactions")
                .document(transaction.id)
                .set(transaction, SetOptions.merge())
                .await()

            firestore.collection("users")
                .document(userUid)
                .collection("transactions")
                .document(transaction.id)
                .set(mapOf("updatedBy" to deviceIdProvider.get()), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            // Log error
        }
    }

    private suspend fun deleteFromFirestore(userUid: String, transactionId: String) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("transactions")
                .document(transactionId)
                .delete()
                .await()
        } catch (e: Exception) {
            // Log error
        }
    }
}
