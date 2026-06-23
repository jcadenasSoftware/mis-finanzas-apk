package com.myfinances.core.security.backup

import com.myfinances.core.security.backup.BackupCryptoConstants.KEY_ALGORITHM
import com.myfinances.core.security.backup.BackupCryptoConstants.KEY_LENGTH
import com.myfinances.core.security.backup.BackupCryptoConstants.KDF_ALGORITHM
import com.myfinances.core.security.backup.BackupCryptoConstants.PBKDF2_ITERATIONS
import com.myfinances.core.security.backup.BackupCryptoConstants.SALT_LENGTH
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Servicio de derivación de claves desde contraseñas.
 *
 * Responsabilidades:
 * - Generar salt aleatorio seguro
 * - Derivar clave criptográfica desde contraseña usando PBKDF2
 *
 * Características:
 * - No depende de Android Keystore (portable a Desktop)
 * - Usa PBKDF2WithHmacSHA256 (compatible Android/Java)
 * - Configuración centralizada en BackupCryptoConstants
 *
 * No debe conocer:
 * - AES
 * - Formato de archivos
 * - Lógica de almacenamiento
 */
@Singleton
class PasswordKeyDerivationService @Inject constructor() {

    private val secureRandom = SecureRandom()

    /**
     * Genera un salt aleatorio seguro para derivación de clave.
     *
     * El salt debe ser único para cada derivación para prevenir
     * ataques de rainbow table y garantizar que la misma contraseña
     * produzca claves diferentes en cada ocasión.
     *
     * @return Salt de 16 bytes aleatorio
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Deriva una clave criptográfica desde una contraseña.
     *
     * Utiliza PBKDF2WithHmacSHA256 con parámetros configurables:
     * - Salt único para cada derivación
     * - Iteraciones configurables (310,000 por defecto)
     * - Output de 256 bits (32 bytes) para AES-256
     *
     * @param password Contraseña en texto claro
     * @param salt Salt aleatorio (debe ser el mismo para descifrar)
     * @param iterations Número de iteraciones PBKDF2
     * @return Clave derivada de 32 bytes (256 bits)
     * @throws KeyDerivationException si la derivación falla
     */
    fun deriveKey(
        password: String,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS
    ): SecretKey {
        val passwordChars = password.toCharArray()
        val keySpec = PBEKeySpec(passwordChars, salt, iterations, KEY_LENGTH * 8)

        return try {
            require(salt.size == SALT_LENGTH) {
                "Salt debe tener exactamente $SALT_LENGTH bytes"
            }

            require(iterations > 0) {
                "Iteraciones debe ser mayor que 0"
            }

            val keyFactory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            keyFactory.generateSecret(keySpec)
        } catch (e: IllegalArgumentException) {
            throw KeyDerivationException("Parámetros inválidos para derivación de clave", e)
        } catch (e: Exception) {
            throw KeyDerivationException("Error al derivar clave desde contraseña", e)
        } finally {
            // Limpieza defensiva del password en memoria
            passwordChars.fill('\u0000')
            keySpec.clearPassword()
        }
    }

    /**
     * Deriva una clave criptográfica desde una contraseña usando iteraciones por defecto.
     *
     * @param password Contraseña en texto claro
     * @param salt Salt aleatorio (debe ser el mismo para descifrar)
     * @return Clave derivada de 32 bytes (256 bits)
     * @throws KeyDerivationException si la derivación falla
     */
    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        return deriveKey(password, salt, PBKDF2_ITERATIONS)
    }
}

/**
 * Excepción lanzada cuando ocurre un error en la derivación de clave.
 */
class KeyDerivationException(message: String, cause: Throwable? = null) : Exception(message, cause)
