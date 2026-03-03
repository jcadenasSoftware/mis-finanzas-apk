package com.myfinances.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.data.local.dao.UserDao
import com.myfinances.data.local.entity.UserEntity
import com.myfinances.data.local.entity.AccountEntity
import com.myfinances.data.repository.AccountRepository
import com.myfinances.data.repository.AuthRepository
import com.myfinances.data.repository.BudgetRepository
import com.myfinances.data.repository.CategoryRepository
import com.myfinances.data.repository.ExchangeRateRepository
import com.myfinances.data.repository.GoalRepository
import com.myfinances.data.repository.LoanPaymentRepository
import com.myfinances.data.repository.LoanRepository
import com.myfinances.data.repository.TransactionRepository
import com.myfinances.data.repository.TransferRepository
import com.myfinances.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AccountWithBalance(
    val account: AccountEntity,
    val balanceCents: Long
)

data class DashboardState(
    val userEmail: String = "",
    val accounts: List<AccountWithBalance> = emptyList(),
    val totalBalanceCents: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddAccountDialog: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDao: UserDao,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val transferRepository: TransferRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val loanRepository: LoanRepository,
    private val loanPaymentRepository: LoanPaymentRepository,
    private val budgetRepository: BudgetRepository,
    private val goalRepository: GoalRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val userUid: String?
        get() = authRepository.currentUser?.uid

    private var lastLoadedUid: String? = null

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                val uid = user?.uid
                if (uid.isNullOrBlank()) {
                    lastLoadedUid = null
                    _state.value = DashboardState()
                    return@collect
                }

                if (uid != lastLoadedUid) {
                    lastLoadedUid = uid
                    loadData()
                }
            }
        }
    }

    fun loadData() {
        val uid = userUid ?: return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                userEmail = authRepository.currentUser?.email ?: ""
            )

            try {
                ensureUserExists(uid)

                Log.d("DashboardViewModel", "Starting sync from Firestore for user: $uid")
                accountRepository.syncFromFirestore(uid)
                Log.d("DashboardViewModel", "Accounts synced")
                categoryRepository.syncFromFirestore(uid)
                Log.d("DashboardViewModel", "Categories synced")
                transactionRepository.syncFromFirestore(uid)
                Log.d("DashboardViewModel", "Transactions synced")
                transferRepository.syncFromFirestore(uid)
                Log.d("DashboardViewModel", "Transfers synced")

                userSettingsRepository.syncFromFirestore(uid)
                exchangeRateRepository.syncFromFirestore(uid)
                loanRepository.syncFromFirestore(uid)
                loanPaymentRepository.syncFromFirestore(uid)
                budgetRepository.syncFromFirestore(uid)
                goalRepository.syncFromFirestore(uid)

                loadAccountsWithBalances(uid)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error during load", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private suspend fun ensureUserExists(uid: String) {
        val now = System.currentTimeMillis() / 1000
        val email = authRepository.currentUser?.email ?: ""
        userDao.upsert(
            UserEntity(
                uid = uid,
                email = email,
                createdAtEpochSec = now,
                updatedAtEpochSec = now
            )
        )
    }

    private suspend fun loadAccountsWithBalances(uid: String) {
        Log.d("DashboardViewModel", "Loading accounts with balances for user: $uid")
        val accounts = accountRepository.getAccounts(uid)
        Log.d("DashboardViewModel", "Found ${accounts.size} accounts in local database")
        
        val accountsWithBalance = accounts.map { account ->
            val balance = accountRepository.computeBalance(uid, account.id)
            Log.d("DashboardViewModel", "Account: ${account.name}, Balance: $balance")
            AccountWithBalance(account, balance)
        }
        val total = accountsWithBalance.sumOf { it.balanceCents }
        Log.d("DashboardViewModel", "Total balance: $total")

        _state.value = _state.value.copy(
            accounts = accountsWithBalance,
            totalBalanceCents = total,
            isLoading = false
        )
        Log.d("DashboardViewModel", "State updated with ${accountsWithBalance.size} accounts")
    }

    fun refreshBalances() {
        val uid = userUid ?: return
        viewModelScope.launch {
            loadAccountsWithBalances(uid)
        }
    }

    fun showAddAccountDialog() {
        _state.value = _state.value.copy(showAddAccountDialog = true)
    }

    fun hideAddAccountDialog() {
        _state.value = _state.value.copy(showAddAccountDialog = false)
    }

    fun createAccount(name: String, type: String, currency: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                accountRepository.create(uid, name, type, currency)
                hideAddAccountDialog()
                loadAccountsWithBalances(uid)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateAccountName(accountId: String, newName: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                accountRepository.updateName(uid, accountId, newName)
                loadAccountsWithBalances(uid)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteAccount(accountId: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                val deleted = accountRepository.delete(uid, accountId)
                if (!deleted) {
                    _state.value = _state.value.copy(
                        error = "No se puede eliminar la cuenta porque tiene movimientos o saldo asociado. Deja el saldo en cero y elimina/mueve sus movimientos para poder eliminarla."
                    )
                } else {
                    loadAccountsWithBalances(uid)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun syncFromFirestore() {
        Log.d("DashboardViewModel", "Sync button clicked!")
        val uid = userUid ?: return
        Log.d("DashboardViewModel", "User UID: $uid")
        
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                
                Log.d("DashboardViewModel", "Starting manual sync from Firestore for user: $uid")
                
                // Test Firestore connection first
                Log.d("DashboardViewModel", "Testing Firestore connection...")
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val testDoc = firestore.collection("test").document("connection")
                testDoc.set(mapOf("timestamp" to System.currentTimeMillis())).await()
                Log.d("DashboardViewModel", "Firestore connection successful!")
                
                Log.d("DashboardViewModel", "About to call accountRepository.syncFromFirestore")
                accountRepository.syncFromFirestore(uid)
                Log.d("DashboardViewModel", "Accounts synced")
                
                Log.d("DashboardViewModel", "About to call categoryRepository.syncFromFirestore")
                categoryRepository.syncFromFirestore(uid)
                Log.d("DashboardViewModel", "Categories synced")
                
                Log.d("DashboardViewModel", "About to call transactionRepository.syncFromFirestore")
                transactionRepository.syncFromFirestore(uid)
                Log.d("DashboardViewModel", "Transactions synced")
                
                Log.d("DashboardViewModel", "About to call transferRepository.syncFromFirestore")
                transferRepository.syncFromFirestore(uid)
                Log.d("DashboardViewModel", "Transfers synced")

                userSettingsRepository.syncFromFirestore(uid)
                exchangeRateRepository.syncFromFirestore(uid)
                loanRepository.syncFromFirestore(uid)
                loanPaymentRepository.syncFromFirestore(uid)
                budgetRepository.syncFromFirestore(uid)
                goalRepository.syncFromFirestore(uid)

                // Reload data
                loadAccountsWithBalances(uid)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "=== ERROR DURING SYNC ===", e)
                Log.e("DashboardViewModel", "Error type: ${e.javaClass.simpleName}")
                Log.e("DashboardViewModel", "Error message: ${e.message}")
                e.printStackTrace()
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Error al sincronizar: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
