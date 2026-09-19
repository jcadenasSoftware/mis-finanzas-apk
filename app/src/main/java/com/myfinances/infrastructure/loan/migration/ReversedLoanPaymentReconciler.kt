package com.jcadenas.xpendz.infrastructure.loan.migration

import android.util.Log
import com.jcadenas.xpendz.data.local.entity.LoanMovementEntity
import com.jcadenas.xpendz.data.local.entity.LoanPaymentEntity
import com.jcadenas.xpendz.infrastructure.loan.room.CanonicalLoanDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Artefactos de transporte de un pago revertido: fila/doc `loan_payments`. */
interface ReversedLoanPaymentStore {
    suspend fun deleteByPaymentSignature(
        userUid: String,
        loanId: String,
        accountId: String?,
        principalCents: Long,
        occurredAtEpochSec: Long,
        linkedTransactionId: String?
    ): LoanPaymentEntity?

    suspend fun deleteFromFirestore(userUid: String, paymentId: String)
}

/** Artefactos de transporte de un pago revertido: movimiento `PAYMENT_*`. */
interface ReversedLoanMovementStore {
    suspend fun deletePaymentBySignature(
        userUid: String,
        loanId: String,
        accountId: String?,
        amountCents: Long,
        occurredAtEpochSec: Long,
        linkedTransactionId: String?
    ): LoanMovementEntity?
}

/** Transacción `LOAN_REPAYMENT_*` asociada a un pago revertido. */
interface ReversedLoanTransactionStore {
    suspend fun deleteFailedLoanTransaction(userUid: String, transactionId: String)
}

/**
 * Garantiza que toda reversión presente en el journal canónico tenga sus
 * artefactos de transporte eliminados, incluso si la reversión ocurrió antes de
 * la corrección de identidad o el borrado remoto quedó sin confirmar.
 *
 * Para cada evento REVERSAL apuntando a un PAYMENT se elimina:
 * - la fila/doc `loan_payments` (localizada por transactionId/firma, además del
 *   doc remoto indexado por el eventId original);
 * - el movimiento PAYMENT_* vinculado (local y remoto);
 * - la transacción LOAN_REPAYMENT_* vinculada (local y remota).
 *
 * Es idempotente y seguro offline: los borrados remotos del SDK se encolan.
 */
@Singleton
class ReversedLoanPaymentReconciler @Inject constructor(
    private val canonicalLoanDao: CanonicalLoanDao,
    private val loanPaymentRepository: ReversedLoanPaymentStore,
    private val loanMovementRepository: ReversedLoanMovementStore,
    private val transactionRepository: ReversedLoanTransactionStore
) {
    companion object {
        private const val TAG = "ReversedPaymentSync"
    }

    suspend fun reconcile(userUid: String) = withContext(Dispatchers.IO) {
        val reversals = canonicalLoanDao.reversalEvents(userUid)
        var cleaned = 0
        for (reversal in reversals) {
            val targetId = reversal.payloadTargetEventId ?: continue
            val target = canonicalLoanDao.findByEventId(userUid, targetId) ?: continue
            if (target.eventType != "PAYMENT") continue
            val amount = target.amountCents ?: continue
            val txId = target.transactionId
            try {
                loanPaymentRepository.deleteByPaymentSignature(
                    userUid = userUid,
                    loanId = target.loanId,
                    accountId = target.accountId,
                    principalCents = amount,
                    occurredAtEpochSec = target.occurredAt,
                    linkedTransactionId = txId
                )
                loanPaymentRepository.deleteFromFirestore(userUid, targetId)
                loanMovementRepository.deletePaymentBySignature(
                    userUid = userUid,
                    loanId = target.loanId,
                    accountId = target.accountId,
                    amountCents = amount,
                    occurredAtEpochSec = target.occurredAt,
                    linkedTransactionId = txId
                )
                if (txId != null) {
                    transactionRepository.deleteFailedLoanTransaction(userUid, txId)
                }
                cleaned++
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo limpiar la reversión de $targetId: ${e.message}")
            }
        }
        if (cleaned > 0) {
            Log.d(TAG, "Reversiones reconciliadas: $cleaned/${reversals.size}")
        }
    }
}
