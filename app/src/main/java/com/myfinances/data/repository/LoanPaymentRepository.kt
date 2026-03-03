package com.myfinances.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myfinances.data.local.dao.LoanDao
import com.myfinances.data.local.dao.LoanPaymentDao
import com.myfinances.data.local.entity.LoanPaymentEntity
import com.myfinances.sync.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanPaymentRepository @Inject constructor(
    private val loanPaymentDao: LoanPaymentDao,
    private val loanDao: LoanDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) {
    fun observeByLoan(userUid: String, loanId: String): Flow<List<LoanPaymentEntity>> {
        return loanPaymentDao.observeByLoan(userUid, loanId)
    }

    suspend fun sumPrincipalByLoan(userUid: String, loanId: String): Long {
        return loanPaymentDao.sumPrincipalByLoan(userUid, loanId)
    }

    suspend fun create(
        userUid: String,
        loanId: String,
        accountId: String,
        principalCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ): LoanPaymentEntity {
        require(accountId.isNotBlank()) { "accountId requerido" }

        val loan = loanDao.getById(loanId) ?: error("Loan no encontrado: $loanId")
        val (_, repaymentCategoryId) = categoryRepository.ensureSystemLoanCategories(userUid)

        val now = System.currentTimeMillis() / 1000
        val payment = LoanPaymentEntity(
            id = UUID.randomUUID().toString(),
            userUid = userUid,
            loanId = loanId,
            accountId = accountId,
            principalCents = principalCents,
            occurredAtEpochSec = occurredAtEpochSec,
            note = note,
            createdAtEpochSec = now,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        loanPaymentDao.insert(payment)
        syncToFirestore(userUid, payment)

        val kind = when (loan.type) {
            "LENT" -> "LOAN_REPAYMENT_PRINCIPAL_IN"
            "BORROWED" -> "LOAN_REPAYMENT_PRINCIPAL_OUT"
            else -> "LOAN_REPAYMENT_PRINCIPAL_IN"
        }

        transactionRepository.create(
            userUid = userUid,
            accountId = accountId,
            categoryId = repaymentCategoryId,
            kind = kind,
            amountCents = principalCents,
            occurredAtEpochSec = occurredAtEpochSec,
            note = note ?: "${kind}: ${loan.counterpartyName}"
        )

        return payment
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("LoanPaymentRepository", "Syncing loanPayments from Firestore user=$userUid")
            val snapshot = firestore.collection("users")
                .document(userUid)
                .collection("loanPayments")
                .get()
                .await()

            val payments = snapshot.documents.mapNotNull { doc ->
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

                    val loanId = anyString("loanId", "loan_id") ?: return@mapNotNull null
                    val accountId = anyString("accountId", "account_id") ?: return@mapNotNull null
                    val principalCents = anyLong("principalCents", "principal_cents") ?: return@mapNotNull null
                    val occurredAt = anyLong("occurredAtEpochSec", "occurred_at_epoch_sec") ?: return@mapNotNull null
                    val createdAt = anyLong("createdAtEpochSec", "created_at_epoch_sec") ?: (System.currentTimeMillis() / 1000)
                    val updatedAt = anyLong("updatedAtEpochSec", "updated_at_epoch_sec") ?: createdAt
                    val note = (data["note"] as? String)
                    val updatedBy = anyString("updatedBy", "updated_by")

                    LoanPaymentEntity(
                        id = doc.id,
                        userUid = userUid,
                        loanId = loanId,
                        accountId = accountId,
                        principalCents = principalCents,
                        occurredAtEpochSec = occurredAt,
                        note = note,
                        createdAtEpochSec = createdAt,
                        updatedAtEpochSec = updatedAt,
                        updatedBy = updatedBy
                    )
                } catch (e: Exception) {
                    Log.e("LoanPaymentRepository", "Error parsing loanPayment doc=${doc.id}", e)
                    null
                }
            }

            var inserted = 0
            var skipped = 0
            for (p in payments) {
                try {
                    loanPaymentDao.insert(p)
                    inserted++
                } catch (e: SQLiteConstraintException) {
                    skipped++
                    Log.e(
                        "LoanPaymentRepository",
                        "FK error inserting loanPayment id=${p.id} loanId=${p.loanId} accountId=${p.accountId}",
                        e
                    )
                }
            }

            Log.d("LoanPaymentRepository", "LoanPayments inserted=$inserted skipped=$skipped")
        } catch (e: Exception) {
            Log.e("LoanPaymentRepository", "Error syncing loanPayments", e)
        }
    }

    private suspend fun syncToFirestore(userUid: String, payment: LoanPaymentEntity) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("loanPayments")
                .document(payment.id)
                .set(payment, SetOptions.merge())
                .await()

            firestore.collection("users")
                .document(userUid)
                .collection("loanPayments")
                .document(payment.id)
                .set(mapOf("updatedBy" to deviceIdProvider.get()), SetOptions.merge())
                .await()
        } catch (_: Exception) {
        }
    }
}
