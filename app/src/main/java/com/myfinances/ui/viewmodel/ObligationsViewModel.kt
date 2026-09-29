package com.jcadenas.xpendz.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcadenas.xpendz.application.obligation.ObligationResolvedStatus
import com.jcadenas.xpendz.application.obligation.ObligationService
import com.jcadenas.xpendz.application.obligation.ResolvedObligationState
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.data.local.entity.CategoryEntity
import com.jcadenas.xpendz.data.local.entity.ObligationEntity
import com.jcadenas.xpendz.data.local.entity.ObligationSettlementEntity
import com.jcadenas.xpendz.data.local.entity.TransactionEntity
import com.jcadenas.xpendz.data.repository.AccountRepository
import com.jcadenas.xpendz.data.repository.AuthRepository
import com.jcadenas.xpendz.data.repository.CategoryRepository
import com.jcadenas.xpendz.data.repository.ObligationRepository
import com.jcadenas.xpendz.data.repository.ObligationSettlementRepository
import com.jcadenas.xpendz.data.repository.TransactionRepository
import com.jcadenas.xpendz.ui.obligations.ObligationOperationGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla Obligaciones.
 *
 * La UI solo representa el dominio: todos los montos/estados vienen de
 * ObligationService.getResolvedState, y las mutaciones pasan por el servicio
 * (nunca por DAOs ni por TransactionRepository directo).
 */
data class ObligationsUiState(
    val isLoading: Boolean = true,
    val obligations: List<ObligationEntity> = emptyList(),
    val resolvedStates: Map<String, ResolvedObligationState> = emptyMap(),
    val selectedTab: String = ObligationEntity.TYPE_RECEIVABLE,
    val receivablePendingCents: Long = 0L,
    val payablePendingCents: Long = 0L,
    val overduePendingCents: Long = 0L,
    val accounts: List<AccountEntity> = emptyList(),
    val accountBalancesCents: Map<String, Long> = emptyMap(),
    val categories: List<CategoryEntity> = emptyList(),
    // Detalle de obligación seleccionada
    val selectedObligation: ObligationEntity? = null,
    val selectedResolvedState: ResolvedObligationState? = null,
    val selectedSettlements: List<ObligationSettlementEntity> = emptyList(),
    val selectedTransactions: Map<String, TransactionEntity> = emptyMap(),
    val selectedCategoryName: String? = null,
    // Banderas de operación (protección doble-submit)
    val isSavingObligation: Boolean = false,
    val isSavingSettlement: Boolean = false,
    val isDeletingSettlement: Boolean = false,
    val isCancellingObligation: Boolean = false,
    // Eventos one-shot consumidos por la pantalla
    val obligationSaved: Boolean = false,
    val settlementSaved: Boolean = false,
    val settlementDeleted: Boolean = false,
    val obligationCancelled: Boolean = false,
    val error: String? = null,
    // Error de dominio de la mutación de abono: se muestra dentro del
    // formulario de settlement (no por snackbar, que queda detrás del modal).
    val settlementFormError: String? = null
)

@HiltViewModel
class ObligationsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val obligationRepository: ObligationRepository,
    private val obligationSettlementRepository: ObligationSettlementRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val obligationService: ObligationService
) : ViewModel() {

    private val _state = MutableStateFlow(ObligationsUiState())
    val state: StateFlow<ObligationsUiState> = _state.asStateFlow()

    private val operationGuard = ObligationOperationGuard()

    // Seam de prueba: permite fijar el uid sin Firebase. En producción es null
    // y el uid siempre proviene de AuthRepository.
    internal var userUidOverride: String? = null

    private var observationJob: Job? = null
    private var detailJob: Job? = null

    private fun effectiveUid(): String? = userUidOverride ?: authRepository.currentUser?.uid

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collectLatest { user ->
                if (!user?.uid.isNullOrBlank() || userUidOverride != null) {
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        val userUid = effectiveUid() ?: return
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val accounts = accountRepository.getAccounts(userUid)
                val categories = categoryRepository.getCategories(userUid)
                val balances = HashMap<String, Long>()
                for (a in accounts) {
                    balances[a.id] = runCatching { accountRepository.computeBalance(userUid, a.id) }.getOrDefault(0L)
                }
                _state.value = _state.value.copy(
                    accounts = accounts,
                    categories = categories,
                    accountBalancesCents = balances
                )
                obligationRepository.observeByUser(userUid).collectLatest { list ->
                    recomputeResolved(userUid, list)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    // Los settlements viven en otra tabla: las mutaciones de abonos no emiten
    // por observeByUser, así que el resumen/resolved se recomputa explícitamente.
    private suspend fun recomputeResolved(userUid: String, list: List<ObligationEntity>) {
        val resolved = HashMap<String, ResolvedObligationState>(list.size)
        var receivable = 0L
        var payable = 0L
        var overdue = 0L
        for (o in list) {
            val st = obligationService.getResolvedState(userUid, o.id)
            resolved[o.id] = st
            if (st.status != ObligationResolvedStatus.CANCELADA) {
                if (ObligationEntity.TYPE_RECEIVABLE == o.type) {
                    receivable += st.pendingAmountCents
                } else {
                    payable += st.pendingAmountCents
                }
                if (st.status == ObligationResolvedStatus.VENCIDA) {
                    overdue += st.pendingAmountCents
                }
            }
        }
        val balances = HashMap<String, Long>()
        for (a in _state.value.accounts) {
            balances[a.id] = runCatching { accountRepository.computeBalance(userUid, a.id) }.getOrDefault(0L)
        }
        _state.value = _state.value.copy(
            isLoading = false,
            obligations = list,
            resolvedStates = resolved,
            receivablePendingCents = receivable,
            payablePendingCents = payable,
            overduePendingCents = overdue,
            accountBalancesCents = balances
        )
        refreshSelectedDetail(userUid)
    }

    fun selectTab(tab: String) {
        _state.value = _state.value.copy(selectedTab = tab)
    }

    // ── Detalle ────────────────────────────────────────────────────
    fun openDetail(obligationId: String) {
        val userUid = effectiveUid() ?: return
        val obligation = _state.value.obligations.firstOrNull { it.id == obligationId } ?: return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                selectedObligation = obligation,
                selectedResolvedState = _state.value.resolvedStates[obligation.id],
                selectedCategoryName = obligation.obligationCategoryId?.let { catId ->
                    _state.value.categories.firstOrNull { it.id == catId }?.name
                }
            )
            obligationSettlementRepository.observeByObligation(obligationId).collectLatest { settlements ->
                val txMap = HashMap<String, TransactionEntity>(settlements.size)
                for (s in settlements) {
                    transactionRepository.getById(s.linkedTransactionId)?.let { txMap[s.id] = it }
                }
                _state.value = _state.value.copy(
                    selectedSettlements = settlements,
                    selectedTransactions = txMap,
                    selectedResolvedState = _state.value.resolvedStates[obligationId]
                )
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _state.value = _state.value.copy(
            selectedObligation = null,
            selectedResolvedState = null,
            selectedSettlements = emptyList(),
            selectedTransactions = emptyMap(),
            selectedCategoryName = null
        )
    }

    private suspend fun refreshSelectedDetail(userUid: String) {
        val selected = _state.value.selectedObligation ?: return
        val fresh = obligationRepository.getById(selected.id) ?: return
        if (fresh.userUid != userUid) return
        _state.value = _state.value.copy(
            selectedObligation = fresh,
            selectedResolvedState = _state.value.resolvedStates[fresh.id],
            selectedCategoryName = fresh.obligationCategoryId?.let { catId ->
                _state.value.categories.firstOrNull { it.id == catId }?.name
            }
        )
    }

    // ── Mutaciones (todas pasan por ObligationService) ─────────────
    fun createObligation(
        type: String,
        title: String,
        counterpartyName: String,
        originalAmountCents: Long,
        issuedAtEpochSec: Long,
        dueAtEpochSec: Long?,
        obligationCategoryId: String?,
        reference: String?,
        notes: String?
    ) {
        val userUid = effectiveUid() ?: return
        if (!operationGuard.tryAcquire(OP_OBLIGATION_SAVE)) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingObligation = true, error = null)
            try {
                obligationService.createObligation(
                    userUid = userUid,
                    type = type,
                    title = title,
                    counterpartyName = counterpartyName,
                    currency = "COP",
                    originalAmountCents = originalAmountCents,
                    issuedAtEpochSec = issuedAtEpochSec,
                    dueAtEpochSec = dueAtEpochSec,
                    obligationCategoryId = obligationCategoryId,
                    reference = reference,
                    notes = notes
                )
                _state.value = _state.value.copy(isSavingObligation = false, obligationSaved = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSavingObligation = false, error = e.message)
            } finally {
                operationGuard.release(OP_OBLIGATION_SAVE)
            }
        }
    }

    fun updateObligationMetadata(
        obligationId: String,
        type: String,
        title: String,
        counterpartyName: String,
        originalAmountCents: Long,
        issuedAtEpochSec: Long,
        dueAtEpochSec: Long?,
        obligationCategoryId: String?,
        reference: String?,
        notes: String?
    ) {
        val userUid = effectiveUid() ?: return
        if (!operationGuard.tryAcquire(OP_OBLIGATION_SAVE)) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingObligation = true, error = null)
            try {
                obligationService.updateObligationMetadata(
                    userUid = userUid,
                    obligationId = obligationId,
                    type = type,
                    title = title,
                    counterpartyName = counterpartyName,
                    currency = "COP",
                    originalAmountCents = originalAmountCents,
                    issuedAtEpochSec = issuedAtEpochSec,
                    dueAtEpochSec = dueAtEpochSec,
                    obligationCategoryId = obligationCategoryId,
                    reference = reference,
                    notes = notes
                )
                _state.value = _state.value.copy(isSavingObligation = false, obligationSaved = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSavingObligation = false, error = e.message)
            } finally {
                operationGuard.release(OP_OBLIGATION_SAVE)
            }
        }
    }

    fun cancelObligation(obligationId: String) {
        val userUid = effectiveUid() ?: return
        if (!operationGuard.tryAcquire(OP_OBLIGATION_CANCEL)) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isCancellingObligation = true, error = null)
            try {
                obligationService.cancelObligation(userUid, obligationId)
                _state.value = _state.value.copy(isCancellingObligation = false, obligationCancelled = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isCancellingObligation = false, error = e.message)
            } finally {
                operationGuard.release(OP_OBLIGATION_CANCEL)
            }
        }
    }

    fun registerSettlement(
        obligationId: String,
        accountId: String,
        financialCategoryId: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ) {
        val userUid = effectiveUid() ?: return
        if (!operationGuard.tryAcquire(OP_SETTLEMENT_SAVE)) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingSettlement = true, settlementFormError = null)
            try {
                obligationService.registerSettlement(
                    userUid = userUid,
                    obligationId = obligationId,
                    accountId = accountId,
                    financialCategoryId = financialCategoryId,
                    amountCents = amountCents,
                    occurredAtEpochSec = occurredAtEpochSec,
                    note = note
                )
                recomputeResolved(userUid, _state.value.obligations)
                _state.value = _state.value.copy(isSavingSettlement = false, settlementSaved = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSavingSettlement = false,
                    settlementFormError = friendlyServiceError(e.message)
                )
            } finally {
                operationGuard.release(OP_SETTLEMENT_SAVE)
            }
        }
    }

    fun updateSettlement(
        settlementId: String,
        accountId: String,
        financialCategoryId: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ) {
        val userUid = effectiveUid() ?: return
        if (!operationGuard.tryAcquire(OP_SETTLEMENT_SAVE)) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingSettlement = true, settlementFormError = null)
            try {
                obligationService.updateSettlement(
                    userUid = userUid,
                    settlementId = settlementId,
                    accountId = accountId,
                    financialCategoryId = financialCategoryId,
                    amountCents = amountCents,
                    occurredAtEpochSec = occurredAtEpochSec,
                    note = note
                )
                recomputeResolved(userUid, _state.value.obligations)
                _state.value = _state.value.copy(isSavingSettlement = false, settlementSaved = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSavingSettlement = false,
                    settlementFormError = friendlyServiceError(e.message)
                )
            } finally {
                operationGuard.release(OP_SETTLEMENT_SAVE)
            }
        }
    }

    fun deleteSettlement(settlementId: String) {
        val userUid = effectiveUid() ?: return
        if (!operationGuard.tryAcquire(OP_SETTLEMENT_DELETE)) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isDeletingSettlement = true, error = null)
            try {
                obligationService.deleteSettlement(userUid, settlementId)
                recomputeResolved(userUid, _state.value.obligations)
                _state.value = _state.value.copy(isDeletingSettlement = false, settlementDeleted = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isDeletingSettlement = false, error = e.message)
            } finally {
                operationGuard.release(OP_SETTLEMENT_DELETE)
            }
        }
    }

    // ── Consumo de eventos one-shot ────────────────────────────────
    fun consumeObligationSaved() {
        _state.value = _state.value.copy(obligationSaved = false)
    }

    fun consumeSettlementSaved() {
        _state.value = _state.value.copy(settlementSaved = false)
    }

    fun consumeSettlementDeleted() {
        _state.value = _state.value.copy(settlementDeleted = false)
    }

    fun consumeObligationCancelled() {
        _state.value = _state.value.copy(obligationCancelled = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun clearSettlementFormError() {
        _state.value = _state.value.copy(settlementFormError = null)
    }

    private fun friendlyServiceError(message: String?): String = when (message) {
        "obligation_cancelled" -> "La obligación está cancelada"
        "obligation_paid" -> "La obligación ya está pagada por completo"
        "settlement_exceeds_pending" -> "El monto excede el saldo pendiente de la obligación"
        "obligation_amount_below_settled" -> "El monto original no puede ser menor a lo ya abonado"
        "linked_transaction_missing" -> "Inconsistencia: el movimiento enlazado no existe"
        "Saldo insuficiente" -> "Saldo insuficiente en la cuenta seleccionada"
        "amountCents" -> "El monto debe ser mayor a $0"
        "accountId" -> "Selecciona una cuenta"
        "financialCategoryId" -> "Selecciona la categoría financiera"
        "obligation_not_found" -> "La obligación no existe"
        "settlement_not_found" -> "El abono no existe"
        null -> "Error inesperado"
        else -> message
    }

    internal fun guard() = operationGuard

    companion object {
        const val OP_OBLIGATION_SAVE = "obligation.save"
        const val OP_OBLIGATION_CANCEL = "obligation.cancel"
        const val OP_SETTLEMENT_SAVE = "settlement.save"
        const val OP_SETTLEMENT_DELETE = "settlement.delete"
    }
}
