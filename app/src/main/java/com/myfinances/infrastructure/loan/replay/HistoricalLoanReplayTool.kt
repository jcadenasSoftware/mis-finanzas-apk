package com.jcadenas.xpendz.infrastructure.loan.replay

import android.util.Log
import com.jcadenas.xpendz.application.loan.LoanApplicationService
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.data.local.dao.LoanDao
import com.jcadenas.xpendz.data.local.dao.LoanMovementDao
import com.jcadenas.xpendz.data.local.dao.LoanPaymentDao
import com.jcadenas.xpendz.data.local.dao.TransactionDao
import com.jcadenas.xpendz.data.local.entity.LoanEntity
import com.jcadenas.xpendz.data.local.entity.LoanMovementEntity
import com.jcadenas.xpendz.data.local.entity.LoanPaymentEntity
import com.jcadenas.xpendz.data.local.entity.TransactionEntity
import com.jcadenas.xpendz.domain.loan.aggregate.Outcome
import com.jcadenas.xpendz.domain.loan.commands.AddPrincipalCommand
import com.jcadenas.xpendz.domain.loan.commands.AdjustPrincipalCommand
import com.jcadenas.xpendz.domain.loan.commands.CloseLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.CreateLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.LoanCommand
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandEnvelope
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandType
import com.jcadenas.xpendz.domain.loan.commands.RegisterPaymentCommand
import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.projection.LoanPaymentProjection
import com.jcadenas.xpendz.domain.loan.projection.LoanProjectionQueryRepository
import com.jcadenas.xpendz.domain.loan.projection.LoanSummaryProjection
import com.jcadenas.xpendz.domain.loan.reducer.DefaultLoanReducer
import com.jcadenas.xpendz.domain.loan.service.error.LoanAggregateException
import com.jcadenas.xpendz.domain.loan.snapshot.LoanSnapshot
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus
import com.jcadenas.xpendz.infrastructure.loan.mapper.LoanInfrastructureMapper
import com.jcadenas.xpendz.infrastructure.loan.migration.CanonicalLoanEventIds
import com.jcadenas.xpendz.infrastructure.loan.projection.mapper.LoanProjectionMapper
import com.jcadenas.xpendz.infrastructure.loan.projection.room.DefaultLoanProjector
import com.jcadenas.xpendz.infrastructure.loan.projection.room.LoanProjectionDao
import com.jcadenas.xpendz.infrastructure.loan.room.CanonicalLoanDao
import com.jcadenas.xpendz.infrastructure.loan.room.RoomLoanRepositoryAdapter
import java.util.Comparator
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android mirror of the Desktop historical loan replay tool.
 *
 * It reconstructs a loan by replaying canonical commands through
 * [LoanApplicationService.process] only. When remote transport rows drift
 * from the local canonical snapshot/projection, the canonical state is
 * deleted, rebuilt, validated, and restored on failure.
 */
class HistoricalLoanReplayTool @Inject constructor(
    private val database: AppDatabase,
    private val loanDao: LoanDao,
    private val loanMovementDao: LoanMovementDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val transactionDao: TransactionDao,
    private val canonicalLoanDao: CanonicalLoanDao,
    private val loanProjectionDao: LoanProjectionDao,
    private val loanApplicationService: LoanApplicationService
) {
    companion object {
        private const val TAG = "LoanReplayTool"
        private val LEGACY_ORDER: Comparator<LegacyEvent> = compareBy<LegacyEvent> { it.occurredAt }
            .thenBy { it.createdAt }
            .thenBy { it.sourceKey }

        private val LOAN_REPAYMENT_KINDS = setOf(
            "LOAN_REPAYMENT_PRINCIPAL_IN",
            "LOAN_REPAYMENT_PRINCIPAL_OUT"
        )
    }

    private val repository = RoomLoanRepositoryAdapter(database, canonicalLoanDao)
    private val projector = DefaultLoanProjector(database, loanProjectionDao)
    private val projectionMapper = LoanProjectionMapper()

    data class ReplayResult(
        val ownerId: String,
        val loanId: String,
        val success: Boolean,
        val status: String,
        val eventsApplied: Int,
        val expectedPaymentCount: Int,
        val actualPaymentCount: Int,
        val summary: LoanSummaryProjection?,
        val snapshot: LoanSnapshot?,
        val errors: List<String>,
        val durationMs: Long
    ) {
        companion object {
            fun successResult(
                ownerId: String,
                loanId: String,
                eventsApplied: Int,
                expectedPaymentCount: Int,
                actualPaymentCount: Int,
                summary: LoanSummaryProjection?,
                snapshot: LoanSnapshot?,
                durationMs: Long
            ): ReplayResult = ReplayResult(ownerId, loanId, true, "SUCCESS", eventsApplied, expectedPaymentCount, actualPaymentCount, summary, snapshot, emptyList(), durationMs)

            fun failedResult(
                ownerId: String,
                loanId: String,
                status: String,
                errors: List<String>,
                durationMs: Long
            ): ReplayResult = ReplayResult(ownerId, loanId, false, status, 0, 0, 0, null, null, errors.toList(), durationMs)
        }
    }

    suspend fun replay(ownerId: String, loanId: String): ReplayResult {
        val startedAt = System.currentTimeMillis()

        val loan = readLoan(ownerId, loanId)
            ?: return ReplayResult.failedResult(ownerId, loanId, "LOAN_NOT_FOUND", listOf("No existe el préstamo legacy"), System.currentTimeMillis() - startedAt)

        val history = readHistory(loan)
        val planResult = buildPlan(loan, history)
        if (!planResult.ok) {
            return ReplayResult.failedResult(ownerId, loanId, planResult.code, planResult.errors, System.currentTimeMillis() - startedAt)
        }

        val plan = planResult.plan!!
        val backup = captureCanonicalState(ownerId, loanId)

        return try {
            deleteCanonicalState(ownerId, loanId)
            val snapshot = executeReplay(loan, plan)
                ?: throw IllegalStateException("El replay no produjo snapshot")

            validateSnapshotAgainstLegacy(loan, snapshot, plan)
            projector.rebuild(ownerId, loanId, repository, DefaultLoanReducer())
            validateProjections(ownerId, loanId, snapshot, plan)

            val summary = loanProjectionDao.getSummaryProjection(ownerId, loanId)?.let(projectionMapper::toDomain)
            ReplayResult.successResult(
                ownerId,
                loanId,
                plan.timeline.size,
                plan.expectedPaymentCount,
                summary?.paymentCount ?: 0,
                summary,
                snapshot,
                System.currentTimeMillis() - startedAt
            )
        } catch (ex: Exception) {
            try {
                restoreCanonicalState(ownerId, loanId, backup)
            } catch (restoreEx: Exception) {
                ex.addSuppressed(restoreEx)
            }
            ReplayResult.failedResult(ownerId, loanId, "REPLAY_ABORTED", listOf(message(ex)), System.currentTimeMillis() - startedAt)
        }
    }

    private fun executeReplay(loan: LegacyLoanRow, plan: ReplayPlan): LoanSnapshot? {
        var fingerprint: String? = null
        var snapshot: LoanSnapshot? = null
        for (event in plan.timeline) {
            val command = buildCommand(loan, event, fingerprint)
            val result = loanApplicationService.process(command)
            if (result.outcome != Outcome.APPLIED) {
                throw IllegalStateException("El evento ${event.type} no se aplicó")
            }
            fingerprint = result.currentSnapshot.journalFingerprint
            snapshot = result.currentSnapshot
        }
        return snapshot
    }

    private fun buildCommand(loan: LegacyLoanRow, event: LegacyEvent, fingerprint: String?): LoanCommand {
        val envelope = LoanCommandEnvelope(
            commandType = commandType(event.type),
            operationId = sharedOperationId(loan.loanId, event),
            loanId = loan.loanId,
            ownerId = loan.ownerId,
            expectedJournalFingerprint = fingerprint,
            occurredAt = event.occurredAt,
            actorId = loan.ownerId,
            originId = loan.ownerId
        )

        return when (event.type) {
            LegacyEventType.CREATION -> CreateLoanCommand(
                envelope = envelope,
                loanType = loan.loanType(),
                initialPrincipalCents = event.amountCents,
                counterpartyName = loan.counterpartyName,
                currency = loan.currency,
                defaultAccountId = event.accountId,
                transactionId = event.transactionId,
                notes = loan.notes
            )
            LegacyEventType.TOPUP -> AddPrincipalCommand(
                envelope = envelope,
                amountCents = event.amountCents,
                accountId = event.accountId,
                transactionId = event.transactionId,
                note = event.note
            )
            LegacyEventType.ADJUSTMENT -> AdjustPrincipalCommand(
                envelope = envelope,
                deltaCents = event.amountCents,
                reason = event.note?.takeIf { it.isNotBlank() } ?: "Corrección histórica",
                accountId = event.accountId,
                transactionId = event.transactionId,
                note = event.note
            )
            LegacyEventType.PAYMENT -> RegisterPaymentCommand(
                envelope = envelope,
                amountCents = event.amountCents,
                accountId = event.accountId,
                transactionId = event.transactionId,
                note = event.note
            )
            LegacyEventType.CLOSE -> CloseLoanCommand(
                envelope = envelope,
                reason = event.note?.takeIf { it.isNotBlank() } ?: "Cierre histórico",
                note = event.note
            )
        }
    }

    private fun commandType(type: LegacyEventType): LoanCommandType = when (type) {
        LegacyEventType.CREATION -> LoanCommandType.CREATE_LOAN
        LegacyEventType.TOPUP -> LoanCommandType.ADD_PRINCIPAL
        LegacyEventType.ADJUSTMENT -> LoanCommandType.ADJUST_PRINCIPAL
        LegacyEventType.PAYMENT -> LoanCommandType.REGISTER_PAYMENT
        LegacyEventType.CLOSE -> LoanCommandType.CLOSE_LOAN
    }

    /**
     * Pagos transportados (`pay:<docId>`) adoptan el docId de `loanPayments` cuando
     * es un UUID v4/v7 canónico: ese docId ya es el eventId del dispositivo origen
     * y preservarlo hace que la reversión elimine el documento correcto en
     * cualquier dispositivo. El resto de eventos usa el id determinístico
     * compartido (Android/Desktop producen el mismo operationId).
     */
    private fun sharedOperationId(loanId: String, event: LegacyEvent): String {
        if (event.type == LegacyEventType.PAYMENT && event.sourceKey.startsWith("pay:")) {
            return CanonicalLoanEventIds.forTransportPayment(
                loanId,
                event.sourceKey.removePrefix("pay:"),
                event.occurredAt
            )
        }
        return CanonicalLoanEventIds.deterministic(loanId, event.type.name, event.sourceKey, event.occurredAt)
    }

    private fun buildPlan(loan: LegacyLoanRow, history: LegacyHistory): PlanResult {
        val timeline = mutableListOf<LegacyEvent>()
        val errors = mutableListOf<String>()

        val creation = findCreation(loan, history)
        if (creation == null) {
            errors += "No existe un evento de creación recuperable"
            return PlanResult.failed("CREATION_NOT_FOUND", errors)
        }
        timeline += creation

        for (movement in history.movements) {
            if (movement.movementType.equals("CREATION", ignoreCase = true)) continue
            eventFromMovement(movement, loan)?.let(timeline::add)
        }

        val referencedMovementTxIds = history.movements.mapNotNull { it.linkedTransactionId }.toSet()
        val referencedPaymentTxIds = history.payments.mapNotNull { it.linkedTransactionId }.toSet()

        for (payment in history.payments) {
            if (payment.linkedTransactionId != null && referencedMovementTxIds.contains(payment.linkedTransactionId)) {
                continue
            }
            timeline += LegacyEvent.payment(payment)
        }

        for (tx in history.transactions) {
            if (referencedMovementTxIds.contains(tx.id) || referencedPaymentTxIds.contains(tx.id)) {
                continue
            }
            eventFromTransaction(tx, loan.loanType())?.let(timeline::add)
        }

        timeline.sortWith(LEGACY_ORDER)

        if (!hasCreationFirst(timeline)) {
            return PlanResult.failed("PAYMENT_BEFORE_CREATION", listOf("Un pago histórico ocurre antes de la creación"))
        }

        val initial = simulate(timeline)
        if (initial == null) {
            return validateTimeline(loan, timeline, errors)
        }

        val synthesized = synthesizeFinalStateIfNeeded(loan, timeline, initial) ?: run {
            return PlanResult.failed("REMOTE_STATE_UNRECONSTRUCTABLE", listOf("El snapshot remoto no puede reconstruirse con la historia local"))
        }

        return validateTimeline(loan, synthesized, errors)
    }

    private fun synthesizeFinalStateIfNeeded(
        loan: LegacyLoanRow,
        timeline: List<LegacyEvent>,
        state: TimelineState
    ): List<LegacyEvent>? {
        val out = timeline.toMutableList()
        var nextOccurredAt = maxOf(loan.updatedAtEpochSec, out.last().occurredAt + 1)

        if (state.principal != loan.principalCents) {
            out += LegacyEvent.syntheticAdjustment(loan, loan.principalCents - state.principal, nextOccurredAt)
            nextOccurredAt += 1
            val recalculated = simulate(out) ?: return null
            if (recalculated.principal != loan.principalCents) {
                return null
            }
        }

        val desiredStatus = loan.status?.uppercase(Locale.ROOT) ?: "OPEN"
        if (desiredStatus == "CLOSED") {
            if (state.pending > 0L) {
                return null
            }
            if (!state.closed) {
                out += LegacyEvent.syntheticClose(loan, nextOccurredAt)
                val recalculated = simulate(out) ?: return null
                if (!recalculated.closed) {
                    return null
                }
            }
        } else if (state.closed) {
            return null
        }

        return out
    }

    private fun simulate(timeline: List<LegacyEvent>): TimelineState? {
        var principal = 0L
        var totalPaid = 0L
        var paymentCount = 0
        var lastPaymentAt = 0L
        var creationSeen = false
        var closed = false

        for (event in timeline) {
            when (event.type) {
                LegacyEventType.CREATION -> {
                    if (creationSeen) return null
                    creationSeen = true
                    principal = event.amountCents
                }
                LegacyEventType.TOPUP, LegacyEventType.ADJUSTMENT -> principal = Math.addExact(principal, event.amountCents)
                LegacyEventType.PAYMENT -> {
                    totalPaid = Math.addExact(totalPaid, event.amountCents)
                    paymentCount++
                    lastPaymentAt = maxOf(lastPaymentAt, event.occurredAt)
                    if (principal - totalPaid < 0L) {
                        return null
                    }
                }
                LegacyEventType.CLOSE -> closed = true
            }
        }

        val pending = maxOf(0L, principal - totalPaid)
        val status = if (closed || pending == 0L) LoanStatus.CLOSED else LoanStatus.OPEN
        val progress = if (principal <= 0L) 0 else ((totalPaid * 100L) / principal).coerceAtMost(100L).toInt()
        return TimelineState(principal, totalPaid, pending, paymentCount, lastPaymentAt, closed, status, progress)
    }

    private fun findCreation(loan: LegacyLoanRow, history: LegacyHistory): LegacyEvent? {
        val explicitCreation = history.movements
            .filter { it.movementType.equals("CREATION", ignoreCase = true) }
            .minWithOrNull(compareBy<LegacyMovementRow> { it.occurredAtEpochSec }
                .thenBy { it.createdAtEpochSec }
                .thenBy { it.id })

        var inferredTimestamp = false
        var chosen = if (explicitCreation != null) {
            LegacyEvent.creation(explicitCreation, loan)
        } else {
            inferredTimestamp = true
            canonicalLoanDao.getJournal(loan.ownerId, loan.loanId)
                .firstOrNull { it.eventType == "CREATION" }
                ?.let { LegacyEvent.creationFromJournal(loan, it.occurredAt, it.amountCents ?: loan.principalCents) }
                ?: LegacyEvent.creation(loan)
        }

        if (inferredTimestamp) {
            val earliestPaymentAt = earliestPaymentAt(history)
            if (earliestPaymentAt != null && chosen.occurredAt > earliestPaymentAt) {
                chosen = chosen.withOccurredAt(earliestPaymentAt - 1L)
            }
        }
        if (chosen.transactionId == null) {
            val linked = firstCreationTransactionId(history, chosen.occurredAt)
            if (linked != null) {
                chosen = chosen.withTransactionId(linked)
            }
        }
        return chosen
    }

    private fun earliestPaymentAt(history: LegacyHistory): Long? = (
        history.movements
            .filter { it.movementType.uppercase(Locale.ROOT) in setOf("PAYMENT", "PAYMENT_IN", "PAYMENT_OUT") }
            .map { it.occurredAtEpochSec } +
            history.payments.map { it.occurredAtEpochSec }
        ).minOrNull()

    private fun firstCreationTransactionId(history: LegacyHistory, occurredAt: Long): String? = history.transactions
        .filter {
            val kind = it.kind.uppercase(Locale.ROOT)
            (kind == "LOAN_LENT_OUT" || kind == "LOAN_BORROWED_IN") && it.occurredAtEpochSec == occurredAt
        }
        .sortedWith(compareBy<TransactionEntity> { it.createdAtEpochSec }
            .thenBy { it.id })
        .firstOrNull()
        ?.id

    private fun eventFromMovement(movement: LegacyMovementRow, loan: LegacyLoanRow): LegacyEvent? =
        when (movement.movementType.uppercase(Locale.ROOT)) {
            "TOPUP" -> LegacyEvent.topup(movement, loan)
            "PAYMENT", "PAYMENT_IN", "PAYMENT_OUT" -> LegacyEvent.payment(movement, loan)
            "CLOSE" -> LegacyEvent.close(movement)
            else -> null
        }

    private fun eventFromTransaction(tx: TransactionEntity, loanType: LoanType): LegacyEvent? {
        val kind = tx.kind.uppercase(Locale.ROOT)
        return when {
            kind == "LOAN_LENT_TOPUP" || kind == "LOAN_BORROWED_TOPUP" -> LegacyEvent.topup(tx, loanType)
            kind.contains("CORRECTION") -> LegacyEvent.adjustment(tx, loanType)
            kind == "LOAN_REPAYMENT_PRINCIPAL_IN" || kind == "LOAN_REPAYMENT_PRINCIPAL_OUT" -> LegacyEvent.payment(tx, loanType)
            else -> null
        }
    }

    private fun hasCreationFirst(timeline: List<LegacyEvent>): Boolean {
        var firstCreation = Long.MAX_VALUE
        var firstPayment = Long.MAX_VALUE
        for (event in timeline) {
            if (event.type == LegacyEventType.CREATION) {
                firstCreation = minOf(firstCreation, event.occurredAt)
            }
            if (event.type == LegacyEventType.PAYMENT) {
                firstPayment = minOf(firstPayment, event.occurredAt)
            }
        }
        return firstCreation != Long.MAX_VALUE && (firstPayment == Long.MAX_VALUE || firstPayment >= firstCreation)
    }

    private fun validateTimeline(loan: LegacyLoanRow, timeline: List<LegacyEvent>, errors: MutableList<String>): PlanResult {
        var principal = 0L
        var totalPaid = 0L
        var paymentCount = 0
        var lastPaymentAt = 0L
        var creationSeen = false

        for (event in timeline) {
            when (event.type) {
                LegacyEventType.CREATION -> {
                    if (creationSeen) {
                        return PlanResult.failed("MULTIPLE_CREATIONS", listOf("Se encontraron múltiples creaciones"))
                    }
                    creationSeen = true
                    principal = event.amountCents
                }
                LegacyEventType.TOPUP, LegacyEventType.ADJUSTMENT -> principal = Math.addExact(principal, event.amountCents)
                LegacyEventType.PAYMENT -> {
                    totalPaid = Math.addExact(totalPaid, event.amountCents)
                    paymentCount++
                    lastPaymentAt = maxOf(lastPaymentAt, event.occurredAt)
                    if (principal - totalPaid < 0L) {
                        return PlanResult.failed("PAYMENT_EXCEEDS_PENDING", listOf("Pago histórico excede el saldo pendiente cronológico"))
                    }
                }
                LegacyEventType.CLOSE -> Unit
            }
        }

        if (principal != loan.principalCents) {
            return PlanResult.failed(
                "PRINCIPAL_MISMATCH",
                listOf("Principal derivado $principal no coincide con legacy ${loan.principalCents}")
            )
        }

        val pending = maxOf(0L, principal - totalPaid)
        val expectedStatus = if (pending == 0L) LoanStatus.CLOSED else LoanStatus.OPEN
        val expectedProgress = if (principal <= 0L) 0 else ((totalPaid * 100L) / principal).coerceAtMost(100L).toInt()

        return PlanResult.valid(
            ReplayPlan(
                timeline = timeline,
                expectedPrincipal = principal,
                expectedTotalPaid = totalPaid,
                expectedPending = pending,
                expectedPaymentCount = paymentCount,
                lastPaymentAt = lastPaymentAt,
                expectedStatus = expectedStatus,
                expectedProgress = expectedProgress
            )
        )
    }

    private fun validateSnapshotAgainstLegacy(loan: LegacyLoanRow, snapshot: LoanSnapshot, plan: ReplayPlan) {
        check(snapshot.principalCents == loan.principalCents) { "Principal ${snapshot.principalCents} no coincide con legacy ${loan.principalCents}" }
        check(snapshot.totalPaidCents == plan.expectedTotalPaid) { "totalPaid no coincide" }
        check(snapshot.pendingCents == plan.expectedPending) { "pending no coincide" }
        check(snapshot.status == plan.expectedStatus) { "status no coincide" }
        check(snapshot.loanType == loan.loanType()) { "loanType no coincide" }
        check(normalize(snapshot.counterpartyName) == normalize(loan.counterpartyName)) { "counterparty no coincide" }
        check(normalize(snapshot.currency) == normalize(loan.currency)) { "currency no coincide" }
        check(normalize(snapshot.defaultAccountId) == normalize(loan.accountId)) { "defaultAccount no coincide" }
        check(normalize(snapshot.notes) == normalize(loan.notes)) { "notes no coincide" }
        check(snapshot.journalEventCount == plan.timeline.size) { "journalEventCount no coincide" }
    }

    private fun validateProjections(ownerId: String, loanId: String, snapshot: LoanSnapshot, plan: ReplayPlan) {
        val summary = loanProjectionDao.getSummaryProjection(ownerId, loanId)
            ?: throw IllegalStateException("Summary projection faltante")

        check(summary.principalCents == snapshot.principalCents) { "principal entre snapshot y summary no coincide" }
        check(summary.totalPaidCents == snapshot.totalPaidCents) { "totalPaid entre snapshot y summary no coincide" }
        check(summary.pendingCents == snapshot.pendingCents) { "pending entre snapshot y summary no coincide" }
        check(summary.status == snapshot.status.name) { "status entre snapshot y summary no coincide" }
        check(summary.paymentCount == plan.expectedPaymentCount) { "paymentCount no coincide" }
        check(normalize(summary.counterparty) == normalize(snapshot.counterpartyName)) { "counterparty entre snapshot y summary no coincide" }
        check(normalize(summary.currency) == normalize(snapshot.currency)) { "currency entre snapshot y summary no coincide" }
        check(normalize(summary.defaultAccountId) == normalize(snapshot.defaultAccountId)) { "defaultAccount entre snapshot y summary no coincide" }
        check(normalize(summary.notes) == normalize(snapshot.notes)) { "notes entre snapshot y summary no coincide" }
        check(summary.journalFingerprint == snapshot.journalFingerprint) { "journalFingerprint no coincide" }

        val payments = loanProjectionDao.getPaymentProjections(ownerId, loanId)
        val total = payments.sumOf { it.amountCents }
        check(payments.size == summary.paymentCount) { "paymentCount no coincide con proyecciones" }
        check(total == summary.totalPaidCents) { "suma de proyecciones no coincide con totalPaid" }
    }

    private fun captureCanonicalState(ownerId: String, loanId: String): CanonicalBackup =
        CanonicalBackup(
            journal = repository.getJournal(ownerId, loanId),
            snapshot = repository.loadSnapshot(ownerId, loanId)
        )

    private fun deleteCanonicalState(ownerId: String, loanId: String) {
        database.runInTransaction {
            loanProjectionDao.deletePaymentProjections(ownerId, loanId)
            loanProjectionDao.deleteSummaryProjection(ownerId, loanId)
            canonicalLoanDao.deleteSnapshot(ownerId, loanId)
            canonicalLoanDao.deleteJournal(ownerId, loanId)
        }
    }

    private fun restoreCanonicalState(ownerId: String, loanId: String, backup: CanonicalBackup) {
        deleteCanonicalState(ownerId, loanId)
        database.runInTransaction {
            for (event in backup.journal) {
                repository.appendEvent(event)
            }
            backup.snapshot?.let(repository::replaceSnapshot)
        }
        projector.rebuild(ownerId, loanId, repository, DefaultLoanReducer())
    }

    private suspend fun readLoan(ownerId: String, loanId: String): LegacyLoanRow? {
        val loan = loanDao.getById(loanId) ?: return null
        return if (loan.userUid == ownerId) loan.toLegacy() else null
    }

    private suspend fun readHistory(loan: LegacyLoanRow): LegacyHistory {
        val movements = loanMovementDao.getByLoan(loan.ownerId, loan.loanId)
        val payments = loanPaymentDao.getByUser(loan.ownerId)
            .filter { it.loanId == loan.loanId }
            .sortedWith(compareBy<LoanPaymentEntity> { it.occurredAtEpochSec }
                .thenBy { it.createdAtEpochSec }
                .thenBy { it.linkedTransactionId ?: it.id })
        val transactions = readTransactions(loan, movements, payments)
        return LegacyHistory(loan, movements.map { it.toLegacy() }, payments.map { it.toLegacy() }, transactions)
    }

    /**
     * Las transacciones LOAN_* no llevan loan_id: solo se admiten las que el
     * préstamo referencia explícitamente desde sus movements/payments o desde
     * el journal canónico previo, más una transacción de creación no reclamada
     * que coincida en tipo, cuenta y fecha. Cualquier otra transacción de la
     * misma cuenta pertenece a otros préstamos y no puede atribuirse.
     */
    private suspend fun readTransactions(
        loan: LegacyLoanRow,
        movements: List<LoanMovementEntity>,
        payments: List<LoanPaymentEntity>
    ): List<TransactionEntity> {
        val referencedTxIds = (movements.mapNotNull { it.linkedTransactionId } +
            payments.mapNotNull { it.linkedTransactionId }).toMutableSet()
        // Las transacciones que el journal canónico previo ya vinculó a este
        // préstamo son referencias determinísticas: se conservan aunque el
        // principal legacy haya sido editado después (el monto ya no coincide).
        var priorCreationOccurredAt: Long? = null
        for (event in canonicalLoanDao.getJournal(loan.ownerId, loan.loanId)) {
            event.transactionId?.let { referencedTxIds.add(it) }
            if (event.eventType == "CREATION" && priorCreationOccurredAt == null) {
                priorCreationOccurredAt = event.occurredAt
            }
        }
        val claimedByOtherLoans = canonicalLoanDao
            .journalTransactionIdsOfOtherLoans(loan.ownerId, loan.loanId)
            .toSet()
        val candidates = transactionDao.getByUser(loan.ownerId)
            .filter {
                it.kind.uppercase(Locale.ROOT).startsWith("LOAN_")
            }
        val creationKind = if (loan.loanType() == LoanType.LENT) "LOAN_LENT_OUT" else "LOAN_BORROWED_IN"
        val creationCandidates = candidates.filter {
            it.id !in referencedTxIds &&
                it.id !in claimedByOtherLoans &&
                it.kind.uppercase(Locale.ROOT) == creationKind
        }
        // El monto no se exige: un ajuste remoto queda plegado en el principal
        // del evento de creación mientras la transacción conserva el original.
        // Primero el occurredAt exacto del CREATION previo; si no hay journal
        // previo, el más cercano al occurredAt del préstamo.
        val creationTx = (
            priorCreationOccurredAt?.let { anchor ->
                creationCandidates
                    .filter { it.occurredAtEpochSec == anchor }
                    .minWithOrNull(compareBy<TransactionEntity> { it.createdAtEpochSec }.thenBy { it.id })
            } ?: creationCandidates.minWithOrNull(
                compareBy<TransactionEntity> { Math.abs(it.occurredAtEpochSec - loan.occurredAtEpochSec) }
                    .thenBy { it.createdAtEpochSec }
                    .thenBy { it.id }
            )
        )
        return (candidates.filter { it.id in referencedTxIds } + listOfNotNull(creationTx))
            .distinctBy { it.id }
            .sortedWith(compareBy<TransactionEntity> { it.occurredAtEpochSec }
                .thenBy { it.createdAtEpochSec }
                .thenBy { it.id })
    }

    private fun LoanEntity.toLegacy(): LegacyLoanRow = LegacyLoanRow(
        loanId = id,
        ownerId = userUid,
        type = type,
        counterpartyName = counterpartyName,
        principalCents = principalCents,
        currency = currency,
        status = status,
        notes = notes,
        occurredAtEpochSec = createdAtEpochSec,
        accountId = accountId,
        createdAtEpochSec = createdAtEpochSec,
        updatedAtEpochSec = updatedAtEpochSec,
        updatedBy = updatedBy
    )

    private fun LoanMovementEntity.toLegacy(): LegacyMovementRow = LegacyMovementRow(
        id = id,
        loanId = loanId,
        userUid = userUid,
        movementType = movementType,
        amountCents = amountCents,
        accountId = accountId,
        linkedTransactionId = linkedTransactionId,
        note = note,
        occurredAtEpochSec = occurredAtEpochSec,
        createdAtEpochSec = createdAtEpochSec,
        updatedAtEpochSec = updatedAtEpochSec,
        updatedBy = updatedBy
    )

    private fun LoanPaymentEntity.toLegacy(): LegacyPaymentRow = LegacyPaymentRow(
        id = id,
        loanId = loanId,
        userUid = userUid,
        accountId = accountId,
        principalCents = principalCents,
        occurredAtEpochSec = occurredAtEpochSec,
        linkedTransactionId = linkedTransactionId,
        note = note,
        createdAtEpochSec = createdAtEpochSec,
        updatedAtEpochSec = updatedAtEpochSec,
        updatedBy = updatedBy
    )

    private fun LegacyLoanRow.loanType(): LoanType = LoanType.valueOf(type.uppercase(Locale.ROOT))

    private fun normalize(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }

    private fun message(ex: Exception): String = ex.message?.takeIf { it.isNotBlank() } ?: ex::class.java.simpleName

    private data class LegacyLoanRow(
        val loanId: String,
        val ownerId: String,
        val type: String,
        val counterpartyName: String,
        val principalCents: Long,
        val currency: String,
        val status: String?,
        val notes: String?,
        val occurredAtEpochSec: Long,
        val accountId: String?,
        val createdAtEpochSec: Long,
        val updatedAtEpochSec: Long,
        val updatedBy: String?
    )

    private data class LegacyMovementRow(
        val id: String,
        val loanId: String,
        val userUid: String,
        val movementType: String,
        val amountCents: Long,
        val accountId: String?,
        val linkedTransactionId: String?,
        val note: String?,
        val occurredAtEpochSec: Long,
        val createdAtEpochSec: Long,
        val updatedAtEpochSec: Long,
        val updatedBy: String?
    )

    private data class LegacyPaymentRow(
        val id: String,
        val loanId: String,
        val userUid: String,
        val accountId: String,
        val principalCents: Long,
        val occurredAtEpochSec: Long,
        val linkedTransactionId: String?,
        val note: String?,
        val createdAtEpochSec: Long,
        val updatedAtEpochSec: Long,
        val updatedBy: String?
    )

    private data class LegacyHistory(
        val loan: LegacyLoanRow,
        val movements: List<LegacyMovementRow>,
        val payments: List<LegacyPaymentRow>,
        val transactions: List<TransactionEntity>
    )

    private enum class LegacyEventType { CREATION, TOPUP, ADJUSTMENT, PAYMENT, CLOSE }

    private data class LegacyEvent(
        val type: LegacyEventType,
        val sourceKey: String,
        val loanId: String,
        val ownerId: String,
        val occurredAt: Long,
        val createdAt: Long,
        val accountId: String?,
        val transactionId: String?,
        val amountCents: Long,
        val note: String?
    ) {
        fun withTransactionId(txId: String): LegacyEvent = copy(transactionId = txId)
        fun withOccurredAt(value: Long): LegacyEvent = copy(occurredAt = value, createdAt = value)

        companion object {
            fun syntheticAdjustment(loan: LegacyLoanRow, deltaCents: Long, occurredAt: Long): LegacyEvent = LegacyEvent(
                type = LegacyEventType.ADJUSTMENT,
                sourceKey = "synth:adjust:${loan.loanId}",
                loanId = loan.loanId,
                ownerId = loan.ownerId,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                accountId = loan.accountId,
                transactionId = null,
                amountCents = deltaCents,
                note = "Ajuste incremental remoto"
            )

            fun syntheticClose(loan: LegacyLoanRow, occurredAt: Long): LegacyEvent = LegacyEvent(
                type = LegacyEventType.CLOSE,
                sourceKey = "synth:close:${loan.loanId}",
                loanId = loan.loanId,
                ownerId = loan.ownerId,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                accountId = loan.accountId,
                transactionId = null,
                amountCents = 0L,
                note = "Cierre incremental remoto"
            )

            fun creation(loan: LegacyLoanRow): LegacyEvent = LegacyEvent(
                type = LegacyEventType.CREATION,
                sourceKey = "loan:${loan.loanId}",
                loanId = loan.loanId,
                ownerId = loan.ownerId,
                occurredAt = loan.occurredAtEpochSec,
                createdAt = loan.createdAtEpochSec,
                accountId = loan.accountId,
                transactionId = null,
                amountCents = loan.principalCents,
                note = loan.notes
            )

            fun creation(movement: LegacyMovementRow, loan: LegacyLoanRow): LegacyEvent = LegacyEvent(
                type = LegacyEventType.CREATION,
                sourceKey = "mov:${movement.id}",
                loanId = loan.loanId,
                ownerId = loan.ownerId,
                occurredAt = movement.occurredAtEpochSec,
                createdAt = movement.createdAtEpochSec,
                accountId = movement.accountId ?: loan.accountId,
                transactionId = movement.linkedTransactionId,
                amountCents = movement.amountCents,
                note = movement.note
            )

            fun creation(tx: TransactionEntity, loan: LegacyLoanRow): LegacyEvent = LegacyEvent(
                type = LegacyEventType.CREATION,
                sourceKey = "tx:${tx.id}",
                loanId = loan.loanId,
                ownerId = loan.ownerId,
                occurredAt = tx.occurredAtEpochSec,
                createdAt = tx.createdAtEpochSec,
                accountId = tx.accountId,
                transactionId = tx.id,
                amountCents = tx.amountCents,
                note = tx.note
            )

            fun creationFromJournal(loan: LegacyLoanRow, occurredAt: Long, amountCents: Long): LegacyEvent = LegacyEvent(
                type = LegacyEventType.CREATION,
                sourceKey = "journal:${loan.loanId}:creation",
                loanId = loan.loanId,
                ownerId = loan.ownerId,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                accountId = loan.accountId,
                transactionId = null,
                amountCents = amountCents,
                note = loan.notes
            )

            fun topup(movement: LegacyMovementRow, loan: LegacyLoanRow): LegacyEvent = LegacyEvent(
                type = LegacyEventType.TOPUP,
                sourceKey = "mov:${movement.id}",
                loanId = loan.loanId,
                ownerId = loan.ownerId,
                occurredAt = movement.occurredAtEpochSec,
                createdAt = movement.createdAtEpochSec,
                accountId = movement.accountId ?: loan.accountId,
                transactionId = movement.linkedTransactionId,
                amountCents = movement.amountCents,
                note = movement.note
            )

            fun topup(tx: TransactionEntity, loanType: LoanType): LegacyEvent = LegacyEvent(
                type = LegacyEventType.TOPUP,
                sourceKey = "tx:${tx.id}",
                loanId = "",
                ownerId = tx.userUid,
                occurredAt = tx.occurredAtEpochSec,
                createdAt = tx.createdAtEpochSec,
                accountId = tx.accountId,
                transactionId = tx.id,
                amountCents = tx.amountCents,
                note = tx.note
            )

            fun adjustment(tx: TransactionEntity, loanType: LoanType): LegacyEvent {
                val delta = if (loanType == LoanType.LENT) {
                    if (tx.kind.uppercase(Locale.ROOT).endsWith("OUT")) tx.amountCents else -tx.amountCents
                } else {
                    if (tx.kind.uppercase(Locale.ROOT).endsWith("IN")) tx.amountCents else -tx.amountCents
                }
                return LegacyEvent(
                    type = LegacyEventType.ADJUSTMENT,
                    sourceKey = "tx:${tx.id}",
                    loanId = "",
                    ownerId = tx.userUid,
                    occurredAt = tx.occurredAtEpochSec,
                    createdAt = tx.createdAtEpochSec,
                    accountId = tx.accountId,
                    transactionId = tx.id,
                    amountCents = delta,
                    note = tx.note
                )
            }

            fun payment(movement: LegacyMovementRow, loan: LegacyLoanRow): LegacyEvent = LegacyEvent(
                type = LegacyEventType.PAYMENT,
                sourceKey = "mov:${movement.id}",
                loanId = loan.loanId,
                ownerId = loan.ownerId,
                occurredAt = movement.occurredAtEpochSec,
                createdAt = movement.createdAtEpochSec,
                accountId = movement.accountId ?: loan.accountId,
                transactionId = movement.linkedTransactionId,
                amountCents = movement.amountCents,
                note = movement.note
            )

            fun payment(payment: LegacyPaymentRow): LegacyEvent = LegacyEvent(
                type = LegacyEventType.PAYMENT,
                sourceKey = "pay:${payment.id}",
                loanId = payment.loanId,
                ownerId = payment.userUid,
                occurredAt = payment.occurredAtEpochSec,
                createdAt = payment.createdAtEpochSec,
                accountId = payment.accountId,
                transactionId = payment.linkedTransactionId,
                amountCents = payment.principalCents,
                note = payment.note
            )

            fun payment(tx: TransactionEntity, loanType: LoanType): LegacyEvent = LegacyEvent(
                type = LegacyEventType.PAYMENT,
                sourceKey = "tx:${tx.id}",
                loanId = "",
                ownerId = tx.userUid,
                occurredAt = tx.occurredAtEpochSec,
                createdAt = tx.createdAtEpochSec,
                accountId = tx.accountId,
                transactionId = tx.id,
                amountCents = tx.amountCents,
                note = tx.note
            )

            fun close(movement: LegacyMovementRow): LegacyEvent = LegacyEvent(
                type = LegacyEventType.CLOSE,
                sourceKey = "mov:${movement.id}",
                loanId = movement.loanId,
                ownerId = movement.userUid,
                occurredAt = movement.occurredAtEpochSec,
                createdAt = movement.createdAtEpochSec,
                accountId = movement.accountId,
                transactionId = movement.linkedTransactionId,
                amountCents = 0L,
                note = movement.note
            )
        }
    }

    private data class ReplayPlan(
        val timeline: List<LegacyEvent>,
        val expectedPrincipal: Long,
        val expectedTotalPaid: Long,
        val expectedPending: Long,
        val expectedPaymentCount: Int,
        val lastPaymentAt: Long,
        val expectedStatus: LoanStatus,
        val expectedProgress: Int
    )

    private data class TimelineState(
        val principal: Long,
        val totalPaid: Long,
        val pending: Long,
        val paymentCount: Int,
        val lastPaymentAt: Long,
        val closed: Boolean,
        val status: LoanStatus,
        val progress: Int
    )

    private data class PlanResult(
        val ok: Boolean,
        val code: String,
        val errors: List<String>,
        val plan: ReplayPlan?
    ) {
        companion object {
            fun valid(plan: ReplayPlan): PlanResult = PlanResult(true, "", emptyList(), plan)
            fun failed(code: String, errors: List<String>): PlanResult = PlanResult(false, code, errors.toList(), null)
        }
    }

    private data class CanonicalBackup(
        val journal: List<LoanMovement>,
        val snapshot: LoanSnapshot?
    )
}
