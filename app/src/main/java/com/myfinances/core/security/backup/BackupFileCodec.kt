package com.myfinances.core.security.backup

import com.myfinances.core.security.backup.BackupCryptoConstants.ENCRYPTION_VERSION
import com.myfinances.core.security.backup.BackupCryptoConstants.FIXED_HEADER_LENGTH
import com.myfinances.core.security.backup.BackupCryptoConstants.FILE_VERSION
import com.myfinances.core.security.backup.BackupCryptoConstants.IV_LENGTH
import com.myfinances.core.security.backup.BackupCryptoConstants.KDF_VERSION
import com.myfinances.core.security.backup.BackupCryptoConstants.MAGIC_NUMBER
import com.myfinances.core.security.backup.BackupCryptoConstants.MAGIC_NUMBER_LENGTH
import com.myfinances.core.security.backup.BackupCryptoConstants.MAX_SUPPORTED_ITERATIONS
import com.myfinances.core.security.backup.BackupCryptoConstants.MIN_SUPPORTED_ITERATIONS
import com.myfinances.core.security.backup.BackupCryptoConstants.SALT_LENGTH
import com.myfinances.core.security.backup.BackupCryptoConstants.SUPPORTED_PBKDF2_ITERATIONS
import java.io.ByteArrayOutputStream

/**
 * Codec para serialización/deserialización del header de archivos de backup.
 *
 * Formato del header (bytes):
 * ┌─────────────┬──────────┬──────────────────┬──────────┬─────────────┬──────────┬──────────┬──────────┐
 * │ magicNumber │ fileVer  │ encryptionVer    │ kdfVer   │ iterations │ saltLen  │ ivLen    │ salt     │ iv       │
 * │ (4 bytes)   │ (1 byte) │ (1 byte)         │ (1 byte) │ (4 bytes)  │ (1 byte) │ (1 byte) │ (16 bytes)│ (12 bytes)│
 * └─────────────┴──────────┴──────────────────┴──────────┴─────────────┴──────────┴──────────┴──────────┴──────────┘
 *
 * Metadatos incluidos:
 * - magicNumber: identificador de archivo "XENC"
 * - fileVersion: versión del formato (actualmente 1)
 * - encryptionVersion: versión del algoritmo de cifrado (1 = AES-GCM)
 * - kdfVersion: versión del KDF (1 = PBKDF2-HMAC-SHA256)
 * - iterations: número de iteraciones PBKDF2
 * - salt: salt único para este backup
 * - iv: nonce para AES-GCM
 *
 * Este diseño permite:
 * - Evolución del formato del archivo
 * - Cambios en algoritmos criptográficos
 * - Validación de compatibilidad antes de procesar
 * - Portabilidad entre Android y Desktop
 */
class BackupFileCodec {

    /**
     * Representación de los metadatos del header de backup.
     */
    data class BackupHeader(
        val magicNumber: String,
        val fileVersion: Int,
        val encryptionVersion: Int,
        val kdfVersion: Int,
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BackupHeader

            if (magicNumber != other.magicNumber) return false
            if (fileVersion != other.fileVersion) return false
            if (encryptionVersion != other.encryptionVersion) return false
            if (kdfVersion != other.kdfVersion) return false
            if (iterations != other.iterations) return false
            if (!salt.contentEquals(other.salt)) return false
            if (!iv.contentEquals(other.iv)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = magicNumber.hashCode()
            result = 31 * result + fileVersion
            result = 31 * result + encryptionVersion
            result = 31 * result + kdfVersion
            result = 31 * result + iterations
            result = 31 * result + salt.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }
    }

    /**
     * Codifica los metadatos del header en un ByteArray.
     *
     * @param header Metadatos del header
     * @return Header serializado en bytes
     * @throws BackupCodecException si el formato es inválido
     */
    fun encodeHeader(header: BackupHeader): ByteArray {
        if (header.magicNumber != MAGIC_NUMBER) {
            throw BackupCodecException("Magic number debe ser '$MAGIC_NUMBER'")
        }

        if (header.salt.size != SALT_LENGTH) {
            throw BackupCodecException("Salt debe tener exactamente $SALT_LENGTH bytes")
        }

        if (header.iv.size != IV_LENGTH) {
            throw BackupCodecException("IV debe tener exactamente $IV_LENGTH bytes")
        }

        ByteArrayOutputStream().use { output ->
            // Magic number (4 bytes)
            output.write(header.magicNumber.toByteArray(Charsets.US_ASCII))

            // File version (1 byte)
            output.write(header.fileVersion)

            // Encryption version (1 byte)
            output.write(header.encryptionVersion)

            // KDF version (1 byte)
            output.write(header.kdfVersion)

            // Iterations (4 bytes, big-endian)
            output.write(header.iterations shr 24)
            output.write((header.iterations shr 16) and 0xFF)
            output.write((header.iterations shr 8) and 0xFF)
            output.write(header.iterations and 0xFF)

            // Salt length (1 byte)
            output.write(header.salt.size)

            // IV length (1 byte)
            output.write(header.iv.size)

            // Salt (16 bytes)
            output.write(header.salt)

            // IV (12 bytes)
            output.write(header.iv)

            return output.toByteArray()
        }
    }

    /**
     * Decodifica un header desde un ByteArray.
     *
     * @param headerBytes Header serializado en bytes
     * @return Metadatos del header decodificados
     * @throws BackupCodecException si el formato es inválido
     */
    fun decodeHeader(headerBytes: ByteArray): BackupHeader {
        // Validación defensiva: header vacío
        if (headerBytes.isEmpty()) {
            throw BackupCodecException("Header vacío")
        }

        // Validación defensiva: header demasiado corto para magic number
        if (headerBytes.size < MAGIC_NUMBER_LENGTH) {
            throw BackupCodecException("Header demasiado corto para contener magic number")
        }

        var offset = 0

        // Leer magic number (4 bytes)
        val magicNumber = String(headerBytes.copyOfRange(offset, offset + MAGIC_NUMBER_LENGTH), Charsets.US_ASCII)
        offset += MAGIC_NUMBER_LENGTH

        // Validar magic number
        if (magicNumber != MAGIC_NUMBER) {
            throw BackupCodecException(
                "Magic number inválido: esperado '$MAGIC_NUMBER', recibido '$magicNumber'"
            )
        }

        // Validación defensiva: header demasiado corto para campos fijos
        if (headerBytes.size < FIXED_HEADER_LENGTH) {
            throw BackupCodecException("Header demasiado corto para contener campos fijos")
        }

        // Leer file version (1 byte)
        val fileVersion = headerBytes[offset].toInt() and 0xFF
        offset++

        // Validar file version
        if (fileVersion != FILE_VERSION.toInt()) {
            throw BackupCodecException(
                "Versión de archivo no soportada: esperado $FILE_VERSION, recibido $fileVersion"
            )
        }

        // Leer encryption version (1 byte)
        val encryptionVersion = headerBytes[offset].toInt() and 0xFF
        offset++

        // Validar encryption version
        if (encryptionVersion != ENCRYPTION_VERSION.toInt()) {
            throw BackupCodecException(
                "Versión de cifrado no soportada: esperado $ENCRYPTION_VERSION, recibido $encryptionVersion"
            )
        }

        // Leer kdf version (1 byte)
        val kdfVersion = headerBytes[offset].toInt() and 0xFF
        offset++

        // Validar kdf version
        if (kdfVersion != KDF_VERSION.toInt()) {
            throw BackupCodecException(
                "Versión de KDF no soportada: esperada $KDF_VERSION, recibida $kdfVersion"
            )
        }

        // Leer iterations (4 bytes, big-endian)
        val iterations = ((headerBytes[offset].toInt() and 0xFF) shl 24) or
                       ((headerBytes[offset + 1].toInt() and 0xFF) shl 16) or
                       ((headerBytes[offset + 2].toInt() and 0xFF) shl 8) or
                       (headerBytes[offset + 3].toInt() and 0xFF)
        offset += 4

        // Validar iterations
        if (iterations <= 0) {
            throw BackupCodecException("Iteraciones inválidas: debe ser mayor que 0")
        }

        // Validar iterations contra valores soportados para prevenir DoS
        if (iterations != SUPPORTED_PBKDF2_ITERATIONS) {
            throw BackupCodecException(
                "Unsupported PBKDF2 iteration count: esperado $SUPPORTED_PBKDF2_ITERATIONS, recibido $iterations"
            )
        }

        // Leer salt length (1 byte)
        val saltLength = headerBytes[offset].toInt() and 0xFF
        offset++

        // Validar salt length
        if (saltLength != SALT_LENGTH) {
            throw BackupCodecException(
                "Longitud de salt inválida: esperada $SALT_LENGTH, recibida $saltLength"
            )
        }

        // Leer iv length (1 byte)
        val ivLength = headerBytes[offset].toInt() and 0xFF
        offset++

        // Validar iv length
        if (ivLength != IV_LENGTH) {
            throw BackupCodecException(
                "Longitud de IV inválida: esperada $IV_LENGTH, recibida $ivLength"
            )
        }

        // Validación defensiva: header demasiado corto para salt + iv
        if (headerBytes.size < offset + saltLength + ivLength) {
            throw BackupCodecException("Header demasiado corto para contener salt e IV")
        }

        // Extraer salt
        val salt = headerBytes.copyOfRange(offset, offset + saltLength)
        offset += saltLength

        // Extraer iv
        val iv = headerBytes.copyOfRange(offset, offset + ivLength)
        offset += ivLength

        return BackupHeader(
            magicNumber = magicNumber,
            fileVersion = fileVersion,
            encryptionVersion = encryptionVersion,
            kdfVersion = kdfVersion,
            iterations = iterations,
            salt = salt,
            iv = iv
        )
    }

    /**
     * Valida que un header sea válido sin lanzar excepción.
     *
     * @param headerBytes Header serializado en bytes
     * @return true si el header es válido, false en caso contrario
     */
    fun isValidHeader(headerBytes: ByteArray): Boolean {
        return try {
            decodeHeader(headerBytes)
            true
        } catch (e: BackupCodecException) {
            false
        }
    }
}

/**
 * Excepción lanzada cuando ocurre un error en la codificación/decodificación del header.
 */
class BackupCodecException(message: String, cause: Throwable? = null) : Exception(message, cause)
