package com.myfinances.core.security.backup

import com.myfinances.core.security.backup.BackupCryptoConstants.ENCRYPTION_ALGORITHM
import com.myfinances.core.security.backup.BackupCryptoConstants.ENCRYPTION_VERSION
import com.myfinances.core.security.backup.BackupCryptoConstants.FILE_VERSION
import com.myfinances.core.security.backup.BackupCryptoConstants.GCM_TAG_LENGTH_BITS
import com.myfinances.core.security.backup.BackupCryptoConstants.IV_LENGTH
import com.myfinances.core.security.backup.BackupCryptoConstants.KDF_VERSION
import com.myfinances.core.security.backup.BackupCryptoConstants.KEY_ALGORITHM
import com.myfinances.core.security.backup.BackupCryptoConstants.MAGIC_NUMBER
import com.myfinances.core.security.backup.BackupCryptoConstants.PBKDF2_ITERATIONS
import com.myfinances.core.security.backup.BackupCryptoConstants.SALT_LENGTH
import com.myfinances.core.security.backup.BackupCryptoConstants.TOTAL_HEADER_LENGTH
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación concreta de BackupEncryptionService usando PBKDF2 + AES-256-GCM.
 *
 * Responsabilidades:
 * - Derivar clave desde contraseña con PBKDF2
 * - Generar IV seguro para AES-GCM
 * - Crear header versionado con metadatos
 * - Autenticar header como AAD (Additional Authenticated Data)
 * - Cifrar/descifrar datos con AES-256-GCM
 * - Validar integridad mediante GCM tag
 *
 * Características:
 * - No depende de Android Keystore (portable a Desktop)
 * - Usa AAD para autenticar metadatos del header
 * - Limpieza defensiva de datos sensibles
 *
 * No accede a:
 * - Room
 * - DataStore
 * - SharedPreferences
 * - Android Keystore
 */
@Singleton
class BackupEncryptionManager @Inject constructor(
    private val keyDerivationService: PasswordKeyDerivationService,
    private val fileCodec: BackupFileCodec
) : BackupEncryptionService {

    companion object

    private val secureRandom = SecureRandom()

    override suspend fun encryptBackup(data: ByteArray, password: String): ByteArray {
        return try {
            // Generar salt e IV
            val salt = keyDerivationService.generateSalt()
            val iv = generateIV()

            // Derivar clave desde contraseña
            val secretKey = keyDerivationService.deriveKey(password, salt, PBKDF2_ITERATIONS)

            // Crear header con metadatos
            val header = BackupFileCodec.BackupHeader(
                magicNumber = MAGIC_NUMBER,
                fileVersion = FILE_VERSION.toInt(),
                encryptionVersion = ENCRYPTION_VERSION.toInt(),
                kdfVersion = KDF_VERSION.toInt(),
                iterations = PBKDF2_ITERATIONS,
                salt = salt,
                iv = iv
            )

            // Codificar header
            val headerBytes = fileCodec.encodeHeader(header)

            // Inicializar cipher para cifrado
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            val keySpec = SecretKeySpec(secretKey.encoded, KEY_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec)

            // Autenticar header como AAD
            cipher.updateAAD(headerBytes)

            // Cifrar datos
            val ciphertext = cipher.doFinal(data)

            // Ensamblar archivo completo (header + ciphertext)
            val result = ByteArrayOutputStream()
            result.write(headerBytes)
            result.write(ciphertext)

            // Limpieza defensiva
            Arrays.fill(secretKey.encoded, 0.toByte())
            Arrays.fill(salt, 0.toByte())
            Arrays.fill(iv, 0.toByte())

            result.toByteArray()
        } catch (e: BackupEncryptionException) {
            throw e
        } catch (e: Exception) {
            throw BackupEncryptionException("Error al cifrar backup", e)
        }
    }

    override suspend fun decryptBackup(encryptedData: ByteArray, password: String): ByteArray {
        return try {
            // Validación defensiva: archivo vacío
            if (encryptedData.isEmpty()) {
                throw BackupEncryptionException("Archivo de backup vacío")
            }

            // Validación defensiva: archivo demasiado corto para header
            if (encryptedData.size < TOTAL_HEADER_LENGTH) {
                throw BackupEncryptionException("Archivo demasiado corto para contener header")
            }

            // Extraer header
            val headerBytes = encryptedData.copyOfRange(0, TOTAL_HEADER_LENGTH)

            // Decodificar header (valida magic number y versiones)
            val header = fileCodec.decodeHeader(headerBytes)

            // Extraer ciphertext
            val ciphertext = encryptedData.copyOfRange(TOTAL_HEADER_LENGTH, encryptedData.size)

            // Validación defensiva: ciphertext vacío
            if (ciphertext.isEmpty()) {
                throw BackupEncryptionException("Archivo no contiene datos cifrados")
            }

            // Validación defensiva: ciphertext demasiado corto (mínimo 16 bytes para auth tag)
            if (ciphertext.size < 16) {
                throw BackupEncryptionException("Ciphertext demasiado corto (mínimo 16 bytes para auth tag)")
            }

            // Derivar clave desde contraseña con salt del header
            val secretKey = keyDerivationService.deriveKey(password, header.salt, header.iterations)

            // Inicializar cipher para descifrado
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, header.iv)
            val keySpec = SecretKeySpec(secretKey.encoded, KEY_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)

            // Autenticar header como AAD
            cipher.updateAAD(headerBytes)

            // Descifrar datos (valida integridad mediante GCM tag)
            val plaintext = cipher.doFinal(ciphertext)

            // Limpieza defensiva
            Arrays.fill(secretKey.encoded, 0.toByte())
            Arrays.fill(header.salt, 0.toByte())
            Arrays.fill(header.iv, 0.toByte())

            plaintext
        } catch (e: BackupEncryptionException) {
            throw e
        } catch (e: BackupCodecException) {
            throw BackupEncryptionException("Formato de archivo inválido", e)
        } catch (e: Exception) {
            throw BackupEncryptionException("Error al descifrar backup", e)
        }
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
        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)
        return iv
    }
}
