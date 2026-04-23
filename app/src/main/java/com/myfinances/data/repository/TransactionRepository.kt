package com.myfinances.data.repository

import android.util.Log
import android.database.sqlite.SQLiteConstraintException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myfinances.data.local.dao.TransactionDao
import com.myfinances.data.local.dao.AccountDao
import com.myfinances.data.local.dao.CategorySpentTotal
import com.myfinances.data.local.dao.HierarchyCategoryTotal
import com.myfinances.data.local.dao.MonthlyCategoryDetailTotal
import com.myfinances.data.local.dao.MonthlyCategoryTotal
import com.myfinances.data.local.dao.RootCategorySpentTotal
import com.myfinances.data.local.dao.TransactionWithDetails
import com.myfinances.data.local.entity.TransactionEntity
import com.myfinances.sync.DeviceIdProvider
import com.myfinances.work.BudgetAlertHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider
) {
    private fun signedAmountDeltaCents(kind: String, amountCents: Long): Long {
        val k = kind.trim().uppercase()
        return when (k) {
            "INCOME", "LOAN_BORROWED_IN", "LOAN_REPAYMENT_PRINCIPAL_IN" -> amountCents
            "EXPENSE", "LOAN_LENT_OUT", "LOAN_REPAYMENT_PRINCIPAL_OUT" -> -amountCents
            else -> 0L
        }
    }

    private suspend fun requireNonNegativeBalanceAfter(
        userUid: String,
        accountId: String,
        currentBalanceCents: Long,
        deltaCents: Long
    ) {
        val projected = currentBalanceCents + deltaCents
        require(projected >= 0L) { "Saldo insuficiente" }
    }

    fun observeRecent(userUid: String, limit: Int = 50): Flow<List<TransactionWithDetails>> {
        return transactionDao.observeRecent(userUid, limit)
    }

    fun observeMaxUpdatedAtEpochSec(userUid: String): Flow<Long?> {
        return transactionDao.observeMaxUpdatedAtEpochSec(userUid)
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

    suspend fun getExpenseTotalsByCategoryInRange(
        userUid: String,
        currency: String,
        fromEpochSec: Long,
        toEpochSec: Long
    ): List<CategorySpentTotal> {
        return transactionDao.getExpenseTotalsByCategoryInRange(userUid, currency, fromEpochSec, toEpochSec)
    }

    suspend fun getIncomeTotalsByRootCategoryInRange(
        userUid: String,
        currency: String,
        fromEpochSec: Long,
        toEpochSec: Long
    ): List<RootCategorySpentTotal> {
        return transactionDao.getIncomeTotalsByRootCategoryInRange(userUid, currency, fromEpochSec, toEpochSec)
    }

    suspend fun getHierarchyTotalsInRange(
        userUid: String,
        kind: String,
        currency: String,
        fromEpochSec: Long,
        toEpochSec: Long
    ): List<HierarchyCategoryTotal> {
        return transactionDao.getHierarchyTotalsInRange(userUid, kind, currency, fromEpochSec, toEpochSec)
    }

    suspend fun getExpenseMonths(userUid: String, currency: String, limit: Int = 24): List<String> {
        return transactionDao.getExpenseMonths(userUid, currency, limit)
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
        val currentBalance = accountDao.computeBalanceCents(userUid, accountId)
        val delta = signedAmountDeltaCents(kind, amountCents)
        if (delta < 0L) {
            requireNonNegativeBalanceAfter(
                userUid = userUid,
                accountId = accountId,
                currentBalanceCents = currentBalance,
                deltaCents = delta
            )
        }

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
        Log.d("BudgetAlert", "create() called: kind=$kind categoryId=$categoryId")
        if (kind.trim().uppercase() == "EXPENSE") {
            val helper = BudgetAlertHelper.instance
            if (helper != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        helper.checkAfterTransaction(
                            userUid = userUid,
                            accountId = accountId,
                            categoryId = categoryId,
                            occurredAtEpochSec = occurredAtEpochSec
                        )
                    } catch (e: Exception) {
                        Log.e("BudgetAlert", "checkAfterTransaction failed: ${e.message}", e)
                    }
                }
            } else {
                Log.w("BudgetAlert", "BudgetAlertHelper.instance is null")
            }
        }
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

        // Enforce non-negative balance by simulating: (current balance) + revert(old) + apply(new)
        // Balance is computed including the existing transaction.
        run {
            val affectedAccountIds = linkedSetOf(existing.accountId, accountId)
            for (accId in affectedAccountIds) {
                val currentBalance = accountDao.computeBalanceCents(userUid, accId)
                val revertOld = if (accId == existing.accountId) -signedAmountDeltaCents(existing.kind, existing.amountCents) else 0L
                val applyNew = if (accId == accountId) signedAmountDeltaCents(kind, amountCents) else 0L
                val projected = currentBalance + revertOld + applyNew
                require(projected >= 0L) { "Saldo insuficiente" }
            }
        }

        var now = System.currentTimeMillis() / 1000
        if (now <= existing.updatedAtEpochSec) {
            now = existing.updatedAtEpochSec + 1
        }
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

    suspend fun deleteAllByUser(userUid: String) {
        transactionDao.deleteAllByUser(userUid)
        deleteAllFromFirestore(userUid)
    }

    private suspend fun deleteAllFromFirestore(userUid: String) {
        try {
            val batch = firestore.batch()
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("transactions")
            val snapshot = collectionRef.get().await()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error deleting all from Firestore", e)
        }
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("TransactionRepository", "Syncing transactions from Firestore for user: $userUid")
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("transactions")
            // NOTE:
            // Desktop currently pulls the full collection and applies last-write-wins locally.
            // Incremental sync based on local MAX(updated_at_epoch_sec) can miss remote rows due to
            // clock skew, equal timestamps, or local rows with artificially high updatedAt.
            // To ensure correctness, always pull the full collection here as well.
            val snapshot = collectionRef
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
            var updated = 0
            var skipped = 0
            for (t in transactions) {
                try {
                    val existing = transactionDao.getById(t.id)
                    if (existing == null) {
                        transactionDao.insert(t)
                        inserted++
                    } else {
                        // last-write-wins
                        if (t.updatedAtEpochSec < existing.updatedAtEpochSec) {
                            continue
                        }
                        if (t.updatedAtEpochSec == existing.updatedAtEpochSec) {
                            val same =
                                t.accountId == existing.accountId &&
                                    t.categoryId == existing.categoryId &&
                                    t.kind == existing.kind &&
                                    t.amountCents == existing.amountCents &&
                                    t.occurredAtEpochSec == existing.occurredAtEpochSec &&
                                    t.note == existing.note
                            if (same) {
                                continue
                            }
                        }
                        transactionDao.update(t)
                        updated++
                    }
                } catch (e: SQLiteConstraintException) {
                    skipped++
                    Log.e(
                        "TransactionRepository",
                        "FK error upserting transaction ${t.id} accountId=${t.accountId} categoryId=${t.categoryId}",
                        e
                    )
                } catch (e: Exception) {
                    skipped++
                    Log.e("TransactionRepository", "Error upserting transaction ${t.id}", e)
                }
            }

            Log.d("TransactionRepository", "Transactions upserted inserted=$inserted updated=$updated skipped=$skipped")
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
