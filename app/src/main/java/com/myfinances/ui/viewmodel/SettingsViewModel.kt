package com.myfinances.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.data.local.entity.ExchangeRateEntity
import com.myfinances.data.local.entity.UserSettingsEntity
import com.myfinances.data.repository.AuthRepository
import com.myfinances.data.repository.ExchangeRateRepository
import com.myfinances.data.repository.UserSettingsRepository
import com.myfinances.ui.util.CountryCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val isLoading: Boolean = false,
    val countryCode: String = "CO",
    val baseCurrency: String = "COP",
    val rates: List<ExchangeRateEntity> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val exchangeRateRepository: ExchangeRateRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val uid: String?
        get() = authRepository.currentUser?.uid

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collectLatest { user ->
                val userUid = user?.uid
                if (userUid.isNullOrBlank()) {
                    _state.value = SettingsState()
                    return@collectLatest
                }

                exchangeRateRepository.observeAll(userUid).collectLatest { rates ->
                    _state.value = _state.value.copy(rates = rates)
                }
            }
        }

        viewModelScope.launch {
            authRepository.observeAuthState().collectLatest { user ->
                val userUid = user?.uid
                if (userUid.isNullOrBlank()) {
                    return@collectLatest
                }

                userSettingsRepository.observe(userUid).collectLatest { settings ->
                    if (settings == null) {
                        val defaultCountry = "CO"
                        val defaultCurrency = CountryCurrency.suggestedCurrency(defaultCountry)
                        _state.value = _state.value.copy(
                            countryCode = defaultCountry,
                            baseCurrency = defaultCurrency
                        )
                    } else {
                        _state.value = _state.value.copy(
                            countryCode = settings.countryCode,
                            baseCurrency = settings.baseCurrency
                        )
                    }
                }
            }
        }
    }

    fun saveCountry(countryCode: String) {
        val userUid = uid ?: return
        viewModelScope.launch {
            val suggested = CountryCurrency.suggestedCurrency(countryCode)
            userSettingsRepository.upsert(userUid, countryCode, suggested)
        }
    }

    fun saveBaseCurrency(baseCurrency: String) {
        val userUid = uid ?: return
        viewModelScope.launch {
            val country = _state.value.countryCode
            userSettingsRepository.upsert(userUid, country, baseCurrency)
        }
    }

    fun upsertRate(from: String, to: String, rate: Double) {
        val userUid = uid ?: return
        viewModelScope.launch {
            exchangeRateRepository.upsert(userUid, from, to, rate)
        }
    }

    fun deleteRate(rateId: String) {
        val userUid = uid ?: return
        viewModelScope.launch {
            exchangeRateRepository.delete(userUid, rateId)
        }
    }

    fun syncFromFirestore() {
        val userUid = uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                userSettingsRepository.syncFromFirestore(userUid)
                exchangeRateRepository.syncFromFirestore(userUid)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

}
