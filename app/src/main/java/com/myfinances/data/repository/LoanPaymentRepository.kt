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
    private val transactionRepository: TransactionRepository,
    private val loanMovementRepository: LoanMovementRepository,
    private val transactionDao: com.myfinances.data.local.dao.TransactionDao
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

        val tx = transactionRepository.create(
            userUid = userUid,
            accountId = accountId,
            categoryId = repaymentCategoryId,
            kind = kind,
            amountCents = principalCents,
            occurredAtEpochSec = occurredAtEpochSec,
            note = note ?: when (loan.type) {
                "LENT" -> "Pago recibido de: ${loan.counterpartyName}"
                "BORROWED" -> "Pago realizado a: ${loan.counterpartyName}"
                else -> "Pago de préstamo: ${loan.counterpartyName}"
            }
        )

        // Crear movimiento de pago en loan_movements (siguiendo lógica de Desktop)
        val movementType = when (loan.type) {
            "LENT" -> "PAYMENT_IN"
            "BORROWED" -> "PAYMENT_OUT"
            else -> "PAYMENT"
        }

        loanMovementRepository.create(
            userUid = userUid,
            loanId = loanId,
            movementType = movementType,
            amountCents = principalCents,
            accountId = accountId,
            linkedTransactionId = tx.id,
            note = note,
            occurredAtEpochSec = occurredAtEpochSec
        )

        return payment
    }

    suspend fun deleteAllByUser(userUid: String) {
        loanPaymentDao.deleteAllByUser(userUid)
        deleteAllFromFirestore(userUid)
    }

    private suspend fun deleteAllFromFirestore(userUid: String) {
        try {
            val batch = firestore.batch()
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("loanPayments")
            val snapshot = collectionRef.get().await()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("LoanPaymentRepository", "Error deleting all from Firestore", e)
        }
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("LoanPaymentRepository", "Syncing loanPayments from Firestore user=$userUid")
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("loanPayments")
            val snapshot = collectionRef
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
            var updated = 0
            var skipped = 0
            for (p in payments) {
                try {
                    val existing = loanPaymentDao.getById(p.id)
                    if (existing == null) {
                        loanPaymentDao.insert(p)
                        inserted++
                    } else if (p.updatedAtEpochSec > existing.updatedAtEpochSec) {
                        loanPaymentDao.update(p)
                        updated++
                    }
                } catch (e: SQLiteConstraintException) {
                    skipped++
                    Log.e(
                        "LoanPaymentRepository",
                        "FK error inserting loanPayment id=${p.id} loanId=${p.loanId} accountId=${p.accountId}",
                        e
                    )
                }
            }

            Log.d("LoanPaymentRepository", "LoanPayments inserted=$inserted updated=$updated skipped=$skipped")
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
        } catch (_: Exception) {
        }
    }

    /**
     * Migración retroactiva: crea LoanMovement para pagos históricos que no tienen movimiento.
     * Este método busca pagos sin movimiento correspondiente y crea el movimiento PAYMENT_IN/PAYMENT_OUT.
     */
    suspend fun migrateHistoricalPayments(userUid: String): MigrationResult {
        var migrated = 0
        var skipped = 0
        var errors = 0

        try {
            val payments = loanPaymentDao.getByUser(userUid)
            val allMovements = loanMovementRepository.getAllByUser(userUid).groupBy { it.loanId }

            for (payment in payments) {
                try {
                    // Verificar si ya existe movimiento para este pago
                    val loanMovements = allMovements[payment.loanId] ?: emptyList()
                    val hasMovement = loanMovements.any { movement ->
                        movement.movementType == "PAYMENT_IN" || movement.movementType == "PAYMENT_OUT" &&
                        movement.amountCents == payment.principalCents &&
                        movement.occurredAtEpochSec == payment.occurredAtEpochSec
                    }

                    if (hasMovement) {
                        skipped++
                        continue
                    }

                    // Obtener el préstamo para determinar el tipo de movimiento
                    val loan = loanDao.getById(payment.loanId) ?: run {
                        errors++
                        continue
                    }

                    // Determinar el tipo de movimiento según el tipo de préstamo
                    val movementType = when (loan.type) {
                        "LENT" -> "PAYMENT_IN"
                        "BORROWED" -> "PAYMENT_OUT"
                        else -> "PAYMENT"
                    }

                    // Buscar la transacción asociada por accountId, occurredAtEpochSec, kind y amountCents
                    val kind = when (loan.type) {
                        "LENT" -> "LOAN_REPAYMENT_PRINCIPAL_IN"
                        "BORROWED" -> "LOAN_REPAYMENT_PRINCIPAL_OUT"
                        else -> "LOAN_REPAYMENT_PRINCIPAL_IN"
                    }

                    val transactions = transactionDao.getFiltered(
                        userUid = userUid,
                        accountId = payment.accountId,
                        categoryId = null,
                        fromEpochSec = payment.occurredAtEpochSec,
                        toEpochSec = payment.occurredAtEpochSec,
                        limit = 100
                    )

                    val matchingTransaction = transactions.find { tx ->
                        tx.kind == kind && tx.amountCents == payment.principalCents
                    }

                    // Crear el movimiento de pago
                    loanMovementRepository.create(
                        userUid = userUid,
                        loanId = payment.loanId,
                        movementType = movementType,
                        amountCents = payment.principalCents,
                        accountId = payment.accountId,
                        linkedTransactionId = matchingTransaction?.id,
                        note = payment.note,
                        occurredAtEpochSec = payment.occurredAtEpochSec
                    )

                    migrated++
                } catch (e: Exception) {
                    errors++
                }
            }
        } catch (e: Exception) {
            return MigrationResult(migrated, skipped, errors, e.message)
        }

        return MigrationResult(migrated, skipped, errors, null)
    }

    data class MigrationResult(
        val migrated: Int,
        val skipped: Int,
        val errors: Int,
        val errorMessage: String?
    )
}
