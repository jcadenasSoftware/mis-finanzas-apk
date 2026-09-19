package com.jcadenas.xpendz.infrastructure.loan.migration

import android.util.Log
import com.jcadenas.xpendz.application.loan.LoanApplicationService
import com.jcadenas.xpendz.data.local.dao.LoanDao
import com.jcadenas.xpendz.data.local.dao.LoanPaymentDao
import com.jcadenas.xpendz.data.local.dao.TransactionDao
import com.jcadenas.xpendz.data.local.entity.LoanPaymentEntity
import com.jcadenas.xpendz.domain.loan.aggregate.Outcome
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandEnvelope
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandType
import com.jcadenas.xpendz.domain.loan.commands.RegisterPaymentCommand
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.infrastructure.loan.room.CanonicalLoanDao
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Equivalente Android de HistoricalLoanPaymentReconciler (Desktop).
 * Reconcilia pagos legacy (loan_payments) hacia el journal canónico
 * usando exclusivamente [LoanApplicationService.process].
 * Idempotente: dedup por firma/transactionId y operationId determinista.
 */
class HistoricalLoanPaymentReconciler @Inject constructor(
    private val loanDao: LoanDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val transactionDao: TransactionDao,
    private val canonicalLoanDao: CanonicalLoanDao,
    private val loanApplicationService: LoanApplicationService
) {
    companion object {
        private const val TAG = "LoanPaymentReconciler"
    }

    data class ReconciliationReport(
        val loansProcessed: Int,
        val paymentsProcessed: Int,
        val paymentsReconciled: Int,
        val paymentsAlreadyExisting: Int,
        val paymentsOmitted: Int,
        val errors: List<String>
    )

    suspend fun reconcile(userUid: String): ReconciliationReport = withContext(Dispatchers.IO) {
        val payments = loanPaymentDao.getByUser(userUid)
            .sortedWith(
                compareBy<LoanPaymentEntity> { it.occurredAtEpochSec }
                    .thenBy { it.createdAtEpochSec }
                    .thenBy { it.linkedTransactionId ?: "" }
            )
        val grouped = payments.groupBy { it.loanId }
        // Mapa tx -> loanId a lo largo de todos los journals del usuario (para no reclamar
        // transacciones ya asociadas a otro préstamo canónico).
        val journalLoanByTransactionId = canonicalLoanDao.paymentEventsWithTransaction(userUid)
            .mapNotNull { event -> event.transactionId?.let { it to event.loanId } }
            .toMap()
        val ownerJournalTransactionIds = journalLoanByTransactionId.keys

        var loansProcessed = 0
        var paymentsProcessed = 0
        var paymentsReconciled = 0
        var paymentsAlreadyExisting = 0
        var paymentsOmitted = 0
        val errors = mutableListOf<String>()

        for ((loanId, loanPayments) in grouped) {
            loansProcessed++
            val snapshot = canonicalLoanDao.loadSnapshot(userUid, loanId)
            if (snapshot == null) {
                paymentsOmitted += loanPayments.size
                errors.add("Préstamo sin resumen canónico: $userUid/$loanId")
                continue
            }
            val loan = loanDao.getById(loanId)
            val journal = canonicalLoanDao.getJournal(userUid, loanId)
            val journalTransactionIds = journal.mapNotNull { it.transactionId }.toMutableSet()
            val journalSignatures = journal
                .filter { it.eventType == "PAYMENT" }
                .map { signature(it.accountId, it.amountCents ?: 0L, it.occurredAt) }
                .toMutableSet()
            val processedTransactionIds = mutableSetOf<String>()
            var fingerprint = snapshot.journalFingerprint

            for (payment in loanPayments) {
                paymentsProcessed++
                val paymentSignature = signature(payment.accountId, payment.principalCents, payment.occurredAtEpochSec)
                if (journalSignatures.contains(paymentSignature)) {
                    paymentsAlreadyExisting++
                    continue
                }
                val resolvedTransactionId = resolveTransactionId(
                    userUid, loanId, loan?.type, payment,
                    journalLoanByTransactionId, ownerJournalTransactionIds,
                    journalTransactionIds, processedTransactionIds
                )
                if (resolvedTransactionId == null) {
                    paymentsOmitted++
                    errors.add("Pago no recuperable: $userUid/$loanId/${payment.id}")
                    continue
                }
                if (journalTransactionIds.contains(resolvedTransactionId)) {
                    paymentsAlreadyExisting++
                    processedTransactionIds.add(resolvedTransactionId)
                    continue
                }
                try {
                    val envelope = LoanCommandEnvelope(
                        commandType = LoanCommandType.REGISTER_PAYMENT,
                        operationId = CanonicalLoanEventIds.forTransportPayment(
                            loanId,
                            payment.id,
                            payment.occurredAtEpochSec
                        ),
                        loanId = loanId,
                        ownerId = userUid,
                        expectedJournalFingerprint = fingerprint,
                        occurredAt = payment.occurredAtEpochSec,
                        actorId = userUid,
                        originId = userUid
                    )
                    val result = loanApplicationService.process(
                        RegisterPaymentCommand(
                            envelope = envelope,
                            amountCents = payment.principalCents,
                            accountId = payment.accountId,
                            transactionId = resolvedTransactionId,
                            note = payment.note
                        )
                    )
                    when (result.outcome) {
                        Outcome.APPLIED -> {
                            fingerprint = result.currentSnapshot.journalFingerprint
                            paymentsReconciled++
                            journalTransactionIds.add(resolvedTransactionId)
                            journalSignatures.add(paymentSignature)
                            processedTransactionIds.add(resolvedTransactionId)
                        }
                        Outcome.REPLAYED -> {
                            paymentsAlreadyExisting++
                            processedTransactionIds.add(resolvedTransactionId)
                            fingerprint = result.currentSnapshot.journalFingerprint
                        }
                    }
                } catch (ex: Exception) {
                    paymentsOmitted++
                    errors.add("Aggregate rechazó pago histórico: $userUid/$loanId/${payment.id} -> ${ex.message}")
                }
            }
        }

        ReconciliationReport(
            loansProcessed = loansProcessed,
            paymentsProcessed = paymentsProcessed,
            paymentsReconciled = paymentsReconciled,
            paymentsAlreadyExisting = paymentsAlreadyExisting,
            paymentsOmitted = paymentsOmitted,
            errors = errors.toList()
        ).also { report ->
            Log.d(
                TAG,
                "reconcile loans=${report.loansProcessed} payments=${report.paymentsProcessed} " +
                    "reconciled=${report.paymentsReconciled} existing=${report.paymentsAlreadyExisting} " +
                    "omitted=${report.paymentsOmitted} errors=${report.errors.size}"
            )
            report.errors.forEach { Log.w(TAG, it) }
        }
    }

    private suspend fun resolveTransactionId(
        userUid: String,
        loanId: String,
        loanType: String?,
        payment: LoanPaymentEntity,
        journalLoanByTransactionId: Map<String, String>,
        ownerJournalTransactionIds: Set<String>,
        journalTransactionIds: Set<String>,
        alreadyProcessedTransactionIds: Set<String>
    ): String? {
        val linked = payment.linkedTransactionId
        if (!linked.isNullOrBlank()) {
            val journalLoanId = journalLoanByTransactionId[linked]
            if (journalLoanId != null) {
                return if (journalLoanId == loanId) linked else null
            }
            if (alreadyProcessedTransactionIds.contains(linked)) {
                return linked
            }
            val tx = transactionDao.getById(linked)
            return if (tx != null && tx.kind == transactionKind(loanType)) linked else null
        }

        val expectedKind = transactionKind(loanType)
        val candidates = transactionDao.getFiltered(
            userUid = userUid,
            accountId = payment.accountId,
            categoryId = null,
            fromEpochSec = payment.occurredAtEpochSec,
            toEpochSec = payment.occurredAtEpochSec,
            limit = 100
        ).filter { it.kind == expectedKind && it.amountCents == payment.principalCents }
            .map { it.id }
            .filterNot(ownerJournalTransactionIds::contains)
            .filterNot(journalTransactionIds::contains)
            .filterNot(alreadyProcessedTransactionIds::contains)
        return if (candidates.size == 1) candidates.first() else null
    }

    private fun transactionKind(loanType: String?): String =
        if (loanType == LoanType.LENT.name) "LOAN_REPAYMENT_PRINCIPAL_IN" else "LOAN_REPAYMENT_PRINCIPAL_OUT"

    private fun signature(accountId: String?, amountCents: Long, occurredAt: Long): String =
        "$accountId|$amountCents|$occurredAt"
}
