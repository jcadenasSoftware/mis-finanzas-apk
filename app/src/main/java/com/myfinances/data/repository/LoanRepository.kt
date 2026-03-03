package com.myfinances.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myfinances.data.local.dao.LoanDao
import com.myfinances.data.local.entity.LoanEntity
import com.myfinances.sync.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanRepository @Inject constructor(
    private val loanDao: LoanDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) {
    fun observeFiltered(userUid: String, type: String?, status: String?, currency: String?): Flow<List<LoanEntity>> {
        return loanDao.observeFiltered(userUid, type, status, currency)
    }

    suspend fun getFiltered(userUid: String, type: String?, status: String?, currency: String?): List<LoanEntity> {
        return loanDao.getFiltered(userUid, type, status, currency)
    }

    suspend fun getById(id: String): LoanEntity? {
        return loanDao.getById(id)
    }

    suspend fun create(
        userUid: String,
        type: String,
        counterpartyName: String,
        accountId: String,
        currency: String,
        principalCents: Long,
        occurredAtEpochSec: Long,
        notes: String?
    ): LoanEntity {
        require(accountId.isNotBlank()) { "accountId requerido" }

        val (loanCategoryId, _) = categoryRepository.ensureSystemLoanCategories(userUid)

        val now = System.currentTimeMillis() / 1000
        val loan = LoanEntity(
            id = UUID.randomUUID().toString(),
            userUid = userUid,
            type = type,
            counterpartyName = counterpartyName,
            accountId = accountId,
            currency = currency,
            principalCents = principalCents,
            status = "OPEN",
            notes = notes,
            createdAtEpochSec = occurredAtEpochSec,
            updatedAtEpochSec = occurredAtEpochSec,
            updatedBy = deviceIdProvider.get()
        )
        loanDao.insert(loan)
        syncToFirestore(userUid, loan)

        val kind = when (type) {
            "LENT" -> "LOAN_LENT_OUT"
            "BORROWED" -> "LOAN_BORROWED_IN"
            else -> type
        }

        transactionRepository.create(
            userUid = userUid,
            accountId = accountId,
            categoryId = loanCategoryId,
            kind = kind,
            amountCents = principalCents,
            occurredAtEpochSec = occurredAtEpochSec,
            note = "${kind}: ${counterpartyName}"
        )

        return loan
    }

    suspend fun close(userUid: String, loanId: String): LoanEntity? {
        val existing = loanDao.getById(loanId) ?: return null
        val now = System.currentTimeMillis() / 1000
        val updated = existing.copy(
            status = "CLOSED",
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        loanDao.update(updated)
        syncToFirestore(userUid, updated)
        return updated
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("LoanRepository", "Syncing loans from Firestore user=$userUid")
            val snapshot = firestore.collection("users")
                .document(userUid)
                .collection("loans")
                .get()
                .await()

            val loans = snapshot.documents.mapNotNull { doc ->
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

                    val type = anyString("type") ?: return@mapNotNull null
                    val counterparty = anyString("counterpartyName", "counterparty_name") ?: return@mapNotNull null
                    val accountId = anyString("accountId", "account_id")
                    val currency = anyString("currency") ?: return@mapNotNull null
                    val principalCents = anyLong("principalCents", "principal_cents") ?: return@mapNotNull null
                    val status = anyString("status") ?: "OPEN"
                    val notes = (data["notes"] as? String)
                    val createdAt = anyLong("createdAtEpochSec", "created_at_epoch_sec") ?: (System.currentTimeMillis() / 1000)
                    val updatedAt = anyLong("updatedAtEpochSec", "updated_at_epoch_sec") ?: createdAt
                    val updatedBy = anyString("updatedBy", "updated_by")

                    LoanEntity(
                        id = doc.id,
                        userUid = userUid,
                        type = type,
                        counterpartyName = counterparty,
                        accountId = accountId,
                        currency = currency,
                        principalCents = principalCents,
                        status = status,
                        notes = notes,
                        createdAtEpochSec = createdAt,
                        updatedAtEpochSec = updatedAt,
                        updatedBy = updatedBy
                    )
                } catch (e: Exception) {
                    Log.e("LoanRepository", "Error parsing loan doc=${doc.id}", e)
                    null
                }
            }

            loanDao.insertAll(loans)
        } catch (e: Exception) {
            Log.e("LoanRepository", "Error syncing loans", e)
        }
    }

    private suspend fun syncToFirestore(userUid: String, loan: LoanEntity) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("loans")
                .document(loan.id)
                .set(loan, SetOptions.merge())
                .await()

            firestore.collection("users")
                .document(userUid)
                .collection("loans")
                .document(loan.id)
                .set(mapOf("updatedBy" to deviceIdProvider.get()), SetOptions.merge())
                .await()
        } catch (_: Exception) {
        }
    }
}
