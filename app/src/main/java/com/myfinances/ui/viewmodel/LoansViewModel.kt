package com.jcadenas.xpendz.ui.viewmodel

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcadenas.xpendz.application.loan.LoanApplicationService
import com.jcadenas.xpendz.application.loan.LoanCommandFactory
import com.jcadenas.xpendz.application.loan.LoanEditPlanner
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.data.repository.AccountRepository
import com.jcadenas.xpendz.data.repository.AuthRepository
import com.jcadenas.xpendz.data.repository.CategoryRepository
import com.jcadenas.xpendz.data.repository.LoanAdminStateRepository
import com.jcadenas.xpendz.data.repository.LoanMovementRepository
import com.jcadenas.xpendz.data.repository.LoanPaymentRepository
import com.jcadenas.xpendz.data.repository.LoanRepository
import com.jcadenas.xpendz.data.repository.TransactionRepository
import com.jcadenas.xpendz.data.repository.TransferRepository
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.projection.LoanPaymentProjection
import com.jcadenas.xpendz.domain.loan.projection.LoanProjectionQueryRepository
import com.jcadenas.xpendz.domain.loan.projection.LoanSummaryFilter
import com.jcadenas.xpendz.domain.loan.projection.LoanSummaryProjection
import com.jcadenas.xpendz.domain.loan.projection.SortBy
import com.jcadenas.xpendz.domain.loan.service.error.LoanAggregateErrorCode
import com.jcadenas.xpendz.domain.loan.aggregate.LoanCommandResult
import com.jcadenas.xpendz.domain.loan.commands.FieldChange
import com.jcadenas.xpendz.domain.loan.commands.LoanCommand
import com.jcadenas.xpendz.domain.loan.service.error.LoanAggregateException
import com.jcadenas.xpendz.domain.loan.snapshot.LoanSnapshot
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus
import com.jcadenas.xpendz.ui.loans.PaymentReversalGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LoansState(
    val isLoading: Boolean = false,
    val isSavingLoan: Boolean = false,
    val isSavingPayment: Boolean = false,
    val isSavingEdit: Boolean = false,
    val isCreatingLoan: Boolean = false,
    val createLoanError: String? = null,
    val createdLoanSnapshot: LoanSnapshot? = null,
    val selectedPaymentSummary: LoanSummaryProjection? = null,
    val paidLoanSnapshot: LoanSnapshot? = null,
    val paymentError: String? = null,
    val selectedPayment: LoanPaymentProjection? = null,
    val reversedLoanSnapshot: LoanSnapshot? = null,
    val reversePaymentError: String? = null,
    val isReversingPayment: Boolean = false,
    val reversingPaymentEventId: String? = null,
    val selectedTopUpSummary: LoanSummaryProjection? = null,
    val isSavingTopUp: Boolean = false,
    val toppedUpLoanSnapshot: LoanSnapshot? = null,
    val topUpError: String? = null,
    val isArchivingLoan: Boolean = false,
    val selectedTab: String = "LENT", // LENT or BORROWED
    val accounts: List<AccountEntity> = emptyList(),
    val accountBalancesCents: Map<String, Long> = emptyMap(),
    val lentLoans: List<LoanSummaryProjection> = emptyList(),
    val borrowedLoans: List<LoanSummaryProjection> = emptyList(),
    val loans: List<LoanSummaryProjection> = emptyList(),
    val totalLentRemainingCents: Long = 0L,
    val totalBorrowedRemainingCents: Long = 0L,
    val paymentProjections: Map<String, List<LoanPaymentProjection>> = emptyMap(),
    val paymentProjectionsError: Map<String, String?> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val transferRepository: TransferRepository,
    private val loanRepository: LoanRepository,
    private val loanAdminStateRepository: LoanAdminStateRepository,
    private val loanPaymentRepository: LoanPaymentRepository,
    private val loanMovementRepository: LoanMovementRepository,
    private val sharedPreferences: SharedPreferences,
    private val loanApplicationService: LoanApplicationService,
    private val loanCommandFactory: LoanCommandFactory,
    private val loanProjectionQueryRepository: LoanProjectionQueryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoansState())
    val state: StateFlow<LoansState> = _state.asStateFlow()

    private val uid: String?
        get() = authRepository.currentUser?.uid

    private var lastLoadedUid: String? = null
    private var accountObservationJob: Job? = null
    private var loanObservationJob: Job? = null
    private val observedLoanIds = mutableSetOf<String>()
    private val paymentReversalGuard = PaymentReversalGuard()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collectLatest { user ->
                val userUid = user?.uid
                if (userUid.isNullOrBlank()) {
                    lastLoadedUid = null
                    accountObservationJob?.cancel()
                    loanObservationJob?.cancel()
                    _state.value = LoansState()
                    return@collectLatest
                }
                if (userUid != lastLoadedUid) {
                    lastLoadedUid = userUid

                    // Migración automática de pagos históricos (se ejecuta solo una vez)
                    val migrationKey = "loan_payment_migration_v1_$userUid"
                    val migrationCompleted = sharedPreferences.getBoolean(migrationKey, false)
                    if (!migrationCompleted) {
                        try {
                            val result = migrateHistoricalPayments()
                            sharedPreferences.edit().putBoolean(migrationKey, true).apply()
                        } catch (e: Exception) {
                            // Si falla, no marcar como completada para reintentar en el siguiente inicio
                        }
                    }

                    startObservers(userUid)
                }
            }
        }
    }

    fun setTab(tab: String) {
        val current = _state.value
        val visibleLoans = when (tab) {
            "BORROWED" -> current.borrowedLoans
            else -> current.lentLoans
        }.sortedByDescending { it.pendingCents }
        _state.value = current.copy(selectedTab = tab, loans = visibleLoans)
    }

    private fun startObservers(userUid: String) {
        accountObservationJob?.cancel()
        loanObservationJob?.cancel()
        accountObservationJob = observeAccountData(userUid)
        loanObservationJob = observeLoanSummaries(userUid)
    }

    private fun observeAccountData(userUid: String) = viewModelScope.launch {
        combine(
            accountRepository.observeAccounts(userUid),
            transactionRepository.observeMaxUpdatedAtEpochSec(userUid),
            transferRepository.observeMaxUpdatedAtEpochSec(userUid)
        ) { accounts, _, _ -> accounts }
            .collectLatest { accountsUnsorted ->
                val balances = withContext(Dispatchers.IO) {
                    accountsUnsorted
                        .map { acc -> async { acc.id to accountRepository.computeBalance(userUid, acc.id) } }
                        .map { it.await() }
                        .toMap()
                }
                val accounts = accountsUnsorted
                    .sortedWith(compareByDescending<AccountEntity> { balances[it.id] ?: Long.MIN_VALUE }
                        .thenBy { it.name.lowercase() })
                _state.value = _state.value.copy(
                    accounts = accounts,
                    accountBalancesCents = balances,
                    isLoading = false
                )
            }
    }

    private fun observeLoanSummaries(userUid: String) = viewModelScope.launch {
        combine(
            loanProjectionQueryRepository.observeActiveSummaries(
                userUid,
                LoanSummaryFilter(loanType = LoanType.LENT, status = LoanStatus.OPEN)
            ),
            loanProjectionQueryRepository.observeActiveSummaries(
                userUid,
                LoanSummaryFilter(loanType = LoanType.BORROWED, status = LoanStatus.OPEN)
            )
        ) { lentLoans, borrowedLoans -> lentLoans to borrowedLoans }
            .collectLatest { (lentLoans, borrowedLoans) ->
                applyLoanSummaries(lentLoans, borrowedLoans)
            }
    }

    private fun applyLoanSummaries(
        lentLoans: List<LoanSummaryProjection>,
        borrowedLoans: List<LoanSummaryProjection>
    ) {
        val selectedTab = _state.value.selectedTab
        val loans = when (selectedTab) {
            "BORROWED" -> borrowedLoans
            else -> lentLoans
        }.sortedByDescending { it.pendingCents }

        _state.value = _state.value.copy(
            lentLoans = lentLoans,
            borrowedLoans = borrowedLoans,
            loans = loans,
            totalLentRemainingCents = lentLoans.sumOf { it.pendingCents },
            totalBorrowedRemainingCents = borrowedLoans.sumOf { it.pendingCents },
            isLoading = false
        )
    }

    suspend fun createLoan(
        type: String,
        accountId: String,
        counterparty: String,
        principalCents: Long,
        occurredAtEpochSec: Long,
        notes: String?
    ): String? {
        if (_state.value.isCreatingLoan) return "Operación en curso"

        val userUid = uid ?: return "Usuario no autenticado"
        val currency = _state.value.accounts.firstOrNull { it.id == accountId }?.currency ?: ""

        _state.value = _state.value.copy(
            isCreatingLoan = true,
            createLoanError = null,
            createdLoanSnapshot = null
        )
        var transactionId: String? = null
        return try {
            val loanType = LoanType.valueOf(type)
            val isLent = loanType == LoanType.LENT
            // Movimiento financiero único: misma convención que Desktop
            // (LOAN_LENT_OUT / LOAN_BORROWED_IN en la categoría "Préstamos").
            val (loanCategoryId, _) = categoryRepository.ensureSystemLoanCategories(userUid)
            val transaction = transactionRepository.create(
                userUid = userUid,
                accountId = accountId,
                categoryId = loanCategoryId,
                kind = if (isLent) "LOAN_LENT_OUT" else "LOAN_BORROWED_IN",
                amountCents = principalCents,
                occurredAtEpochSec = occurredAtEpochSec,
                note = if (isLent) {
                    "Préstamo otorgado a: $counterparty"
                } else {
                    "Dinero recibido de: $counterparty"
                }
            )
            transactionId = transaction.id

            val command = loanCommandFactory.createLoan(
                ownerId = userUid,
                loanType = loanType,
                counterpartyName = counterparty,
                currency = currency,
                defaultAccountId = accountId,
                initialPrincipalCents = principalCents,
                occurredAt = occurredAtEpochSec,
                transactionId = transactionId,
                notes = notes
            )
            val result = processCommand(command)
            transactionId = null
            publishLoanDoc(userUid, result.currentSnapshot, occurredAtEpochSec, result.event.recordedAt)
            _state.value = _state.value.copy(
                isCreatingLoan = false,
                createLoanError = null,
                createdLoanSnapshot = result.currentSnapshot
            )
            null
        } catch (e: LoanAggregateException) {
            deleteTransactionQuietly(userUid, transactionId)
            _state.value = _state.value.copy(
                isCreatingLoan = false,
                createLoanError = resolveErrorMessage(e),
                createdLoanSnapshot = null
            )
            _state.value.createLoanError
        } catch (e: Exception) {
            deleteTransactionQuietly(userUid, transactionId)
            _state.value = _state.value.copy(
                isCreatingLoan = false,
                createLoanError = e.message ?: "Error inesperado",
                createdLoanSnapshot = null
            )
            _state.value.createLoanError
        }
    }

    // El stack canónico de préstamos es síncrono; Room exige no ejecutarlo en Main.
    private suspend fun processCommand(command: LoanCommand): LoanCommandResult =
        withContext(Dispatchers.IO) { loanApplicationService.process(command) }

    private suspend fun <T> loanQuery(block: LoanProjectionQueryRepository.() -> T): T =
        withContext(Dispatchers.IO) { loanProjectionQueryRepository.block() }

    private suspend fun publishLoanDoc(
        userUid: String,
        snapshot: LoanSnapshot,
        occurredAtEpochSec: Long?,
        createdAtEpochSec: Long?,
        paymentId: String? = null,
        transactionId: String? = null,
        operationId: String? = null,
        eventId: String? = null
    ) {
        loanRepository.publishLoanToFirestore(
            userUid = userUid,
            loanId = snapshot.loanId,
            type = snapshot.loanType.name,
            counterpartyName = snapshot.counterpartyName,
            accountId = snapshot.defaultAccountId,
            principalCents = snapshot.principalCents,
            currency = snapshot.currency,
            status = snapshot.status.name,
            notes = snapshot.notes,
            occurredAtEpochSec = occurredAtEpochSec,
            createdAtEpochSec = createdAtEpochSec,
            paymentId = paymentId,
            transactionId = transactionId,
            operationId = operationId,
            eventId = eventId
        )
    }

    private fun resolveErrorMessage(error: LoanAggregateException): String {
        return when (error.code) {
            LoanAggregateErrorCode.INVALID_INITIAL_PRINCIPAL,
            LoanAggregateErrorCode.INVALID_COUNTERPARTY,
            LoanAggregateErrorCode.INVALID_CURRENCY,
            LoanAggregateErrorCode.EMPTY_METADATA_CHANGE -> "Verifica los datos ingresados"
            LoanAggregateErrorCode.LOAN_ALREADY_EXISTS -> "El préstamo ya existe"
            else -> error.code.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    fun preparePayment(loanId: String) {
        val userUid = uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                selectedPaymentSummary = null,
                paidLoanSnapshot = null,
                paymentError = null
            )
            val summary = loanQuery { getSummaryProjection(userUid, loanId) }
            _state.value = _state.value.copy(selectedPaymentSummary = summary)
        }
    }

    fun registerPayment(loanId: String, accountId: String, amountCents: Long, note: String?) {
        if (_state.value.isSavingPayment) return

        val userUid = uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isSavingPayment = true,
                paidLoanSnapshot = null,
                paymentError = null
            )
            val summary = _state.value.selectedPaymentSummary
                ?: loanQuery { getSummaryProjection(userUid, loanId) }
            if (summary == null) {
                _state.value = _state.value.copy(
                    isSavingPayment = false,
                    paymentError = "Préstamo no encontrado"
                )
                return@launch
            }

            var transactionId: String? = null
            try {
                val (_, repaymentCategoryId) = categoryRepository.ensureSystemLoanCategories(userUid)
                val kind = if (summary.loanType == LoanType.LENT) {
                    "LOAN_REPAYMENT_PRINCIPAL_IN"
                } else {
                    "LOAN_REPAYMENT_PRINCIPAL_OUT"
                }
                val transaction = transactionRepository.create(
                    userUid = userUid,
                    accountId = accountId,
                    categoryId = repaymentCategoryId,
                    kind = kind,
                    amountCents = amountCents,
                    occurredAtEpochSec = System.currentTimeMillis() / 1000,
                    note = note ?: if (summary.loanType == LoanType.LENT) {
                        "Pago recibido de: ${summary.counterparty}"
                    } else {
                        "Pago realizado a: ${summary.counterparty}"
                    }
                )
                transactionId = transaction.id
                val command = loanCommandFactory.registerPayment(
                    ownerId = userUid,
                    loanId = loanId,
                    expectedJournalFingerprint = summary.journalFingerprint,
                    amountCents = amountCents,
                    accountId = accountId,
                    transactionId = transaction.id,
                    note = note
                )
                logPaymentTrace(
                    label = "REGISTER_PAYMENT_START",
                    loanId = loanId,
                    transactionId = transaction.id,
                    operationId = command.envelope.operationId,
                    updatedAt = command.envelope.occurredAt,
                    updatedBy = command.envelope.actorId ?: userUid,
                    extras = "accountId=$accountId amountCents=$amountCents expectedFingerprint=${summary.journalFingerprint}"
                )
                val result = processCommand(command)
                logPaymentTrace(
                    label = "REGISTER_PAYMENT_APPLIED",
                    loanId = loanId,
                    paymentId = result.event.eventId,
                    transactionId = result.event.transactionId,
                    operationId = result.operationId,
                    eventId = result.event.eventId,
                    updatedAt = result.event.recordedAt,
                    updatedBy = result.event.actorId ?: result.event.originId ?: userUid,
                    extras = "occurredAt=${result.event.occurredAt} journalFingerprint=${result.currentSnapshot.journalFingerprint} pending=${result.currentSnapshot.pendingCents} totalPaid=${result.currentSnapshot.totalPaidCents}"
                )
                loanPaymentRepository.publishPaymentToFirestore(
                    userUid = userUid,
                    paymentId = result.event.eventId,
                    loanId = loanId,
                    accountId = accountId,
                    principalCents = amountCents,
                    occurredAtEpochSec = result.event.occurredAt,
                    linkedTransactionId = transactionId,
                    note = note,
                    createdAtEpochSec = result.event.recordedAt,
                    operationId = result.operationId,
                    eventId = result.event.eventId
                )
                publishLoanDoc(
                    userUid,
                    result.currentSnapshot,
                    null,
                    null,
                    paymentId = result.event.eventId,
                    transactionId = result.event.transactionId,
                    operationId = result.operationId,
                    eventId = result.event.eventId
                )
                _state.value = _state.value.copy(
                    isSavingPayment = false,
                    paidLoanSnapshot = result.currentSnapshot,
                    paymentError = null
                )
            } catch (e: LoanAggregateException) {
                deleteTransactionQuietly(userUid, transactionId)
                _state.value = _state.value.copy(
                    isSavingPayment = false,
                    paidLoanSnapshot = null,
                    paymentError = resolvePaymentErrorMessage(e)
                )
            } catch (e: Exception) {
                deleteTransactionQuietly(userUid, transactionId)
                _state.value = _state.value.copy(
                    isSavingPayment = false,
                    paidLoanSnapshot = null,
                    paymentError = e.message ?: "Error inesperado"
                )
            }
        }
    }

    fun prepareTopUp(loanId: String) {
        val userUid = uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                selectedTopUpSummary = null,
                toppedUpLoanSnapshot = null,
                topUpError = null
            )
            val summary = loanQuery { getSummaryProjection(userUid, loanId) }
            _state.value = _state.value.copy(selectedTopUpSummary = summary)
        }
    }

    fun topUpLoan(loanId: String, accountId: String, amountCents: Long, note: String?) {
        if (_state.value.isSavingTopUp) return

        val userUid = uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isSavingTopUp = true,
                toppedUpLoanSnapshot = null,
                topUpError = null
            )
            val summary = _state.value.selectedTopUpSummary
                ?: loanQuery { getSummaryProjection(userUid, loanId) }
            if (summary == null) {
                _state.value = _state.value.copy(
                    isSavingTopUp = false,
                    topUpError = "Préstamo no encontrado"
                )
                return@launch
            }

            var transactionId: String? = null
            try {
                val (loanCategoryId, _) = categoryRepository.ensureSystemLoanCategories(userUid)
                val isLent = summary.loanType == LoanType.LENT
                // Misma convención que Desktop: LOAN_LENT_TOPUP / LOAN_BORROWED_TOPUP
                // en la categoría "Préstamos" con la nota estándar de top-up.
                val transaction = transactionRepository.create(
                    userUid = userUid,
                    accountId = accountId,
                    categoryId = loanCategoryId,
                    kind = if (isLent) "LOAN_LENT_TOPUP" else "LOAN_BORROWED_TOPUP",
                    amountCents = amountCents,
                    occurredAtEpochSec = System.currentTimeMillis() / 1000,
                    note = note ?: if (isLent) {
                        "Aumento de préstamo otorgado a: ${summary.counterparty}"
                    } else {
                        "Aumento de deuda con: ${summary.counterparty}"
                    }
                )
                transactionId = transaction.id
                val command = loanCommandFactory.addPrincipal(
                    ownerId = userUid,
                    loanId = loanId,
                    expectedJournalFingerprint = summary.journalFingerprint,
                    amountCents = amountCents,
                    accountId = accountId,
                    transactionId = transaction.id,
                    note = note
                )
                val result = processCommand(command)
                publishLoanDoc(userUid, result.currentSnapshot, null, null)
                _state.value = _state.value.copy(
                    isSavingTopUp = false,
                    toppedUpLoanSnapshot = result.currentSnapshot,
                    topUpError = null
                )
            } catch (e: LoanAggregateException) {
                deleteTransactionQuietly(userUid, transactionId)
                _state.value = _state.value.copy(
                    isSavingTopUp = false,
                    toppedUpLoanSnapshot = null,
                    topUpError = resolveTopUpErrorMessage(e)
                )
            } catch (e: Exception) {
                deleteTransactionQuietly(userUid, transactionId)
                _state.value = _state.value.copy(
                    isSavingTopUp = false,
                    toppedUpLoanSnapshot = null,
                    topUpError = e.message ?: "Error inesperado"
                )
            }
        }
    }

    private fun resolveTopUpErrorMessage(error: LoanAggregateException): String {
        return when (error.code) {
            LoanAggregateErrorCode.INVALID_TOPUP_AMOUNT -> "El monto no es válido"
            LoanAggregateErrorCode.LOAN_NOT_FOUND -> "El préstamo no existe"
            LoanAggregateErrorCode.LOAN_ALREADY_EXPLICITLY_CLOSED -> "El préstamo está cerrado"
            LoanAggregateErrorCode.EXPECTED_FINGERPRINT_REQUIRED -> "El préstamo fue modificado; cierra y vuelve a abrir"
            LoanAggregateErrorCode.ARITHMETIC_OVERFLOW -> "El monto excede el límite permitido"
            LoanAggregateErrorCode.COMMAND_TYPE_MISMATCH,
            LoanAggregateErrorCode.INVALID_COMMAND -> "Comando no válido"
            else -> error.code.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private suspend fun deleteTransactionQuietly(userUid: String, transactionId: String?) {
        if (transactionId == null) return
        try {
            transactionRepository.deleteFailedLoanTransaction(userUid, transactionId)
        } catch (_: Exception) {
        }
    }

    private fun resolvePaymentErrorMessage(error: LoanAggregateException): String {
        return when (error.code) {
            LoanAggregateErrorCode.INVALID_PAYMENT_AMOUNT -> "El monto del pago no es válido"
            LoanAggregateErrorCode.LOAN_HAS_NO_PENDING_BALANCE -> "El préstamo no tiene saldo pendiente"
            LoanAggregateErrorCode.PAYMENT_EXCEEDS_PENDING -> "El monto excede el saldo pendiente"
            LoanAggregateErrorCode.LOAN_NOT_FOUND -> "El préstamo no existe"
            LoanAggregateErrorCode.EXPECTED_FINGERPRINT_REQUIRED -> "El préstamo fue modificado; cierra y vuelve a abrir"
            LoanAggregateErrorCode.COMMAND_TYPE_MISMATCH,
            LoanAggregateErrorCode.INVALID_COMMAND -> "Comando no válido"
            else -> error.code.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    fun selectPaymentForReverse(loanId: String, paymentEventId: String) {
        val userUid = uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                selectedPayment = null,
                reversedLoanSnapshot = null,
                reversePaymentError = null
            )
            val payment = loanQuery {
                getPaymentProjections(userUid, loanId)
                    .firstOrNull { it.sourceEventId == paymentEventId }
            }
            _state.value = _state.value.copy(selectedPayment = payment)
        }
    }

    fun reversePayment(loanId: String, paymentEventId: String, reason: String, note: String?) {
        if (_state.value.isReversingPayment) return
        if (!paymentReversalGuard.tryAcquire(paymentEventId)) return

        val userUid = uid
        if (userUid == null) {
            paymentReversalGuard.release(paymentEventId)
            return
        }
        // El flag se activa de forma síncrona (hilo principal) antes de lanzar
        // la coroutine: un doble tap en el mismo frame queda descartado.
        _state.value = _state.value.copy(
            isReversingPayment = true,
            reversingPaymentEventId = paymentEventId,
            reversedLoanSnapshot = null,
            reversePaymentError = null
        )
        viewModelScope.launch {
            try {
                val payment = _state.value.selectedPayment
                    ?.takeIf { it.sourceEventId == paymentEventId }
                    ?: loanQuery {
                        getPaymentProjections(userUid, loanId)
                            .firstOrNull { it.sourceEventId == paymentEventId }
                    }
                if (payment == null) {
                    _state.value = _state.value.copy(
                        isReversingPayment = false,
                        reversingPaymentEventId = null,
                        reversePaymentError = "Pago no encontrado"
                    )
                    return@launch
                }

                val summary = loanQuery { getSummaryProjection(userUid, loanId) }
                if (summary == null) {
                    _state.value = _state.value.copy(
                        isReversingPayment = false,
                        reversingPaymentEventId = null,
                        reversePaymentError = "Préstamo no encontrado"
                    )
                    return@launch
                }

                try {
                    val command = loanCommandFactory.reversePayment(
                        ownerId = userUid,
                        loanId = loanId,
                        paymentEventId = payment.sourceEventId,
                        expectedJournalFingerprint = summary.journalFingerprint,
                        reason = reason,
                        note = note
                    )
                    val result = processCommand(command)
                    deleteReversedPaymentArtifacts(userUid, payment)
                    publishLoanDoc(userUid, result.currentSnapshot, null, null)
                    // La carga del historial es puntual (no un flow): retiramos
                    // el pago revertido del estado para que desaparezca de la
                    // lista sin esperar una recarga.
                    val remainingPayments = (_state.value.paymentProjections[loanId] ?: emptyList())
                        .filter { it.sourceEventId != payment.sourceEventId }
                    _state.value = _state.value.copy(
                        isReversingPayment = false,
                        reversingPaymentEventId = null,
                        reversedLoanSnapshot = result.currentSnapshot,
                        reversePaymentError = null,
                        paymentProjections = _state.value.paymentProjections + (loanId to remainingPayments)
                    )
                } catch (e: LoanAggregateException) {
                    _state.value = _state.value.copy(
                        isReversingPayment = false,
                        reversingPaymentEventId = null,
                        reversedLoanSnapshot = null,
                        reversePaymentError = resolveReversePaymentErrorMessage(e)
                    )
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        isReversingPayment = false,
                        reversingPaymentEventId = null,
                        reversedLoanSnapshot = null,
                        reversePaymentError = e.message ?: "Error inesperado"
                    )
                }
            } finally {
                paymentReversalGuard.release(paymentEventId)
            }
        }
    }

    /**
     * Tras revertir un pago, elimina sus representaciones de transporte para que
     * ningún dispositivo pueda reconstruirlo por pull o por replay:
     * - fila/doc `loan_payments` (el doc remoto usa el eventId del dispositivo
     *   origen, que puede diferir del eventId del journal local; por eso la fila
     *   se localiza por transactionId/firma y además se borra el doc por
     *   sourceEventId para cubrir el dispositivo origen);
     * - movimiento PAYMENT_* vinculado;
     * - transacción LOAN_REPAYMENT_* vinculada (saldo de cuenta).
     * Las operaciones remotas usan el SDK (encola offline); los borrados son
     * idempotentes.
     */
    private suspend fun deleteReversedPaymentArtifacts(userUid: String, payment: LoanPaymentProjection) {
        try {
            loanPaymentRepository.deleteByPaymentSignature(
                userUid = userUid,
                loanId = payment.loanId,
                accountId = payment.accountId,
                principalCents = payment.amountCents,
                occurredAtEpochSec = payment.occurredAt,
                linkedTransactionId = payment.transactionId
            )
        } catch (_: Exception) {
        }
        loanPaymentRepository.deleteFromFirestore(userUid, payment.sourceEventId)
        try {
            loanMovementRepository.deletePaymentBySignature(
                userUid = userUid,
                loanId = payment.loanId,
                accountId = payment.accountId,
                amountCents = payment.amountCents,
                occurredAtEpochSec = payment.occurredAt,
                linkedTransactionId = payment.transactionId
            )
        } catch (_: Exception) {
        }
        deleteTransactionQuietly(userUid, payment.transactionId)
    }

    private fun resolveReversePaymentErrorMessage(error: LoanAggregateException): String {
        return when (error.code) {
            LoanAggregateErrorCode.TARGET_EVENT_NOT_FOUND -> "Pago no encontrado"
            LoanAggregateErrorCode.TARGET_NOT_PAYMENT -> "El evento seleccionado no es un pago"
            LoanAggregateErrorCode.CROSS_LOAN_TARGET -> "El pago pertenece a otro préstamo"
            LoanAggregateErrorCode.PAYMENT_ALREADY_REVERSED -> "El pago ya fue revertido"
            LoanAggregateErrorCode.REVERSAL_REASON_REQUIRED -> "El motivo es obligatorio"
            LoanAggregateErrorCode.EXPECTED_FINGERPRINT_REQUIRED -> "El préstamo fue modificado; cierra y vuelve a abrir"
            else -> error.code.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    fun archiveLoan(loanId: String) {
        if (_state.value.isArchivingLoan) return

        val userUid = uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isArchivingLoan = true, error = null)
            try {
                loanAdminStateRepository.archive(userUid, loanId)
                _state.value = _state.value.copy(isArchivingLoan = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isArchivingLoan = false, error = e.message ?: "Error inesperado")
            }
        }
    }

    fun updateLoan(
        loanId: String,
        counterpartyName: String? = null,
        accountId: String? = null,
        principalCents: Long? = null,
        notes: String? = null
    ) {
        // Protección contra doble clic
        if (_state.value.isSavingEdit) return

        val userUid = uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, isSavingEdit = true, error = null)
            try {
                val existing = (_state.value.lentLoans + _state.value.borrowedLoans)
                    .find { it.loanId == loanId }
                    ?: throw IllegalStateException("Préstamo no encontrado")

                var fingerprint = existing.journalFingerprint

                // Decidir los comandos antes de llamar al servicio: solo se
                // emiten los que representan un cambio efectivo.
                val decision = LoanEditPlanner.decide(
                    existing = existing,
                    counterpartyName = counterpartyName,
                    accountId = accountId,
                    principalCents = principalCents,
                    notes = notes
                )

                // Ajuste de principal si el monto cambió
                if (decision.principalChanged) {
                    val adjustAccountId = accountId ?: existing.defaultAccountId
                    var pendingTxId: String? = null
                    try {
                        if (adjustAccountId != null) {
                            val isLent = existing.loanType == LoanType.LENT
                            val isIncrease = decision.deltaCents > 0
                            // Misma convención que Desktop: corrección IN/OUT
                            // según tipo de préstamo y dirección del ajuste.
                            val kind = when {
                                isLent && isIncrease -> "LOAN_LENT_CORRECTION_OUT"
                                isLent -> "LOAN_LENT_CORRECTION_IN"
                                isIncrease -> "LOAN_BORROWED_CORRECTION_IN"
                                else -> "LOAN_BORROWED_CORRECTION_OUT"
                            }
                            val (loanCategoryId, _) =
                                categoryRepository.ensureSystemLoanCategories(userUid)
                            pendingTxId = transactionRepository.create(
                                userUid = userUid,
                                accountId = adjustAccountId,
                                categoryId = loanCategoryId,
                                kind = kind,
                                amountCents = kotlin.math.abs(decision.deltaCents),
                                occurredAtEpochSec = System.currentTimeMillis() / 1000,
                                note = if (isLent) {
                                    "Corrección de préstamo otorgado a: ${existing.counterparty}"
                                } else {
                                    "Corrección de deuda con: ${existing.counterparty}"
                                }
                            ).id
                        }
                        val adjustCommand = loanCommandFactory.adjustPrincipal(
                            ownerId = userUid,
                            loanId = loanId,
                            expectedJournalFingerprint = fingerprint,
                            deltaCents = decision.deltaCents,
                            reason = "Ajuste de monto",
                            accountId = adjustAccountId,
                            transactionId = pendingTxId,
                            note = null
                        )
                        val adjustResult = processCommand(adjustCommand)
                        pendingTxId = null
                        fingerprint = adjustResult.currentSnapshot.journalFingerprint
                        // Cada comando publica su propio estado: el ajuste no debe
                        // depender de que un comando posterior publique por él.
                        publishLoanDoc(userUid, adjustResult.currentSnapshot, null, null)
                    } catch (e: Exception) {
                        deleteTransactionQuietly(userUid, pendingTxId)
                        throw e
                    }
                }

                // Actualización de metadatos solo si hay cambios efectivos
                if (decision.metadataChanged) {
                    val metadataCommand = loanCommandFactory.updateLoanMetadata(
                        ownerId = userUid,
                        loanId = loanId,
                        expectedJournalFingerprint = fingerprint,
                        counterpartyName = FieldChange(decision.counterpartyChanged, decision.counterpartyName),
                        defaultAccountId = FieldChange(decision.accountChanged, decision.accountId),
                        notes = FieldChange(decision.notesChanged, decision.notes)
                    )
                    try {
                        val metadataResult = processCommand(metadataCommand)
                        publishLoanDoc(userUid, metadataResult.currentSnapshot, null, null)
                    } catch (e: LoanAggregateException) {
                        // NO_EFFECTIVE_CHANGE por concurrencia: el ajuste ya se
                        // aplicó y publicó; no es un error para el usuario.
                        if (e.code != LoanAggregateErrorCode.NO_EFFECTIVE_CHANGE) throw e
                    }
                }

                _state.value = _state.value.copy(
                    isLoading = false,
                    isSavingEdit = false,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, isSavingEdit = false, error = e.message)
            }
        }
    }

    suspend fun getLoanEditData(loanId: String): LoanEditData? {
        val summary = (_state.value.lentLoans + _state.value.borrowedLoans)
            .find { it.loanId == loanId } ?: return null
        return LoanEditData(
            originalPrincipalCents = summary.principalCents,
            paidCents = summary.totalPaidCents,
            pendingCents = summary.pendingCents,
            progressPercent = summary.progressPercent,
            currency = summary.currency,
            status = summary.status.name
        )
    }

    fun calculateNewPending(newPrincipalCents: Long, paidCents: Long): Long {
        return (newPrincipalCents - paidCents).coerceAtLeast(0L)
    }

    fun calculateNewProgress(newPrincipalCents: Long, paidCents: Long): Int {
        return if (newPrincipalCents > 0) {
            ((paidCents * 100) / newPrincipalCents).toInt().coerceIn(0, 100)
        } else {
            0
        }
    }

    data class LoanEditData(
        val originalPrincipalCents: Long,
        val paidCents: Long,
        val pendingCents: Long,
        val progressPercent: Int,
        val currency: String,
        val status: String
    )

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun loadPaymentProjections(loanId: String) {
        val userUid = uid ?: return
        viewModelScope.launch {
            try {
                val projections = loanQuery {
                    getPaymentProjections(userUid, loanId)
                }.sortedByDescending { it.occurredAt }
                _state.value = _state.value.copy(
                    paymentProjections = _state.value.paymentProjections + (loanId to projections),
                    paymentProjectionsError = _state.value.paymentProjectionsError + (loanId to null)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    paymentProjectionsError = _state.value.paymentProjectionsError + (loanId to e.message)
                )
            }
        }
    }

    suspend fun migrateHistoricalPayments(): LoanPaymentRepository.MigrationResult {
        val userUid = uid ?: return LoanPaymentRepository.MigrationResult(0, 0, 0, "Usuario no autenticado")
        return loanPaymentRepository.migrateHistoricalPayments(userUid)
    }

    private fun logPaymentTrace(
        label: String,
        loanId: String? = null,
        paymentId: String? = null,
        transactionId: String? = null,
        operationId: String? = null,
        eventId: String? = null,
        updatedAt: Long? = null,
        updatedBy: String? = null,
        extras: String? = null
    ) {
        val message = buildString {
            append(label)
            append(" loanId=").append(loanId ?: "-")
            append(" paymentId=").append(paymentId ?: "-")
            append(" transactionId=").append(transactionId ?: "-")
            append(" operationId=").append(operationId ?: "-")
            append(" eventId=").append(eventId ?: "-")
            append(" updatedAt=").append(updatedAt?.toString() ?: "-")
            append(" updatedBy=").append(updatedBy ?: "-")
            if (!extras.isNullOrBlank()) {
                append(' ')
                append(extras)
            }
        }
        Log.d("LoanPaymentTrace", message)
    }
}
