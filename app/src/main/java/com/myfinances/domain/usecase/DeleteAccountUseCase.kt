package com.jcadenas.xpendz.domain.usecase

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.jcadenas.xpendz.data.local.AppPreferencesKeys
import com.jcadenas.xpendz.data.local.appDataStore
import com.jcadenas.xpendz.data.repository.AccountRepository
import com.jcadenas.xpendz.data.repository.AuthRepository
import com.jcadenas.xpendz.data.repository.BudgetRepository
import com.jcadenas.xpendz.data.repository.CategoryRepository
import com.jcadenas.xpendz.data.repository.ExchangeRateRepository
import com.jcadenas.xpendz.data.repository.GoalRepository
import com.jcadenas.xpendz.data.repository.LoanMovementRepository
import com.jcadenas.xpendz.data.repository.LoanPaymentRepository
import com.jcadenas.xpendz.data.repository.LoanRepository
import com.jcadenas.xpendz.data.repository.TransactionRepository
import com.jcadenas.xpendz.data.repository.TransferRepository
import com.jcadenas.xpendz.data.repository.UserRepository
import com.jcadenas.xpendz.data.repository.UserSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

enum class AuthProvider { EMAIL, GOOGLE }

sealed class DeleteAccountResult {
    object Success : DeleteAccountResult()
    data class RequiresReauthentication(val providers: List<AuthProvider>) : DeleteAccountResult()
    data class Error(val message: String) : DeleteAccountResult()
}

@Singleton
class DeleteAccountUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val sharedPreferences: SharedPreferences,
    private val firestore: FirebaseFirestore,
    private val transactionRepository: TransactionRepository,
    private val transferRepository: TransferRepository,
    private val loanPaymentRepository: LoanPaymentRepository,
    private val loanMovementRepository: LoanMovementRepository,
    private val loanRepository: LoanRepository,
    private val goalRepository: GoalRepository,
    private val budgetRepository: BudgetRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val userRepository: UserRepository
) {

    suspend fun execute(userUid: String): DeleteAccountResult {
        // Paso 1: eliminar datos remotos (Firestore)
        try {
            transactionRepository.deleteAllRemoteByUser(userUid)
            transferRepository.deleteAllRemoteByUser(userUid)
            loanPaymentRepository.deleteAllRemoteByUser(userUid)
            loanMovementRepository.deleteAllRemoteByUser(userUid)
            loanRepository.deleteAllRemoteByUser(userUid)
            goalRepository.deleteAllRemoteByUser(userUid)
            budgetRepository.deleteAllRemoteByUser(userUid)
            accountRepository.deleteAllRemoteByUser(userUid)
            categoryRepository.deleteAllRemoteByUser(userUid)
            exchangeRateRepository.deleteAllRemoteByUser(userUid)
            userSettingsRepository.deleteAllRemoteByUser(userUid)
            firestore.collection("users").document(userUid).delete().await()
        } catch (e: Exception) {
            Log.e("DeleteAccountUseCase", "Error al eliminar datos remotos", e)
            return DeleteAccountResult.Error(
                "No se pudieron eliminar los datos del servidor. Inténtalo de nuevo."
            )
        }

        // Paso 2: eliminar cuenta de Firebase Authentication
        val deleteResult = authRepository.deleteAccount()
        if (deleteResult.isFailure) {
            val e = deleteResult.exceptionOrNull()
            return if (e is FirebaseAuthRecentLoginRequiredException) {
                val providers = detectProvider()
                Log.w("DeleteAccountUseCase", "Reautenticación requerida, proveedores=$providers")
                DeleteAccountResult.RequiresReauthentication(providers)
            } else {
                Log.e("DeleteAccountUseCase", "Error al eliminar cuenta Firebase Auth", e)
                DeleteAccountResult.Error(
                    "No se pudo eliminar la cuenta. Inténtalo de nuevo."
                )
            }
        }

        // Paso 3: limpiar datos locales
        cleanLocalData(userUid)

        // Paso 4: cerrar sesión
        authRepository.signOut()

        return DeleteAccountResult.Success
    }

    suspend fun reauthenticateAndExecute(
        userUid: String,
        credential: AuthCredential
    ): DeleteAccountResult {
        val reauthResult = authRepository.reauthenticate(credential)
        if (reauthResult.isFailure) {
            Log.e("DeleteAccountUseCase", "Reautenticación fallida", reauthResult.exceptionOrNull())
            return DeleteAccountResult.Error(
                "Credenciales incorrectas. Verifica e inténtalo de nuevo."
            )
        }
        // Retomar desde Paso 2: los datos remotos ya fueron eliminados
        val deleteResult = authRepository.deleteAccount()
        if (deleteResult.isFailure) {
            Log.e("DeleteAccountUseCase", "Error al eliminar Firebase Auth tras reautenticación",
                deleteResult.exceptionOrNull())
            return DeleteAccountResult.Error(
                "No se pudo eliminar la cuenta. Inténtalo de nuevo."
            )
        }
        cleanLocalData(userUid)
        authRepository.signOut()
        return DeleteAccountResult.Success
    }

    private suspend fun cleanLocalData(userUid: String) {
        try {
            transactionRepository.deleteAllLocalByUser(userUid)
            transferRepository.deleteAllLocalByUser(userUid)
            loanPaymentRepository.deleteAllLocalByUser(userUid)
            loanMovementRepository.deleteAllLocalByUser(userUid)
            loanRepository.deleteAllLocalByUser(userUid)
            goalRepository.deleteAllLocalByUser(userUid)
            budgetRepository.deleteAllLocalByUser(userUid)
            accountRepository.deleteAllLocalByUser(userUid)
            categoryRepository.deleteAllLocalByUser(userUid)
            exchangeRateRepository.deleteAllLocalByUser(userUid)
            userSettingsRepository.deleteAllLocalByUser(userUid)
            userRepository.deleteByUid(userUid)
            context.appDataStore.edit { prefs ->
                prefs[AppPreferencesKeys.ONBOARDING_COMPLETED] = false
            }
            // Usar commit() en lugar de apply() para garantizar escritura síncrona antes
            // de que el proceso pueda finalizar tras el signOut().
            sharedPreferences.edit().remove("device_id").commit()
        } catch (e: Exception) {
            // La limpieza local es best-effort: la cuenta Firebase Auth ya fue eliminada,
            // por lo que el UID no puede volver a autenticarse. Los datos locales huérfanos
            // serán sobreescritos o ignorados en la próxima sesión de otro usuario.
            Log.e("DeleteAccountUseCase", "Error en limpieza local (best-effort, no crítico)", e)
        }
    }

    private fun detectProvider(): List<AuthProvider> {
        val providerData = authRepository.currentUser?.providerData
            ?: return listOf(AuthProvider.EMAIL)
        val providers = mutableListOf<AuthProvider>()
        if (providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }) {
            providers.add(AuthProvider.GOOGLE)
        }
        if (providerData.any { it.providerId == "password" }) {
            providers.add(AuthProvider.EMAIL)
        }
        return providers.ifEmpty { listOf(AuthProvider.EMAIL) }
    }
}
