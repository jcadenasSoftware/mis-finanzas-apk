package com.myfinances.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.data.repository.AccountRepository
import com.myfinances.data.repository.AuthRepository
import com.myfinances.data.repository.BudgetRepository
import com.myfinances.data.repository.CategoryRepository
import com.myfinances.data.repository.ExchangeRateRepository
import com.myfinances.data.repository.LoanMovementRepository
import com.myfinances.data.repository.GoalRepository
import com.myfinances.data.repository.LoanPaymentRepository
import com.myfinances.data.repository.LoanRepository
import com.myfinances.data.repository.TransactionRepository
import com.myfinances.data.repository.TransferRepository
import com.myfinances.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class SyncProgressState(
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val message: String? = null,
    val isCancelling: Boolean = false
) {
    val progress: Float
        get() = if (totalSteps <= 0) 0f else currentStep.toFloat() / totalSteps.toFloat()
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val transferRepository: TransferRepository,
    private val budgetRepository: BudgetRepository,
    private val goalRepository: GoalRepository,
    private val loanRepository: LoanRepository,
    private val loanPaymentRepository: LoanPaymentRepository,
    private val loanMovementRepository: LoanMovementRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {

    companion object {
        private const val TOTAL_SYNC_STEPS = 11
        private const val MIN_SYNC_INTERVAL_MS = 45_000L
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncVersion = MutableStateFlow(0)
    val syncVersion: StateFlow<Int> = _syncVersion.asStateFlow()

    private val _baseDataVersion = MutableStateFlow(0)
    val baseDataVersion: StateFlow<Int> = _baseDataVersion.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _progress = MutableStateFlow(SyncProgressState())
    val progress: StateFlow<SyncProgressState> = _progress.asStateFlow()

    private var syncJob: Job? = null
    private var lastSyncStartedAtMs: Long = 0L

    fun clearError() {
        _error.value = null
    }

    fun cancelSync() {
        val job = syncJob ?: return
        if (!job.isActive || _progress.value.isCancelling) {
            return
        }
        _progress.value = _progress.value.copy(
            isCancelling = true,
            message = "Cancelando sincronización..."
        )
        viewModelScope.launch {
            job.cancelAndJoin()
        }
    }

    fun syncAll(force: Boolean = false) {
        val uid = authRepository.currentUser?.uid
        if (uid.isNullOrBlank()) {
            return
        }

        if (_isSyncing.value) {
            return
        }

        val now = System.currentTimeMillis()
        if (!force && now - lastSyncStartedAtMs < MIN_SYNC_INTERVAL_MS) {
            return
        }

        syncJob = viewModelScope.launch {
            lastSyncStartedAtMs = System.currentTimeMillis()
            _isSyncing.value = true
            updateProgress(step = 0, message = "Preparando sincronización...")
            try {
                ensureActiveSync()
                updateProgress(step = 1, message = "Sincronizando cuentas...")
                accountRepository.syncFromFirestore(uid)

                ensureActiveSync()
                updateProgress(step = 2, message = "Sincronizando categorías...")
                categoryRepository.syncFromFirestore(uid)
                _baseDataVersion.value = _baseDataVersion.value + 1

                ensureActiveSync()
                updateProgress(step = 3, message = "Sincronizando transacciones...")
                coroutineScope {
                    awaitAll(
                        async { transactionRepository.syncFromFirestore(uid) },
                        async {
                            updateProgress(step = 4, message = "Sincronizando transferencias...")
                            transferRepository.syncFromFirestore(uid)
                        },
                        async {
                            updateProgress(step = 5, message = "Sincronizando configuración...")
                            userSettingsRepository.syncFromFirestore(uid)
                        },
                        async {
                            updateProgress(step = 6, message = "Sincronizando tasas de cambio...")
                            exchangeRateRepository.syncFromFirestore(uid)
                        },
                        async {
                            updateProgress(step = 7, message = "Sincronizando préstamos...")
                            loanRepository.syncFromFirestore(uid)
                        },
                        async {
                            updateProgress(step = 8, message = "Sincronizando presupuestos y metas...")
                            budgetRepository.syncFromFirestore(uid)
                            goalRepository.syncFromFirestore(uid)
                        }
                    )
                }

                ensureActiveSync()
                updateProgress(step = 9, message = "Sincronizando pagos de préstamos...")
                loanPaymentRepository.syncFromFirestore(uid)

                ensureActiveSync()
                updateProgress(step = 10, message = "Sincronizando movimientos de préstamos...")
                loanMovementRepository.syncFromFirestore(uid)

                ensureActiveSync()
                updateProgress(step = 11, message = "Sincronización completada")
                _syncVersion.value = _syncVersion.value + 1
            } catch (_: CancellationException) {
                _status.value = "Sincronización cancelada"
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
            } finally {
                _isSyncing.value = false
                _status.value = null
                _progress.value = SyncProgressState()
                syncJob = null
            }
        }
    }

    private fun updateProgress(step: Int, message: String) {
        _status.value = message
        _progress.value = SyncProgressState(
            currentStep = step,
            totalSteps = TOTAL_SYNC_STEPS,
            message = message,
            isCancelling = _progress.value.isCancelling
        )
    }

    private suspend fun ensureActiveSync() {
        if (!currentCoroutineContext().isActive) {
            throw CancellationException("Sync cancelled")
        }
    }
}
