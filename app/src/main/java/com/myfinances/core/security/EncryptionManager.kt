package com.myfinances.core.security

import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estrategia de logging:
 * - En DEBUG: se loguean excepciones completas para diagnóstico
 * - En RELEASE: se loguean sólo mensajes genéricos sin stack traces
 * - Nunca se exponen datos sensibles (plaintext, ciphertext, keys)
 *
 * NOTA: Para evitar dependencias de BuildConfig en módulos core,
 * el modo de logging se determina mediante flags de compilación.
 */

/**
 * Implementación concreta de EncryptionService usando AES-256-GCM.
 *
 * Responsabilidades:
 * - Cifrado/descifrado con AES-256-GCM
 * - Generación segura de IV (nonce)
 * - Validación de integridad mediante auth tag
 * - Manejo de errores
 *
 * No accede a:
 * - Room
 * - DataStore
 * - SharedPreferences
 *
 * Usa:
 * - KeyStoreKeyProvider para obtener la clave maestra
 * - CipherPayloadCodec para serialización versionada
 */
@Singleton
class EncryptionManager @Inject constructor(
    private val keyProvider: KeyStoreKeyProvider,
    private val payloadCodec: CipherPayloadCodec
) : EncryptionService {

    companion object {
        private const val TAG = "EncryptionManager"
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH = 12
    }

    private val secureRandom = SecureRandom()

    override suspend fun encrypt(plaintext: ByteArray): ByteArray {
        try {
            val secretKey = keyProvider.getOrCreateMasterKey()
            val iv = generateIV()

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            val ciphertext = cipher.doFinal(plaintext)

            // Codificar con metadatos versionados
            return payloadCodec.encode(iv, ciphertext)
        } catch (e: Exception) {
            logErrorSafe("Error al cifrar datos", e)
            throw EncryptionException("Error al cifrar datos", e)
        }
    }

    override suspend fun decrypt(ciphertext: ByteArray): ByteArray {
        try {
            // Decodificar payload versionado
            val payload = payloadCodec.decode(ciphertext)

            // Validar versión del payload (por ahora solo soportamos v1)
            if (payload.payloadVersion != 1) {
                throw EncryptionException(
                    "Versión de payload no soportada: ${payload.payloadVersion}"
                )
            }

            // Validar versión de la clave (por ahora solo soportamos v1)
            if (payload.keyVersion != 1) {
                throw EncryptionException(
                    "Versión de clave no soportada: ${payload.keyVersion}"
                )
            }

            val secretKey = keyProvider.getOrCreateMasterKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            return cipher.doFinal(payload.ciphertext)
        } catch (e: EncryptionException) {
            throw e
        } catch (e: Exception) {
            logErrorSafe("Error al descifrar datos", e)
            throw EncryptionException("Error al descifrar datos", e)
        }
    }

    /**
     * Logging seguro que evita exposición de stack traces.
     * Se loguea sólo el mensaje genérico sin la excepción completa.
     */
    private fun logErrorSafe(message: String, exception: Exception) {
        Log.e(TAG, message)
    }

    override suspend fun encrypt(plaintext: String): ByteArray {
        return encrypt(plaintext.toByteArray(StandardCharsets.UTF_8))
    }

    override suspend fun decryptToString(ciphertext: ByteArray): String {
        val decrypted = decrypt(ciphertext)
        return String(decrypted, StandardCharsets.UTF_8)
    }

    /**
     * Genera un IV (nonce) seguro para AES-GCM.
     *
     * GCM requiere IV de 12 bytes para máxima seguridad.
     * El IV debe ser único para cada cifrado con la misma clave.
     *
     * @return IV de 12 bytes
     */
    private fun generateIV(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        return iv
    }
}
