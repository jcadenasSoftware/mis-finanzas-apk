package com.jcadenas.xpendz.core.security

/**
 * Interfaz de servicio de cifrado.
 *
 * Proporciona abstracción para operaciones de cifrado/descifrado,
 * permitiendo desacoplamiento, testabilidad y compatibilidad futura
 * con Desktop u otras implementaciones.
 */
interface EncryptionService {

    /**
     * Cifra datos binarios.
     *
     * @param plaintext Datos en claro a cifrar
     * @return Datos cifrados con metadatos (version + IV + ciphertext + tag)
     * @throws EncryptionException Si ocurre un error durante el cifrado
     */
    suspend fun encrypt(plaintext: ByteArray): ByteArray

    /**
     * Descifra datos binarios.
     *
     * @param ciphertext Datos cifrados con metadatos
     * @return Datos en claro
     * @throws EncryptionException Si ocurre un error durante el descifrado o la validación
     */
    suspend fun decrypt(ciphertext: ByteArray): ByteArray

    /**
     * Cifra una cadena de texto.
     *
     * @param plaintext Texto en claro a cifrar
     * @return Datos cifrados con metadatos
     * @throws EncryptionException Si ocurre un error durante el cifrado
     */
    suspend fun encrypt(plaintext: String): ByteArray

    /**
     * Descifra una cadena de texto.
     *
     * @param ciphertext Datos cifrados con metadatos
     * @return Texto en claro
     * @throws EncryptionException Si ocurre un error durante el descifrado o la validación
     */
    suspend fun decryptToString(ciphertext: ByteArray): String
}

/**
 * Excepción lanzada cuando ocurre un error de cifrado/descifrado.
 */
class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
