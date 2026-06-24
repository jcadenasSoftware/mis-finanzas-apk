package com.jcadenas.xpendz.di

import android.content.ContentResolver
import android.content.Context
import com.jcadenas.xpendz.ui.backup.BackupFileManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para la capa de UI de backups.
 *
 * Provee las dependencias necesarias para:
 * - BackupFileManager (acceso a SAF mediante ContentResolver)
 *
 * Características:
 * - BackupFileManager es singleton para reutilización
 * - Instalado en SingletonComponent para acceso global
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupUiModule {

    /**
     * Provee BackupFileManager como singleton.
     *
     * Este administrador encapsula el acceso a Storage Access Framework
     * y usa ContentResolver para abrir streams desde/ hacia Uris.
     */
    @Provides
    @Singleton
    fun provideBackupFileManager(@ApplicationContext context: Context): BackupFileManager {
        return BackupFileManager(context.contentResolver)
    }
}
