package com.jcadenas.xpendz.core.security

import java.io.ByteArrayOutputStream

/**
 * Codec para serialización/deserialización de payloads cifrados versionados.
 *
 * Formato del payload (bytes):
 * ┌─────────────┬──────────────┬──────────┬───────────────┬──────────────┐
 * │ payloadVer  │ keyVersion   │ ivLength │ IV            │ ciphertext   │
 * │ (1 byte)    │ (1 byte)     │ (2 bytes)│ (12 bytes)    │ (variable)   │
 * └─────────────┴──────────────┴──────────┴───────────────┴──────────────┘
 *
 * Metadatos incluidos:
 * - payloadVersion: versión del formato del payload (actualmente 1)
 * - keyVersion: versión de la clave usada (actualmente 1)
 * - iv: nonce/IV usado en AES-GCM (12 bytes)
 * - ciphertext: datos cifrados con auth tag incluido
 *
 * Este diseño permite:
 * - Rotación de claves futura
 * - Evolución del formato del payload
 * - Mantener compatibilidad hacia atrás
 *
 * NOTA SOBRE VALIDACIÓN DE VERSIONES:
 * Este codec es responsable de decodificar el payload y extraer las versiones.
 * La validación de si las versiones son soportadas está en EncryptionManager.
 * Esta separación permite:
 * - Reutilizar el codec en contextos donde se soporten múltiples versiones
 * - Evitar acoplar el codec a la lógica de negocio de versiones
 * - Facilitar pruebas unitarias del codec sin dependencias de la capa de cifrado
 */
class CipherPayloadCodec {

    companion object {
        private const val CURRENT_PAYLOAD_VERSION: Byte = 1
        private const val CURRENT_KEY_VERSION: Byte = 1
        private const val GCM_IV_LENGTH = 12
        private const val HEADER_LENGTH = 4 // 1 + 1 + 2
    }

    /**
     * Codifica los componentes del cifrado en un payload versionado.
     *
     * @param iv Vector de inicialización (nonce)
     * @param ciphertext Datos cifrados (incluye auth tag en AES-GCM)
     * @return Payload completo con metadatos
     */
    fun encode(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.size == GCM_IV_LENGTH) {
            "IV debe tener exactamente $GCM_IV_LENGTH bytes (GCM standard)"
        }

        ByteArrayOutputStream().use { output ->
            // Header: payloadVersion (1 byte)
            output.write(CURRENT_PAYLOAD_VERSION.toInt())

            // Header: keyVersion (1 byte)
            output.write(CURRENT_KEY_VERSION.toInt())

            // Header: ivLength (2 bytes, big-endian)
            output.write(iv.size shr 8)
            output.write(iv.size and 0xFF)

            // IV
            output.write(iv)

            // Ciphertext (incluye auth tag)
            output.write(ciphertext)

            return output.toByteArray()
        }
    }

    /**
     * Decodifica un payload versionado en sus componentes.
     *
     * @param payload Payload completo con metadatos
     * @return CipherPayload con los componentes decodificados
     * @throws PayloadCodecException si el formato es inválido
     */
    fun decode(payload: ByteArray): CipherPayload {
        // Validación defensiva: payload vacío
        if (payload.isEmpty()) {
            throw PayloadCodecException("Payload vacío")
        }

        // Validación defensiva: payload demasiado corto para header
        if (payload.size < HEADER_LENGTH) {
            throw PayloadCodecException("Payload demasiado corto para contener header")
        }

        var offset = 0

        // Leer payloadVersion
        val payloadVersion = payload[offset].toInt() and 0xFF
        offset++

        // Leer keyVersion
        val keyVersion = payload[offset].toInt() and 0xFF
        offset++

        // Leer ivLength (big-endian)
        val ivLength = ((payload[offset].toInt() and 0xFF) shl 8) or
                      (payload[offset + 1].toInt() and 0xFF)
        offset += 2

        // Validación IV length
        if (ivLength != GCM_IV_LENGTH) {
            throw PayloadCodecException(
                "IV length inválido: esperado $GCM_IV_LENGTH, recibido $ivLength"
            )
        }

        // Validación defensiva: payload demasiado corto para IV
        if (payload.size < offset + ivLength) {
            throw PayloadCodecException("Payload demasiado corto para contener IV")
        }

        // Extraer IV
        val iv = payload.copyOfRange(offset, offset + ivLength)
        offset += ivLength

        // Validación defensiva: payload sin ciphertext
        if (offset >= payload.size) {
            throw PayloadCodecException("Payload no contiene ciphertext")
        }

        // Validación defensiva: ciphertext demasiado corto (GCM requiere al menos tag de 16 bytes)
        val ciphertextLength = payload.size - offset
        if (ciphertextLength < 16) {
            throw PayloadCodecException(
                "Ciphertext demasiado corto: mínimo 16 bytes (auth tag), recibido $ciphertextLength"
            )
        }

        // Extraer ciphertext
        val ciphertext = payload.copyOfRange(offset, payload.size)

        return CipherPayload(
            payloadVersion = payloadVersion,
            keyVersion = keyVersion,
            iv = iv,
            ciphertext = ciphertext
        )
    }

    /**
     * Representación de los componentes de un payload cifrado.
     */
    data class CipherPayload(
        val payloadVersion: Int,
        val keyVersion: Int,
        val iv: ByteArray,
        val ciphertext: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CipherPayload

            if (payloadVersion != other.payloadVersion) return false
            if (keyVersion != other.keyVersion) return false
            if (!iv.contentEquals(other.iv)) return false
            if (!ciphertext.contentEquals(other.ciphertext)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = payloadVersion
            result = 31 * result + keyVersion
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            return result
        }
    }
}

/**
 * Excepción lanzada cuando ocurre un error en la codificación/decodificación del payload.
 */
class PayloadCodecException(message: String, cause: Throwable? = null) : Exception(message, cause)
