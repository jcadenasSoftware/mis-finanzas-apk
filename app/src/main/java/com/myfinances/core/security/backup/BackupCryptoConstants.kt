package com.jcadenas.xpendz.core.security.backup

/**
 * Constantes criptográficas centralizadas para backups cifrados con contraseña.
 *
 * Estas constantes son compartidas entre Android y Desktop para garantizar
 * compatibilidad total de backups entre plataformas.
 *
 * ESTRATEGIA DE COMPATIBILIDAD FUTURA:
 *
 * Versiones actuales soportadas:
 * - fileVersion: 1
 * - encryptionVersion: 1 (AES-256-GCM)
 * - kdfVersion: 1 (PBKDF2WithHmacSHA256)
 *
 * Migración futura de versiones:
 * - Al cambiar fileVersion, implementar lógica de migración en BackupFileCodec
 * - Al cambiar encryptionVersion, soportar versiones anteriores en BackupEncryptionManager
 * - Al cambiar kdfVersion, soportar versiones anteriores en PasswordKeyDerivationService
 *
 * Política de soporte:
 * - Mantener soporte para versiones anteriores por al menos 2 versiones mayores
 * - Documentar cambios en CHANGELOG
 * - Implementar migraciones automáticas cuando sea posible
 * - Versiones muy antiguas pueden requerir migración manual o ser no soportadas
 */
object BackupCryptoConstants {

    /**
     * Magic number para identificar archivos de backup de Xpendz.
     * "XENC" = Xpendz Encrypted
     */
    const val MAGIC_NUMBER = "XENC"

    /**
     * Versión actual del formato de archivo de backup.
     */
    const val FILE_VERSION: Byte = 1

    /**
     * Versión del algoritmo de cifrado.
     * 1 = AES-256-GCM
     */
    const val ENCRYPTION_VERSION: Byte = 1

    /**
     * Versión del algoritmo de derivación de clave.
     * 1 = PBKDF2WithHmacSHA256
     */
    const val KDF_VERSION: Byte = 1

    /**
     * Número de iteraciones para PBKDF2.
     *
     * 310,000 iteraciones es un valor conservador que:
     * - Cumple recomendaciones OWASP 2021 (mínimo 310,000 para SHA256)
     * - Balancea seguridad y rendimiento en dispositivos móviles
     * - Es configurable para ajustes futuros
     */
    const val PBKDF2_ITERATIONS = 310_000

    /**
     * Iteraciones PBKDF2 soportadas para la versión actual del formato.
     *
     * Para la versión 1 del formato, solo se acepta exactamente 310,000 iteraciones.
     * Esto previene ataques de DoS por iteraciones arbitrarias en el header.
     */
    const val SUPPORTED_PBKDF2_ITERATIONS = 310_000

    /**
     * Mínimo de iteraciones PBKDF2 soportadas.
     *
     * Para la versión 1, igual al valor soportado.
     */
    const val MIN_SUPPORTED_ITERATIONS = 310_000

    /**
     * Máximo de iteraciones PBKDF2 soportadas.
     *
     * Para la versión 1, igual al valor soportado.
     * Esto previene ataques de DoS por iteraciones excesivas.
     */
    const val MAX_SUPPORTED_ITERATIONS = 310_000

    /**
     * Longitud del salt para PBKDF2 en bytes.
     * 16 bytes = 128 bits
     */
    const val SALT_LENGTH = 16

    /**
     * Longitud de la clave derivada en bytes.
     * 32 bytes = 256 bits (AES-256)
     */
    const val KEY_LENGTH = 32

    /**
     * Longitud del IV para AES-GCM en bytes.
     * 12 bytes es el tamaño recomendado para máxima seguridad
     */
    const val IV_LENGTH = 12

    /**
     * Longitud del tag de autenticación GCM en bits.
     * 128 bits es el tamaño recomendado para máxima seguridad
     */
    const val GCM_TAG_LENGTH_BITS = 128

    /**
     * Algoritmo de derivación de clave.
     */
    const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"

    /**
     * Algoritmo de cifrado.
     */
    const val ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"

    /**
     * Algoritmo de clave.
     */
    const val KEY_ALGORITHM = "AES"

    /**
     * Longitud del magic number en bytes.
     */
    const val MAGIC_NUMBER_LENGTH = 4

    /**
     * Longitud del header fijo (sin salt ni IV) en bytes.
     * magicNumber (4) + fileVersion (1) + encryptionVersion (1) + kdfVersion (1) +
     * iterations (4) + saltLength (1) + ivLength (1) = 13 bytes
     */
    const val FIXED_HEADER_LENGTH = 13

    /**
     * Longitud total del header en bytes.
     * fixedHeader (13) + salt (16) + iv (12) = 41 bytes
     */
    const val TOTAL_HEADER_LENGTH = FIXED_HEADER_LENGTH + SALT_LENGTH + IV_LENGTH
}
