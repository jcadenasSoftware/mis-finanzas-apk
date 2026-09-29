package com.jcadenas.xpendz.application.obligation

import androidx.room.withTransaction
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.data.local.dao.AccountDao
import com.jcadenas.xpendz.data.local.dao.ObligationDao
import com.jcadenas.xpendz.data.local.dao.ObligationSettlementDao
import com.jcadenas.xpendz.data.local.dao.TransactionDao
import com.jcadenas.xpendz.data.local.entity.ObligationEntity
import com.jcadenas.xpendz.data.local.entity.ObligationSettlementEntity
import com.jcadenas.xpendz.data.local.entity.TransactionEntity
import com.jcadenas.xpendz.data.repository.ObligationRepository
import com.jcadenas.xpendz.data.repository.ObligationSettlementRepository
import com.jcadenas.xpendz.sync.DeviceIdProvider
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObligationService @Inject constructor(
    private val database: AppDatabase,
    private val obligationRepository: ObligationRepository,
    private val obligationSettlementRepository: ObligationSettlementRepository,
    private val obligationDao: ObligationDao,
    private val obligationSettlementDao: ObligationSettlementDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val deviceIdProvider: DeviceIdProvider
) {
    internal var afterTransactionMutationHook: (() -> Unit)? = null

    suspend fun createObligation(
        userUid: String,
        type: String,
        title: String,
        counterpartyName: String,
        currency: String,
        originalAmountCents: Long,
        issuedAtEpochSec: Long,
        dueAtEpochSec: Long? = null,
        obligationCategoryId: String? = null,
        reference: String? = null,
        notes: String? = null
    ): ObligationEntity = obligationRepository.create(
        userUid = userUid,
        type = type,
        title = title,
        counterpartyName = counterpartyName,
        currency = currency,
        originalAmountCents = originalAmountCents,
        issuedAtEpochSec = issuedAtEpochSec,
        dueAtEpochSec = dueAtEpochSec,
        obligationCategoryId = obligationCategoryId,
        reference = reference,
        notes = notes
    )

    suspend fun updateObligationMetadata(
        userUid: String,
        obligationId: String,
        type: String,
        title: String,
        counterpartyName: String,
        currency: String,
        originalAmountCents: Long,
        issuedAtEpochSec: Long,
        dueAtEpochSec: Long?,
        obligationCategoryId: String?,
        reference: String?,
        notes: String?
    ): ObligationEntity {
        val obligation = requireObligation(userUid, obligationId)
        val totalSettled = obligationSettlementDao.getTotalSettledCents(obligation.id)
        require(originalAmountCents > 0L) { "originalAmountCents" }
        check(originalAmountCents >= totalSettled) { "obligation_amount_below_settled" }
        return obligationRepository.update(
            obligationId = obligation.id,
            type = type,
            title = title,
            counterpartyName = counterpartyName,
            currency = currency,
            originalAmountCents = originalAmountCents,
            issuedAtEpochSec = issuedAtEpochSec,
            dueAtEpochSec = dueAtEpochSec,
            obligationCategoryId = obligationCategoryId,
            reference = reference,
            notes = notes
        )
    }

    suspend fun cancelObligation(
        userUid: String,
        obligationId: String,
        cancelledAtEpochSec: Long = currentEpochSec()
    ): ObligationEntity {
        val obligation = requireObligation(userUid, obligationId)
        if (obligation.cancelledAtEpochSec != null) {
            return obligation
        }
        return obligationRepository.cancel(obligation.id, cancelledAtEpochSec)
    }

    suspend fun registerSettlement(
        userUid: String,
        obligationId: String,
        accountId: String,
        financialCategoryId: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ): SettlementMutationResult {
        val result = database.withTransaction {
            val obligation = requireObligation(userUid, obligationId)
            val currentState = resolveState(obligation, obligationSettlementDao.getTotalSettledCents(obligation.id), currentEpochSec())
            validateSettlementAllowed(currentState)
            validateSettlementAmount(amountCents, currentState.pendingAmountCents)

            val now = currentEpochSec()
            val txKind = settlementTransactionKind(obligation.type)
            requireCompatibleFinancialCategory(requireNonBlank(financialCategoryId, "financialCategoryId"), txKind)
            requireNonNegativeBalanceAfterCreate(userUid, accountId, txKind, amountCents)
            val normalizedNote = normalizeOptionalText(note)
            val updatedBy = deviceIdProvider.get()
            val transaction = TransactionEntity(
                id = UUID.randomUUID().toString(),
                userUid = userUid,
                accountId = accountId,
                categoryId = requireNonBlank(financialCategoryId, "financialCategoryId"),
                kind = txKind,
                amountCents = amountCents,
                occurredAtEpochSec = occurredAtEpochSec,
                note = normalizedNote,
                createdAtEpochSec = now,
                updatedAtEpochSec = now,
                updatedBy = updatedBy
            )
            transactionDao.insert(transaction)
            afterTransactionMutationHook?.invoke()

            val settlement = ObligationSettlementEntity(
                id = UUID.randomUUID().toString(),
                userUid = userUid,
                obligationId = obligation.id,
                accountId = accountId,
                amountCents = amountCents,
                occurredAtEpochSec = occurredAtEpochSec,
                linkedTransactionId = transaction.id,
                note = normalizedNote,
                createdAtEpochSec = now,
                updatedAtEpochSec = now,
                updatedBy = updatedBy
            )
            obligationSettlementDao.insert(settlement)

            SettlementMutationResult(
                obligation = obligation,
                settlement = settlement,
                transaction = transaction,
                resolvedState = resolveState(
                    obligation = obligation,
                    totalSettledCents = obligationSettlementDao.getTotalSettledCents(obligation.id),
                    nowEpochSec = currentEpochSec()
                )
            )
        }
        obligationSettlementRepository.publishSettlement(result.settlement)
        return result
    }

    suspend fun updateSettlement(
        userUid: String,
        settlementId: String,
        accountId: String,
        financialCategoryId: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ): SettlementMutationResult {
        val result = database.withTransaction {
            val settlement = requireSettlement(userUid, settlementId)
            val obligation = requireObligation(userUid, settlement.obligationId)
            check(obligation.cancelledAtEpochSec == null) { "obligation_cancelled" }
            val existingTransaction = requireTransaction(settlement.linkedTransactionId, userUid)
            val totalSettled = obligationSettlementDao.getTotalSettledCents(obligation.id)
            val settledExcludingCurrent = totalSettled - settlement.amountCents
            val allowedAmount = obligation.originalAmountCents - settledExcludingCurrent
            validateSettlementAmount(amountCents, allowedAmount)

            val txKind = settlementTransactionKind(obligation.type)
            requireCompatibleFinancialCategory(requireNonBlank(financialCategoryId, "financialCategoryId"), txKind)
            requireNonNegativeBalanceAfterUpdate(
                userUid = userUid,
                existing = existingTransaction,
                newAccountId = accountId,
                newKind = txKind,
                newAmountCents = amountCents
            )

            val normalizedNote = normalizeOptionalText(note)
            val updatedTx = existingTransaction.copy(
                accountId = requireNonBlank(accountId, "accountId"),
                categoryId = requireNonBlank(financialCategoryId, "financialCategoryId"),
                kind = txKind,
                amountCents = amountCents,
                occurredAtEpochSec = occurredAtEpochSec,
                note = normalizedNote,
                updatedAtEpochSec = nextUpdatedAt(existingTransaction.updatedAtEpochSec),
                updatedBy = deviceIdProvider.get()
            )
            transactionDao.update(updatedTx)
            afterTransactionMutationHook?.invoke()

            val updatedSettlement = settlement.copy(
                accountId = updatedTx.accountId,
                amountCents = amountCents,
                occurredAtEpochSec = occurredAtEpochSec,
                note = normalizedNote,
                updatedAtEpochSec = nextUpdatedAt(settlement.updatedAtEpochSec),
                updatedBy = updatedTx.updatedBy
            )
            obligationSettlementDao.update(updatedSettlement)

            SettlementMutationResult(
                obligation = obligation,
                settlement = updatedSettlement,
                transaction = updatedTx,
                resolvedState = resolveState(
                    obligation = obligation,
                    totalSettledCents = obligationSettlementDao.getTotalSettledCents(obligation.id),
                    nowEpochSec = currentEpochSec()
                )
            )
        }
        obligationSettlementRepository.publishSettlement(result.settlement)
        return result
    }

    suspend fun deleteSettlement(
        userUid: String,
        settlementId: String
    ): DeleteSettlementResult {
        val result = database.withTransaction {
            val settlement = requireSettlement(userUid, settlementId)
            val obligation = requireObligation(userUid, settlement.obligationId)
            val transaction = requireTransaction(settlement.linkedTransactionId, userUid)

            obligationSettlementDao.delete(settlement.id)
            afterTransactionMutationHook?.invoke()
            transactionDao.delete(transaction.id)

            DeleteSettlementResult(
                deletedSettlementId = settlement.id,
                deletedTransactionId = transaction.id,
                resolvedState = resolveState(
                    obligation = obligation,
                    totalSettledCents = obligationSettlementDao.getTotalSettledCents(obligation.id),
                    nowEpochSec = currentEpochSec()
                )
            )
        }
        obligationSettlementRepository.publishSettlementDeleted(userUid, result.deletedSettlementId, result.deletedTransactionId)
        return result
    }

    suspend fun getResolvedState(
        userUid: String,
        obligationId: String,
        nowEpochSec: Long = currentEpochSec()
    ): ResolvedObligationState {
        val obligation = requireObligation(userUid, obligationId)
        return resolveState(obligation, obligationSettlementDao.getTotalSettledCents(obligation.id), nowEpochSec)
    }

    private suspend fun requireObligation(userUid: String, obligationId: String): ObligationEntity {
        val obligation = obligationDao.getById(obligationId) ?: throw IllegalArgumentException("obligation_not_found")
        check(obligation.userUid == userUid) { "obligation_not_found" }
        return obligation
    }

    private suspend fun requireSettlement(userUid: String, settlementId: String): ObligationSettlementEntity {
        val settlement = obligationSettlementDao.getById(settlementId) ?: throw IllegalArgumentException("settlement_not_found")
        check(settlement.userUid == userUid) { "settlement_not_found" }
        return settlement
    }

    private suspend fun requireTransaction(transactionId: String, userUid: String): TransactionEntity {
        val transaction = transactionDao.getById(transactionId) ?: throw IllegalStateException("linked_transaction_missing")
        check(transaction.userUid == userUid) { "linked_transaction_missing" }
        return transaction
    }

    private suspend fun requireNonNegativeBalanceAfterCreate(
        userUid: String,
        accountId: String,
        kind: String,
        amountCents: Long
    ) {
        val delta = signedAmountDeltaCents(kind, amountCents)
        if (delta >= 0L) {
            return
        }
        val currentBalance = accountDao.computeBalanceCents(userUid, accountId)
        require(currentBalance + delta >= 0L) { "Saldo insuficiente" }
    }

    private suspend fun requireNonNegativeBalanceAfterUpdate(
        userUid: String,
        existing: TransactionEntity,
        newAccountId: String,
        newKind: String,
        newAmountCents: Long
    ) {
        val affectedAccountIds = linkedSetOf(existing.accountId, newAccountId)
        for (accountId in affectedAccountIds) {
            val currentBalance = accountDao.computeBalanceCents(userUid, accountId)
            val revertOld = if (accountId == existing.accountId) -signedAmountDeltaCents(existing.kind, existing.amountCents) else 0L
            val applyNew = if (accountId == newAccountId) signedAmountDeltaCents(newKind, newAmountCents) else 0L
            require(currentBalance + revertOld + applyNew >= 0L) { "Saldo insuficiente" }
        }
    }

    private fun validateSettlementAllowed(state: ResolvedObligationState) {
        check(state.status != ObligationResolvedStatus.CANCELADA) { "obligation_cancelled" }
        check(state.status != ObligationResolvedStatus.PAGADA) { "obligation_paid" }
    }

    private fun validateSettlementAmount(amountCents: Long, maxAmountCents: Long) {
        require(amountCents > 0L) { "amountCents" }
        check(amountCents <= maxAmountCents) { "settlement_exceeds_pending" }
    }

    private fun resolveState(
        obligation: ObligationEntity,
        totalSettledCents: Long,
        nowEpochSec: Long
    ): ResolvedObligationState {
        check(totalSettledCents <= obligation.originalAmountCents) { "obligation_overpaid" }
        val pendingAmountCents = obligation.originalAmountCents - totalSettledCents
        val status = when {
            obligation.cancelledAtEpochSec != null -> ObligationResolvedStatus.CANCELADA
            pendingAmountCents == 0L -> ObligationResolvedStatus.PAGADA
            obligation.dueAtEpochSec != null && obligation.dueAtEpochSec < nowEpochSec -> ObligationResolvedStatus.VENCIDA
            totalSettledCents == 0L -> ObligationResolvedStatus.PENDIENTE
            else -> ObligationResolvedStatus.PARCIAL
        }
        return ResolvedObligationState(
            obligationId = obligation.id,
            status = status,
            originalAmountCents = obligation.originalAmountCents,
            totalSettledCents = totalSettledCents,
            pendingAmountCents = pendingAmountCents,
            dueAtEpochSec = obligation.dueAtEpochSec,
            cancelledAtEpochSec = obligation.cancelledAtEpochSec
        )
    }

    // La categoría financiera del abono vive en la Transaction: su kind debe ser
    // compatible con el tipo de movimiento (cobro → INCOME, pago → EXPENSE).
    // "BOTH" o kind vacío (legado, hereda del padre) son compatibles.
    private suspend fun requireCompatibleFinancialCategory(categoryId: String, txKind: String) {
        val category = database.categoryDao().getById(categoryId)
            ?: throw IllegalArgumentException("settlement_category_not_found")
        var effectiveKind = category.kind.trim()
        if (effectiveKind.isEmpty() && !category.parentId.isNullOrBlank()) {
            effectiveKind = database.categoryDao().getById(category.parentId)?.kind?.trim().orEmpty()
        }
        if (effectiveKind.isNotEmpty()
            && !effectiveKind.equals("BOTH", ignoreCase = true)
            && !effectiveKind.equals(txKind, ignoreCase = true)
        ) {
            throw IllegalStateException("settlement_category_kind_mismatch")
        }
    }

    private fun settlementTransactionKind(obligationType: String): String = when (ObligationEntity.normalizeType(obligationType)) {
        ObligationEntity.TYPE_RECEIVABLE -> "INCOME"
        ObligationEntity.TYPE_PAYABLE -> "EXPENSE"
        else -> throw IllegalArgumentException("type")
    }

    private fun signedAmountDeltaCents(kind: String, amountCents: Long): Long = when (kind.trim().uppercase()) {
        "INCOME" -> amountCents
        "EXPENSE" -> -amountCents
        else -> 0L
    }

    private fun normalizeOptionalText(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun requireNonBlank(value: String, field: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { field }
        return normalized
    }

    private fun nextUpdatedAt(previous: Long): Long {
        val now = currentEpochSec()
        return if (now > previous) now else previous + 1L
    }

    private fun currentEpochSec(): Long = System.currentTimeMillis() / 1000
}

enum class ObligationResolvedStatus {
    CANCELADA,
    PAGADA,
    VENCIDA,
    PENDIENTE,
    PARCIAL
}

data class ResolvedObligationState(
    val obligationId: String,
    val status: ObligationResolvedStatus,
    val originalAmountCents: Long,
    val totalSettledCents: Long,
    val pendingAmountCents: Long,
    val dueAtEpochSec: Long?,
    val cancelledAtEpochSec: Long?
)

data class SettlementMutationResult(
    val obligation: ObligationEntity,
    val settlement: ObligationSettlementEntity,
    val transaction: TransactionEntity,
    val resolvedState: ResolvedObligationState
)

data class DeleteSettlementResult(
    val deletedSettlementId: String,
    val deletedTransactionId: String,
    val resolvedState: ResolvedObligationState
)
