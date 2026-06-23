package com.myfinances.di

import com.myfinances.core.security.backup.BackupEncryptionManager
import com.myfinances.data.backup.BackupJsonSerializer
import com.myfinances.data.backup.BackupSchemaValidator
import com.myfinances.data.backup.BackupService
import com.myfinances.data.backup.BackupServiceImpl
import com.myfinances.data.backup.DataExporter
import com.myfinances.data.backup.DataImporter
import com.myfinances.data.backup.RoomDataExporter
import com.myfinances.data.backup.RoomDataImporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para la infraestructura de exportación/importación de backups.
 *
 * Provee las dependencias necesarias para:
 * - Exportar datos desde Room a BackupData
 * - Importar datos desde BackupData a Room
 * - Serializar/deserializar BackupData a/desde JSON
 * - Validar BackupData
 * - Orquestar el flujo completo de backup (BackupService)
 *
 * Características:
 * - Todas las dependencias son singleton
 * - Portable a Android y Desktop JVM
 * - Constructor injection para testabilidad
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupDataModule {

    /**
     * Provee BackupSchemaValidator como singleton.
     *
     * Este validador es stateless y puede ser compartido.
     */
    @Provides
    @Singleton
    fun provideBackupSchemaValidator(): BackupSchemaValidator {
        return BackupSchemaValidator()
    }

    /**
     * Provee BackupJsonSerializer como singleton.
     *
     * Este serializer tiene configuración estática y puede ser compartido.
     */
    @Provides
    @Singleton
    fun provideBackupJsonSerializer(validator: BackupSchemaValidator): BackupJsonSerializer {
        return BackupJsonSerializer(validator)
    }

    /**
     * Provee RoomDataExporter como implementación de DataExporter.
     *
     * Este exporter depende de todos los DAOs y del validador.
     */
    @Provides
    @Singleton
    fun provideRoomDataExporter(
        userDao: com.myfinances.data.local.dao.UserDao,
        userSettingsDao: com.myfinances.data.local.dao.UserSettingsDao,
        categoryDao: com.myfinances.data.local.dao.CategoryDao,
        accountDao: com.myfinances.data.local.dao.AccountDao,
        loanDao: com.myfinances.data.local.dao.LoanDao,
        transactionDao: com.myfinances.data.local.dao.TransactionDao,
        transferDao: com.myfinances.data.local.dao.TransferDao,
        budgetDao: com.myfinances.data.local.dao.BudgetDao,
        goalDao: com.myfinances.data.local.dao.GoalDao,
        loanPaymentDao: com.myfinances.data.local.dao.LoanPaymentDao,
        loanMovementDao: com.myfinances.data.local.dao.LoanMovementDao,
        exchangeRateDao: com.myfinances.data.local.dao.ExchangeRateDao,
        validator: BackupSchemaValidator
    ): DataExporter {
        return RoomDataExporter(
            userDao = userDao,
            userSettingsDao = userSettingsDao,
            categoryDao = categoryDao,
            accountDao = accountDao,
            loanDao = loanDao,
            transactionDao = transactionDao,
            transferDao = transferDao,
            budgetDao = budgetDao,
            goalDao = goalDao,
            loanPaymentDao = loanPaymentDao,
            loanMovementDao = loanMovementDao,
            exchangeRateDao = exchangeRateDao,
            validator = validator
        )
    }

    /**
     * Provee RoomDataImporter como implementación de DataImporter.
     *
     * Este importer depende de la base de datos, todos los DAOs y del validador.
     */
    @Provides
    @Singleton
    fun provideRoomDataImporter(
        database: com.myfinances.data.local.AppDatabase,
        userDao: com.myfinances.data.local.dao.UserDao,
        userSettingsDao: com.myfinances.data.local.dao.UserSettingsDao,
        categoryDao: com.myfinances.data.local.dao.CategoryDao,
        accountDao: com.myfinances.data.local.dao.AccountDao,
        loanDao: com.myfinances.data.local.dao.LoanDao,
        transactionDao: com.myfinances.data.local.dao.TransactionDao,
        transferDao: com.myfinances.data.local.dao.TransferDao,
        budgetDao: com.myfinances.data.local.dao.BudgetDao,
        goalDao: com.myfinances.data.local.dao.GoalDao,
        loanPaymentDao: com.myfinances.data.local.dao.LoanPaymentDao,
        loanMovementDao: com.myfinances.data.local.dao.LoanMovementDao,
        exchangeRateDao: com.myfinances.data.local.dao.ExchangeRateDao,
        validator: BackupSchemaValidator
    ): DataImporter {
        return RoomDataImporter(
            database = database,
            userDao = userDao,
            userSettingsDao = userSettingsDao,
            categoryDao = categoryDao,
            accountDao = accountDao,
            loanDao = loanDao,
            transactionDao = transactionDao,
            transferDao = transferDao,
            budgetDao = budgetDao,
            goalDao = goalDao,
            loanPaymentDao = loanPaymentDao,
            loanMovementDao = loanMovementDao,
            exchangeRateDao = exchangeRateDao,
            validator = validator
        )
    }

    /**
     * Provee BackupService como implementación de BackupServiceImpl.
     *
     * Este servicio orquesta el flujo completo de backup:
     * - Exportación: Room → BackupData → JSON → Cifrado → Archivo
     * - Importación: Archivo → Descifrado → JSON → BackupData → Room
     */
    @Provides
    @Singleton
    fun provideBackupService(
        dataExporter: DataExporter,
        dataImporter: DataImporter,
        jsonSerializer: BackupJsonSerializer,
        encryptionManager: BackupEncryptionManager
    ): BackupService {
        return BackupServiceImpl(
            dataExporter = dataExporter,
            dataImporter = dataImporter,
            jsonSerializer = jsonSerializer,
            encryptionManager = encryptionManager
        )
    }
}
