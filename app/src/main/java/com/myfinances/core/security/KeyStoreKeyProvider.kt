package com.jcadenas.xpendz.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proveedor de claves para Android Keystore.
 *
 * Encapsula completamente el acceso a Android Keystore:
 * - Crea la clave si no existe
 * - Recupera la clave existente
 * - Maneja errores de Keystore
 *
 * Configuración:
 * - AES-256-GCM
 * - NoPadding
 * - Alias: xpendz_local_master_key_v1
 */
@Singleton
class KeyStoreKeyProvider @Inject constructor() {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "xpendz_local_master_key_v1"
        private const val KEY_SIZE = 256
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    /**
     * Obtiene o crea la clave maestra del Keystore.
     *
     * @return SecretKey de AES-256-GCM
     * @throws KeyStoreException si no se puede obtener o crear la clave
     */
    fun getOrCreateMasterKey(): SecretKey {
        return try {
            val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingKey != null) {
                existingKey.secretKey
            } else {
                createMasterKey()
            }
        } catch (e: KeyStoreException) {
            throw KeyStoreException("Error al acceder al Keystore", e)
        } catch (e: UnrecoverableKeyException) {
            throw KeyStoreException("Clave no recuperable (posiblemente invalidada)", e)
        } catch (e: GeneralSecurityException) {
            throw KeyStoreException("Error de seguridad general en Keystore", e)
        } catch (e: ProviderException) {
            throw KeyStoreException("Error del proveedor de seguridad", e)
        } catch (e: Exception) {
            throw KeyStoreException("Error desconocido al obtener clave", e)
        }
    }

    /**
     * Crea una nueva clave maestra en el Keystore.
     *
     * @return SecretKey recién creada
     * @throws KeyStoreException si no se puede crear la clave
     */
    private fun createMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Verifica si la clave existe en el Keystore.
     *
     * @return true si la clave existe, false en caso contrario
     */
    fun keyExists(): Boolean {
        return keyStore.containsAlias(KEY_ALIAS)
    }
}

/**
 * Excepción lanzada cuando ocurre un error en el Keystore.
 */
class KeyStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
