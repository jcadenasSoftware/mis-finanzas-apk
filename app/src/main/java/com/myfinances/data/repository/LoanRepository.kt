package com.myfinances.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myfinances.data.local.dao.LoanDao
import com.myfinances.data.local.entity.LoanEntity
import com.myfinances.data.local.entity.LoanMovementType
import com.myfinances.data.local.entity.TransactionEntity
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
    private val transactionRepository: TransactionRepository,
    private val loanMovementRepository: LoanMovementRepository,
    private val loanPaymentRepository: LoanPaymentRepository,
    private val accountRepository: AccountRepository
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
        val occ = occurredAtEpochSec.takeIf { it > 0 } ?: now

        val existingLoan = loanDao.findActiveByCounterpartyAndType(
            userUid = userUid,
            type = type,
            counterpartyName = counterpartyName
        )

        return if (existingLoan == null) {
            // Validar saldo insuficiente ANTES de crear el préstamo
            val txKindForValidation = when (type) {
                "LENT" -> "LOAN_LENT_OUT"
                "BORROWED" -> "LOAN_BORROWED_IN"
                else -> type
            }
            val currentBalance = accountRepository.computeBalance(userUid, accountId)
            val delta = when (txKindForValidation) {
                "LOAN_LENT_OUT" -> -principalCents
                "LOAN_BORROWED_IN" -> principalCents
                else -> 0L
            }
            if (delta < 0L) {
                val projected = currentBalance + delta
                require(projected >= 0L) { "Saldo insuficiente" }
            }

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
                createdAtEpochSec = occ,
                updatedAtEpochSec = occ,
                updatedBy = deviceIdProvider.get()
            )
            loanDao.insert(loan)
            syncToFirestore(userUid, loan)

            loanMovementRepository.create(
                userUid = userUid,
                loanId = loan.id,
                movementType = LoanMovementType.CREATION.name,
                amountCents = principalCents,
                accountId = accountId,
                linkedTransactionId = null,
                note = notes,
                occurredAtEpochSec = occ
            )

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
                occurredAtEpochSec = occ,
                note = if (type == "LENT") {
                    "Préstamo otorgado a: ${counterpartyName}"
                } else {
                    "Dinero recibido de: ${counterpartyName}"
                }
            )

            loan
        } else {
            // Validar saldo insuficiente ANTES de actualizar el préstamo (TOPUP)
            val txKindForValidation = when (type) {
                "LENT" -> "LOAN_LENT_TOPUP"
                "BORROWED" -> "LOAN_BORROWED_TOPUP"
                else -> type
            }
            val currentBalance = accountRepository.computeBalance(userUid, accountId)
            val delta = when (txKindForValidation) {
                "LOAN_LENT_TOPUP" -> -principalCents
                "LOAN_BORROWED_TOPUP" -> principalCents
                else -> 0L
            }
            if (delta < 0L) {
                val projected = currentBalance + delta
                require(projected >= 0L) { "Saldo insuficiente" }
            }

            val updatedPrincipal = existingLoan.principalCents + principalCents
            val updatedLoan = existingLoan.copy(
                principalCents = updatedPrincipal,
                updatedAtEpochSec = occ,
                updatedBy = deviceIdProvider.get()
            )
            loanDao.update(updatedLoan)
            syncToFirestore(userUid, updatedLoan)

            loanMovementRepository.create(
                userUid = userUid,
                loanId = existingLoan.id,
                movementType = LoanMovementType.TOPUP.name,
                amountCents = principalCents,
                accountId = accountId,
                linkedTransactionId = null,
                note = notes,
                occurredAtEpochSec = occ
            )

            val kind = when (type) {
                "LENT" -> "LOAN_LENT_TOPUP"
                "BORROWED" -> "LOAN_BORROWED_TOPUP"
                else -> type
            }

            transactionRepository.create(
                userUid = userUid,
                accountId = accountId,
                categoryId = loanCategoryId,
                kind = kind,
                amountCents = principalCents,
                occurredAtEpochSec = occ,
                note = if (type == "LENT") {
                    "Aumento de préstamo otorgado a: ${counterpartyName}"
                } else {
                    "Aumento de deuda con: ${counterpartyName}"
                }
            )

            updatedLoan
        }
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

    suspend fun updateLoan(
        userUid: String,
        loanId: String,
        counterpartyName: String? = null,
        accountId: String? = null,
        principalCents: Long? = null,
        notes: String? = null
    ): LoanEntity {
        val existing = loanDao.getById(loanId)
            ?: throw IllegalStateException("Préstamo no encontrado")

        val now = System.currentTimeMillis() / 1000
        val oldPrincipal = existing.principalCents
        val newPrincipal = principalCents ?: oldPrincipal
        val diffCents = newPrincipal - oldPrincipal
        val oldAccountId = existing.accountId
        val newAccountId = accountId ?: oldAccountId
        val accountChanged = oldAccountId != newAccountId

        // Validar nuevo monto
        if (principalCents != null && principalCents <= 0) {
            throw IllegalArgumentException("El monto debe ser mayor a 0")
        }

        // Caso 1: Sin cambio de monto pero con cambio de cuenta
        if (diffCents == 0L && accountChanged) {
            // Validar saldo en la nueva cuenta antes de cambiar
            val isLent = existing.type == "LENT"
            val delta = if (isLent) -oldPrincipal else oldPrincipal
            if (delta < 0L) {
                val currentBalance = accountRepository.computeBalance(userUid, newAccountId ?: throw IllegalStateException("AccountId requerido"))
                val projected = currentBalance + delta
                require(projected >= 0L) { "Saldo insuficiente en la nueva cuenta" }
            }

            // Revertir impacto en cuenta anterior
            val (loanCategoryId, _) = categoryRepository.ensureSystemLoanCategories(userUid)
            val reverseKind = if (isLent) "LOAN_LENT_CORRECTION_IN" else "LOAN_BORROWED_CORRECTION_OUT"
            transactionRepository.create(
                userUid = userUid,
                accountId = oldAccountId ?: throw IllegalStateException("AccountId original requerido"),
                categoryId = loanCategoryId,
                kind = reverseKind,
                amountCents = oldPrincipal,
                occurredAtEpochSec = now,
                note = "Cambio de cuenta: revertir préstamo a ${existing.counterpartyName}"
            )

            // Aplicar impacto en nueva cuenta
            val forwardKind = if (isLent) "LOAN_LENT_OUT" else "LOAN_BORROWED_IN"
            transactionRepository.create(
                userUid = userUid,
                accountId = newAccountId ?: throw IllegalStateException("AccountId requerido"),
                categoryId = loanCategoryId,
                kind = forwardKind,
                amountCents = oldPrincipal,
                occurredAtEpochSec = now,
                note = "Cambio de cuenta: préstamo a ${existing.counterpartyName}"
            )

            val updatedNotes = if (notes != null && notes.isNotBlank()) {
                if (existing.notes.isNullOrBlank()) {
                    notes
                } else {
                    "${existing.notes}\n---\n$notes"
                }
            } else {
                existing.notes
            }

            val updated = existing.copy(
                counterpartyName = counterpartyName ?: existing.counterpartyName,
                accountId = newAccountId ?: throw IllegalStateException("AccountId requerido"),
                notes = updatedNotes,
                updatedAtEpochSec = now,
                updatedBy = deviceIdProvider.get()
            )
            loanDao.update(updated)
            syncToFirestore(userUid, updated)
            return updated
        }

        // Caso 2: Sin cambio de monto ni de cuenta - actualizar solo campos modificados
        if (diffCents == 0L) {
            val updatedNotes = if (notes != null && notes.isNotBlank()) {
                if (existing.notes.isNullOrBlank()) {
                    notes
                } else {
                    "${existing.notes}\n---\n$notes"
                }
            } else {
                existing.notes
            }

            val updated = existing.copy(
                counterpartyName = counterpartyName ?: existing.counterpartyName,
                accountId = accountId ?: existing.accountId,
                notes = updatedNotes,
                updatedAtEpochSec = now,
                updatedBy = deviceIdProvider.get()
            )
            loanDao.update(updated)
            syncToFirestore(userUid, updated)
            return updated
        }

        // Caso 3: Cambio de monto - crear transacción de corrección y movimiento ADJUSTMENT
        val (loanCategoryId, _) = categoryRepository.ensureSystemLoanCategories(userUid)

        // Determinar tipo de transacción de corrección
        val isLent = existing.type == "LENT"
        val isIncrease = diffCents > 0
        val kind = when {
            isLent && isIncrease -> "LOAN_LENT_CORRECTION_OUT"
            isLent && !isIncrease -> "LOAN_LENT_CORRECTION_IN"
            !isLent && isIncrease -> "LOAN_BORROWED_CORRECTION_IN"
            !isLent && !isIncrease -> "LOAN_BORROWED_CORRECTION_OUT"
            else -> throw IllegalStateException("Tipo de corrección no válido")
        }

        var txId: TransactionEntity? = null

        // Si también cambió la cuenta, manejar el cambio de cuenta primero
        if (accountChanged) {
            // Validar saldo en la nueva cuenta antes de cambiar
            val deltaForNewAccount = if (isLent) -newPrincipal else newPrincipal
            if (deltaForNewAccount < 0L) {
                val currentBalance = accountRepository.computeBalance(userUid, newAccountId ?: throw IllegalStateException("AccountId requerido"))
                val projected = currentBalance + deltaForNewAccount
                require(projected >= 0L) { "Saldo insuficiente en la nueva cuenta" }
            }

            // Revertir impacto completo en cuenta anterior
            val reverseKind = if (isLent) "LOAN_LENT_CORRECTION_IN" else "LOAN_BORROWED_CORRECTION_OUT"
            transactionRepository.create(
                userUid = userUid,
                accountId = oldAccountId ?: throw IllegalStateException("AccountId original requerido"),
                categoryId = loanCategoryId,
                kind = reverseKind,
                amountCents = oldPrincipal,
                occurredAtEpochSec = now,
                note = "Cambio de cuenta: revertir préstamo a ${existing.counterpartyName}"
            )

            // Aplicar impacto completo en nueva cuenta con el nuevo monto
            val forwardKind = if (isLent) "LOAN_LENT_OUT" else "LOAN_BORROWED_IN"
            txId = transactionRepository.create(
                userUid = userUid,
                accountId = newAccountId ?: throw IllegalStateException("AccountId requerido"),
                categoryId = loanCategoryId,
                kind = forwardKind,
                amountCents = newPrincipal,
                occurredAtEpochSec = now,
                note = "Cambio de cuenta: préstamo a ${existing.counterpartyName}"
            )
        } else {
            // Solo cambio de monto - crear transacción de corrección
            val txAmount = kotlin.math.abs(diffCents)
            val txNote = if (isLent) {
                "Corrección de préstamo otorgado a: ${existing.counterpartyName}"
            } else {
                "Corrección de deuda con: ${existing.counterpartyName}"
            }
            txId = transactionRepository.create(
                userUid = userUid,
                accountId = newAccountId ?: throw IllegalStateException("AccountId requerido"),
                categoryId = loanCategoryId,
                kind = kind,
                amountCents = txAmount,
                occurredAtEpochSec = now,
                note = txNote
            )
        }

        // Actualizar préstamo
        val updatedNotes = if (notes != null && notes.isNotBlank()) {
            if (existing.notes.isNullOrBlank()) {
                notes
            } else {
                "${existing.notes}\n---\n$notes"
            }
        } else {
            existing.notes
        }

        // Calcular saldo pendiente para reconciliar estado
        val paidCents = loanPaymentRepository.sumPrincipalByLoan(userUid, loanId)
        val pendingCents = kotlin.math.max(0L, newPrincipal - paidCents)
        val newStatus = if (pendingCents <= 0L) "CLOSED" else "OPEN"

        val updated = existing.copy(
            counterpartyName = counterpartyName ?: existing.counterpartyName,
            accountId = accountId ?: existing.accountId,
            principalCents = newPrincipal,
            notes = updatedNotes,
            status = newStatus,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        loanDao.update(updated)
        syncToFirestore(userUid, updated)

        // Crear movimiento ADJUSTMENT solo si no hubo cambio de cuenta
        // (cuando hay cambio de cuenta, ya se crearon transacciones completas)
        if (!accountChanged && txId != null) {
            loanMovementRepository.create(
                userUid = userUid,
                loanId = loanId,
                movementType = LoanMovementType.ADJUSTMENT.name,
                amountCents = diffCents, // Puede ser positivo o negativo
                accountId = newAccountId ?: throw IllegalStateException("AccountId requerido"),
                linkedTransactionId = txId.id,
                note = notes,
                occurredAtEpochSec = now
            )
        }

        return updated
    }

    suspend fun deleteAllByUser(userUid: String) {
        loanDao.deleteAllByUser(userUid)
        deleteAllFromFirestore(userUid)
    }

    private suspend fun deleteAllFromFirestore(userUid: String) {
        try {
            val batch = firestore.batch()
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("loans")
            val snapshot = collectionRef.get().await()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("LoanRepository", "Error deleting all from Firestore", e)
        }
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("LoanRepository", "Syncing loans from Firestore user=$userUid")
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("loans")
            val snapshot = collectionRef
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

            for (loan in loans) {
                val existing = loanDao.getById(loan.id)
                if (existing == null) {
                    loanDao.insert(loan)
                } else if (loan.updatedAtEpochSec > existing.updatedAtEpochSec) {
                    loanDao.update(loan)
                }
            }
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
        } catch (_: Exception) {
        }
    }
}
