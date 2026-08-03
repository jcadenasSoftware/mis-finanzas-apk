package com.jcadenas.xpendz.data.repository

import android.util.Log
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    val isLoggedIn: Boolean
        get() = currentUser != null

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        Log.d("AuthRepository", "[DIAGNOSTIC] signInWithGoogle called")
        Log.d("AuthRepository", "[DIAGNOSTIC] idToken length: ${idToken.length}")
        Log.d("AuthRepository", "[DIAGNOSTIC] idToken preview: ${if (idToken.length >= 20) idToken.take(20) + "..." else idToken}")
        return try {
            Log.d("AuthRepository", "[DIAGNOSTIC] Creating GoogleAuthProvider.getCredential(idToken, null)")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            Log.d("AuthRepository", "[DIAGNOSTIC] Credential created successfully")
            Log.d("AuthRepository", "[DIAGNOSTIC] Calling firebaseAuth.signInWithCredential(credential)")
            val result = firebaseAuth.signInWithCredential(credential).await()
            Log.d("AuthRepository", "[DIAGNOSTIC] firebaseAuth.signInWithCredential completed")
            Log.d("AuthRepository", "[DIAGNOSTIC] result.user: ${result.user?.email}")
            Log.d("AuthRepository", "[DIAGNOSTIC] result.user.uid: ${result.user?.uid}")
            result.user?.let { 
                Log.d("AuthRepository", "[DIAGNOSTIC] Returning Result.success(user)")
                Result.success(it) 
            } ?: run {
                Log.e("AuthRepository", "[DIAGNOSTIC] result.user is NULL")
                Result.failure(Exception("Usuario no encontrado"))
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "[DIAGNOSTIC] Exception caught in signInWithGoogle")
            Log.e("AuthRepository", "[DIAGNOSTIC] Exception class: ${e.javaClass.simpleName}")
            Log.e("AuthRepository", "[DIAGNOSTIC] Exception.message: ${e.message}")
            Log.e("AuthRepository", "[DIAGNOSTIC] Exception.localizedMessage: ${e.localizedMessage}")
            Log.e("AuthRepository", "[DIAGNOSTIC] Exception full stack trace", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            result.user?.let { Result.success(it) }
                ?: Result.failure(Exception("Usuario no encontrado"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createUserWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let { Result.success(it) }
                ?: Result.failure(Exception("No se pudo crear el usuario"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        return try {
            firebaseAuth.currentUser?.delete()?.await()
                ?: return Result.failure(Exception("No hay usuario autenticado"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reauthenticate(credential: AuthCredential): Result<Unit> {
        return try {
            firebaseAuth.currentUser?.reauthenticate(credential)?.await()
                ?: return Result.failure(Exception("No hay usuario autenticado"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }

    fun buildEmailCredential(email: String, password: String): AuthCredential =
        EmailAuthProvider.getCredential(email, password)

    fun buildGoogleCredential(idToken: String): AuthCredential =
        GoogleAuthProvider.getCredential(idToken, null)
}
