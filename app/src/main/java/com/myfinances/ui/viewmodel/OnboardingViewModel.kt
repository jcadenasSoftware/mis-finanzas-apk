package com.jcadenas.xpendz.ui.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcadenas.xpendz.data.local.AppPreferencesKeys
import com.jcadenas.xpendz.data.local.appDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val ONBOARDING_COMPLETED = AppPreferencesKeys.ONBOARDING_COMPLETED

    val onboardingCompleted = context.appDataStore.data
        .map { prefs -> prefs[ONBOARDING_COMPLETED] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun completeOnboarding() {
        viewModelScope.launch {
            context.appDataStore.edit { prefs ->
                prefs[ONBOARDING_COMPLETED] = true
            }
        }
    }
}
