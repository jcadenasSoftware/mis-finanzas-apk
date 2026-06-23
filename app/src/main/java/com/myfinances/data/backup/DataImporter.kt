package com.myfinances.data.backup

/**
 * Contrato para importación de datos desde BackupData a Room.
 *
 * Esta interfaz define la operación de restauración de datos desde una estructura
 * BackupData hacia la base de datos Room.
 *
 * Responsabilidades de la implementación:
 * - Validar BackupData antes de importar
 * - Insertar entidades en orden topológico de dependencias
 * - Manejar Foreign Keys correctamente
 * - Ejecutar dentro de transacción para atomicidad
 * - Limpiar datos existentes según estrategia (REPLACE_ALL o MERGE)
 *
 * Características:
 * - Operación suspend para no bloquear el hilo principal
 * - Portable a Android y Desktop JVM
 * - Sin dependencias de Android-specific APIs
 *
 * Estrategia recomendada:
 * - REPLACE_ALL para todas las entidades excepto BudgetEntity y ExchangeRateEntity
 * - MERGE para BudgetEntity (por unique constraint natural)
 * - MERGE para ExchangeRateEntity (por unique constraint natural)
 *
 * @param backupData Datos de backup a restaurar
 * @throws BackupImportException si ocurre un error durante la importación
 * @throws SchemaValidationException si el schemaVersion no es soportado o hay inconsistencias
 */
interface DataImporter {
    /**
     * Restaura los datos desde BackupData hacia Room.
     *
     * El proceso debe:
     * 1. Validar BackupData con BackupSchemaValidator
     * 2. Iniciar transacción Room
     * 3. Borrar datos existentes según estrategia (REPLACE_ALL)
     *    - Orden inverso de dependencias
     * 4. Insertar entidades en orden topológico:
     *    - UserEntity
     *    - UserSettingsEntity
     *    - CategoryEntity
     *    - AccountEntity
     *    - LoanEntity
     *    - TransactionEntity
     *    - TransferEntity
     *    - BudgetEntity (MERGE si existe, INSERT si no)
     *    - GoalEntity
     *    - LoanPaymentEntity
     *    - LoanMovementEntity
     *    - ExchangeRateEntity (MERGE si existe, INSERT si no)
     * 5. Confirmar transacción
     *
     * Nota: La transacción debe ser atómica. Si falla cualquier paso,
     * se debe hacer rollback completo.
     *
     * @param backupData Datos de backup a restaurar
     * @throws BackupImportException si ocurre un error durante la importación
     * @throws SchemaValidationException si el schemaVersion no es soportado o hay inconsistencias
     */
    suspend fun restore(userUid: String, backupData: BackupData)
}
