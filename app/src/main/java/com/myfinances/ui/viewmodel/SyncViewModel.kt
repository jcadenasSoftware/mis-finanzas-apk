package com.myfinances.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.data.repository.AccountRepository
import com.myfinances.data.repository.AuthRepository
import com.myfinances.data.repository.CategoryRepository
import com.myfinances.data.repository.TransactionRepository
import com.myfinances.data.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val transferRepository: TransferRepository
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncVersion = MutableStateFlow(0)
    val syncVersion: StateFlow<Int> = _syncVersion.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun syncAll() {
        val uid = authRepository.currentUser?.uid
        if (uid.isNullOrBlank()) {
            return
        }

        if (_isSyncing.value) {
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                accountRepository.syncFromFirestore(uid)
                categoryRepository.syncFromFirestore(uid)
                transactionRepository.syncFromFirestore(uid)
                transferRepository.syncFromFirestore(uid)
                _syncVersion.value = _syncVersion.value + 1
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
