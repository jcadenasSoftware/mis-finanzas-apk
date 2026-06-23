package com.myfinances.core.security.backup

/**
 * Interfaz de servicio de cifrado para backups con contraseña.
 *
 * Proporciona abstracción para operaciones de cifrado/descifrado de backups,
 * permitiendo desacoplamiento, testabilidad y compatibilidad futura
 * con Desktop u otras implementaciones.
 *
 * Características:
 * - No depende de Android Keystore (portable a Desktop)
 * - Usa PBKDF2 + AES-256-GCM
 * - Autentica metadatos del header como AAD
 * - Formato de archivo versionado
 */
interface BackupEncryptionService {

    /**
     * Cifra datos de backup con contraseña.
     *
     * El proceso incluye:
     * - Derivar clave desde contraseña con PBKDF2
     * - Generar IV aleatorio
     * - Crear header versionado con metadatos
     * - Autenticar header como AAD
     * - Cifrar datos con AES-256-GCM
     * - Retornar archivo completo (header + ciphertext)
     *
     * @param data Datos en claro a cifrar
     * @param password Contraseña para derivar clave
     * @return Archivo completo cifrado (header + ciphertext)
     * @throws BackupEncryptionException si ocurre un error durante el cifrado
     */
    suspend fun encryptBackup(data: ByteArray, password: String): ByteArray

    /**
     * Descifra datos de backup con contraseña.
     *
     * El proceso incluye:
     * - Decodificar header versionado
     * - Validar magic number y versiones
     * - Derivar clave desde contraseña con PBKDF2
     * - Autenticar header como AAD
     * - Descifrar datos con AES-256-GCM
     * - Validar integridad mediante GCM tag
     *
     * @param encryptedData Archivo completo cifrado (header + ciphertext)
     * @param password Contraseña para derivar clave
     * @return Datos en claro descifrados
     * @throws BackupEncryptionException si ocurre un error durante el descifrado
     */
    suspend fun decryptBackup(encryptedData: ByteArray, password: String): ByteArray
}

/**
 * Excepción lanzada cuando ocurre un error de cifrado/descifrado de backup.
 */
class BackupEncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
