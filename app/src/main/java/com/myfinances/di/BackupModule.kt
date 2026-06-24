package com.jcadenas.xpendz.di

import com.jcadenas.xpendz.core.security.backup.BackupEncryptionManager
import com.jcadenas.xpendz.core.security.backup.BackupEncryptionService
import com.jcadenas.xpendz.core.security.backup.BackupFileCodec
import com.jcadenas.xpendz.core.security.backup.PasswordKeyDerivationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de Hilt para componentes de seguridad de backups.
 *
 * Provee:
 * - PasswordKeyDerivationService (derivación de clave PBKDF2)
 * - BackupFileCodec (serialización de header versionado)
 * - BackupEncryptionManager (implementación concreta)
 * - BackupEncryptionService (interfaz)
 *
 * Separado de SecurityModule para mantener:
 * - Bajo acoplamiento entre cifrado local y cifrado de backups
 * - Alta cohesión (cada módulo maneja su dominio)
 * - Portabilidad (este módulo es portable a Desktop)
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    /**
     * Provee PasswordKeyDerivationService como singleton.
     *
     * Singleton porque:
     * - No mantiene estado mutable
     * - SecureRandom es thread-safe
     * - Evita instanciación repetida
     */
    @Provides
    @Singleton
    fun providePasswordKeyDerivationService(): PasswordKeyDerivationService {
        return PasswordKeyDerivationService()
    }

    /**
     * Provee BackupFileCodec como singleton.
     *
     * Singleton porque:
     * - Stateless (sin estado mutable)
     * - Evita instanciación repetida
     */
    @Provides
    @Singleton
    fun provideBackupFileCodec(): BackupFileCodec {
        return BackupFileCodec()
    }

    /**
     * Provee la implementación concreta de BackupEncryptionService.
     *
     * Singleton porque:
     * - No mantiene estado mutable
     * - SecureRandom es thread-safe
     * - Es seguro para uso concurrente
     */
    @Provides
    @Singleton
    fun provideBackupEncryptionManager(
        keyDerivationService: PasswordKeyDerivationService,
        fileCodec: BackupFileCodec
    ): BackupEncryptionManager {
        return BackupEncryptionManager(keyDerivationService, fileCodec)
    }

    /**
     * Provee la interfaz BackupEncryptionService.
     *
     * Los consumidores deben depender de la interfaz, no de la
     * implementación concreta, para:
     * - Desacoplamiento
     * - Testabilidad (fácil mocking)
     * - Compatibilidad futura con Desktop
     */
    @Provides
    @Singleton
    fun provideBackupEncryptionService(
        backupEncryptionManager: BackupEncryptionManager
    ): BackupEncryptionService {
        return backupEncryptionManager
    }
}
