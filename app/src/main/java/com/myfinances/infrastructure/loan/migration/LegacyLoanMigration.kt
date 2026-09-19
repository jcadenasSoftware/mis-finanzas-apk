package com.jcadenas.xpendz.infrastructure.loan.migration

import android.util.Log
import com.jcadenas.xpendz.application.loan.LoanApplicationService
import com.jcadenas.xpendz.data.local.dao.LoanDao
import com.jcadenas.xpendz.data.local.dao.LoanMovementDao
import com.jcadenas.xpendz.data.local.dao.LoanPaymentDao
import com.jcadenas.xpendz.data.local.dao.TransactionDao
import com.jcadenas.xpendz.data.local.entity.LoanEntity
import com.jcadenas.xpendz.data.local.entity.LoanMovementEntity
import com.jcadenas.xpendz.data.local.entity.LoanPaymentEntity
import com.jcadenas.xpendz.domain.loan.aggregate.Outcome
import com.jcadenas.xpendz.domain.loan.commands.AddPrincipalCommand
import com.jcadenas.xpendz.domain.loan.commands.CloseLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.CreateLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.LoanCommand
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandEnvelope
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandType
import com.jcadenas.xpendz.domain.loan.commands.RegisterPaymentCommand
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.infrastructure.loan.projection.room.LoanProjectionDao
import com.jcadenas.xpendz.infrastructure.loan.room.CanonicalLoanDao
import com.jcadenas.xpendz.sync.DeviceIdProvider
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jcadenas.xpendz.infrastructure.loan.replay.HistoricalLoanReplayTool

/**
 * Equivalente Android de LegacyLoanMigration (Desktop).
 * Ingesta préstamos legacy (loans + loan_movements) en el journal canónico
 * usando exclusivamente [LoanApplicationService.process].
 * Idempotente: un préstamo con cualquier evento en loan_journal_v1 se omite,
 * y el reconciliador deduplica pagos por firma/transactionId/operationId.
 */
class LegacyLoanMigration @Inject constructor(
    private val loanDao: LoanDao,
    private val loanMovementDao: LoanMovementDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val transactionDao: TransactionDao,
    private val canonicalLoanDao: CanonicalLoanDao,
    private val loanProjectionDao: LoanProjectionDao,
    private val deviceIdProvider: DeviceIdProvider,
    private val loanApplicationService: LoanApplicationService,
    private val paymentReconciler: HistoricalLoanPaymentReconciler,
    private val historicalLoanReplayTool: HistoricalLoanReplayTool
) {
    companion object {
        private const val TAG = "LegacyLoanMigration"
        private val PAYMENT_MOVEMENT_TYPES = setOf("PAYMENT", "PAYMENT_IN", "PAYMENT_OUT")
    }

    data class MigrationReport(
        val loansMigrated: Int,
        val loansAlreadyCanonical: Int,
        val loansFailed: Int,
        val reconciliation: HistoricalLoanPaymentReconciler.ReconciliationReport
    )

    suspend fun migrate(userUid: String): MigrationReport = withContext(Dispatchers.IO) {
        val loans = loanDao.getByUser(userUid)
            .sortedWith(compareBy<LoanEntity> { it.createdAtEpochSec }.thenBy { it.id })
        val localDeviceId = deviceIdProvider.get()

        var migrated = 0
        var alreadyCanonical = 0
        var failed = 0
        val driftedLoans = mutableListOf<LoanEntity>()

        for (loan in loans) {
            if (!alreadyCanonical(userUid, loan.id)) {
                val movements = loanMovementDao.getByLoan(userUid, loan.id)
                val fingerprint = migrateLoan(loan, movements)
                if (fingerprint == null) {
                    failed++
                    continue
                }
                migrateMovements(loan, movements, fingerprint)
                migrated++
                continue
            }

            alreadyCanonical++
            if (loan.updatedBy == localDeviceId) {
                continue
            }
            if (hasCanonicalDrift(loan)) {
                driftedLoans += loan
            }
        }

        val reconciliation = paymentReconciler.reconcile(userUid)

        var replayedDrift = 0
        var replayFailed = 0
        for (loan in driftedLoans) {
            if (replayDriftedLoan(loan)) {
                replayedDrift++
            } else {
                replayFailed++
            }
        }

        MigrationReport(migrated, alreadyCanonical, failed, reconciliation).also {
            Log.d(
                TAG,
                "migrate migrated=$migrated alreadyCanonical=$alreadyCanonical failed=$failed " +
                    "replayedDrift=$replayedDrift replayFailed=$replayFailed " +
                    "reconciled=${reconciliation.paymentsReconciled} omitted=${reconciliation.paymentsOmitted}"
            )
        }
    }

    private fun alreadyCanonical(userUid: String, loanId: String): Boolean =
        canonicalLoanDao.getJournal(userUid, loanId).isNotEmpty()

    private suspend fun hasCanonicalDrift(loan: LoanEntity): Boolean {
        val snapshot = canonicalLoanDao.loadSnapshot(loan.userUid, loan.id) ?: return true
        val summary = loanProjectionDao.getSummaryProjection(loan.userUid, loan.id) ?: return true

        if (loan.principalCents != snapshot.principalCents) return true
        if (normalize(loan.counterpartyName) != normalize(snapshot.counterpartyName)) return true
        if (normalize(loan.currency) != normalize(snapshot.currency)) return true
        if (loan.type.uppercase() != snapshot.loanType.uppercase()) return true
        if (normalize(loan.accountId) != normalize(snapshot.defaultAccountId)) return true
        if (normalize(loan.notes) != normalize(snapshot.notes)) return true

        val expected = expectedPayments(loan)
        if (summary.paymentCount.toLong() != expected[0]) return true
        if (summary.totalPaidCents != expected[1]) return true
        return false
    }

    private suspend fun replayDriftedLoan(loan: LoanEntity): Boolean {
        return try {
            val result = historicalLoanReplayTool.replay(loan.userUid, loan.id)
            if (result.success) {
                Log.d(TAG, "replayed drifted loan ${loan.id} eventsApplied=${result.eventsApplied}")
                true
            } else {
                Log.w(TAG, "replay failed for ${loan.id} status=${result.status} errors=${result.errors}")
                false
            }
        } catch (ex: Exception) {
            Log.w(TAG, "replay error for ${loan.id}: ${ex.message}")
            false
        }
    }

    /**
     * Conjunto esperado de pagos, espejo del timeline del replay: movements de
     * tipo pago más las filas loan_payments no cubiertas por el
     * linkedTransactionId de un movement. Las transacciones LOAN_* no llevan
     * loan_id, así que una repayment huérfana de la misma cuenta no puede
     * atribuirse a este préstamo (podría pertenecer a otro).
     */
    private suspend fun expectedPayments(loan: LoanEntity): LongArray {
        val movements = loanMovementDao.getByLoan(loan.userUid, loan.id)
        val movementTxIds = movements.mapNotNull { it.linkedTransactionId }.toSet()
        val paymentMovements = movements.filter { it.movementType.uppercase() in PAYMENT_MOVEMENT_TYPES }
        val paymentRows = loanPaymentDao.getByUser(loan.userUid)
            .filter { it.loanId == loan.id }
            .filter { it.linkedTransactionId == null || it.linkedTransactionId !in movementTxIds }
        return longArrayOf(
            (paymentMovements.size + paymentRows.size).toLong(),
            paymentMovements.sumOf { it.amountCents } + paymentRows.sumOf { it.principalCents }
        )
    }

    private fun normalize(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }

    private fun migrateLoan(loan: LoanEntity, movements: List<LoanMovementEntity>): String? {
        var initialPrincipal = loan.principalCents
        var occurredAt = loan.createdAtEpochSec
        var transactionId: String? = null

        val creation = movements.firstOrNull { it.movementType.equals("CREATION", ignoreCase = true) }
        if (creation != null) {
            initialPrincipal = creation.amountCents
            occurredAt = creation.occurredAtEpochSec
            transactionId = creation.linkedTransactionId
        }

        val envelope = LoanCommandEnvelope(
            commandType = LoanCommandType.CREATE_LOAN,
            operationId = CanonicalLoanEventIds.deterministic(
                loan.id,
                "CREATION",
                creation?.let { "mov:${it.id}" } ?: "loan:${loan.id}",
                occurredAt
            ),
            loanId = loan.id,
            ownerId = loan.userUid,
            expectedJournalFingerprint = null,
            occurredAt = occurredAt,
            actorId = loan.userUid,
            originId = loan.userUid
        )

        return try {
            val result = loanApplicationService.process(
                CreateLoanCommand(
                    envelope = envelope,
                    loanType = LoanType.valueOf(loan.type.uppercase()),
                    initialPrincipalCents = initialPrincipal,
                    counterpartyName = loan.counterpartyName,
                    currency = loan.currency,
                    defaultAccountId = loan.accountId,
                    transactionId = transactionId,
                    notes = loan.notes
                )
            )
            if (result.outcome == Outcome.APPLIED) result.currentSnapshot.journalFingerprint else null
        } catch (ex: Exception) {
            Log.w(TAG, "No se pudo migrar préstamo ${loan.id}: ${ex.message}")
            null
        }
    }

    private fun migrateMovements(loan: LoanEntity, movements: List<LoanMovementEntity>, initialFingerprint: String) {
        var fingerprint = initialFingerprint
        for (movement in movements) {
            if (movement.movementType.equals("CREATION", ignoreCase = true)) {
                continue
            }
            val nextFingerprint = when (movement.movementType.uppercase()) {
                "TOPUP" -> processMovement(loan, movement, fingerprint, LoanCommandType.ADD_PRINCIPAL) { envelope ->
                    AddPrincipalCommand(envelope, movement.amountCents, movement.accountId, movement.linkedTransactionId, movement.note)
                }
                "PAYMENT_IN", "PAYMENT_OUT" -> processMovement(loan, movement, fingerprint, LoanCommandType.REGISTER_PAYMENT) { envelope ->
                    RegisterPaymentCommand(envelope, movement.amountCents, movement.accountId, movement.linkedTransactionId, movement.note)
                }
                "CLOSE" -> processMovement(loan, movement, fingerprint, LoanCommandType.CLOSE_LOAN) { envelope ->
                    CloseLoanCommand(envelope, movement.note ?: "Cierre", movement.note)
                }
                else -> null
            }
            if (nextFingerprint != null) {
                fingerprint = nextFingerprint
            }
        }
    }

    private fun processMovement(
        loan: LoanEntity,
        movement: LoanMovementEntity,
        fingerprint: String,
        type: LoanCommandType,
        build: (LoanCommandEnvelope) -> LoanCommand
    ): String? {
        val eventType = when (type) {
            LoanCommandType.ADD_PRINCIPAL -> "TOPUP"
            LoanCommandType.REGISTER_PAYMENT -> "PAYMENT"
            LoanCommandType.CLOSE_LOAN -> "CLOSE"
            else -> type.name
        }
        val envelope = LoanCommandEnvelope(
            commandType = type,
            operationId = CanonicalLoanEventIds.deterministic(
                loan.id,
                eventType,
                "mov:${movement.id}",
                movement.occurredAtEpochSec
            ),
            loanId = loan.id,
            ownerId = loan.userUid,
            expectedJournalFingerprint = fingerprint,
            occurredAt = movement.occurredAtEpochSec,
            actorId = loan.userUid,
            originId = loan.userUid
        )
        return try {
            val result = loanApplicationService.process(build(envelope))
            if (result.outcome == Outcome.APPLIED || result.outcome == Outcome.REPLAYED) {
                result.currentSnapshot.journalFingerprint
            } else {
                null
            }
        } catch (ex: Exception) {
            Log.w(TAG, "Movimiento ${movement.id} (${movement.movementType}) rechazado para ${loan.id}: ${ex.message}")
            null
        }
    }
}
