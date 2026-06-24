package com.jcadenas.xpendz.data.backup

/**
 * Contrato para exportación de datos de Room a BackupData.
 *
 * Esta interfaz define la operación de exportación de todos los datos del usuario
 * desde la base de datos Room hacia una estructura BackupData serializable.
 *
 * Responsabilidades de la implementación:
 * - Consultar todas las entidades Room en orden topológico
 * - Construir BackupMetadata con metadata actual
 * - Mantener consistencia de Foreign Keys
 * - Manejar errores de exportación
 *
 * Características:
 * - Operación suspend para no bloquear el hilo principal
 * - Portable a Android y Desktop JVM
 * - Sin dependencias de Android-specific APIs
 *
 * @property userUid UID del usuario cuyos datos se exportarán
 * @return BackupData con todas las entidades del usuario
 * @throws BackupExportException si ocurre un error durante la exportación
 */
interface DataExporter {
    /**
     * Exporta todos los datos del usuario desde Room a BackupData.
     *
     * El proceso debe:
     * 1. Validar que el usuario existe
     * 2. Exportar UserEntity
     * 3. Exportar UserSettingsEntity
     * 4. Exportar CategoryEntity (todas, incluyendo jerarquía)
     * 5. Exportar AccountEntity (todas)
     * 6. Exportar LoanEntity (todas)
     * 7. Exportar TransactionEntity (todas)
     * 8. Exportar TransferEntity (todas)
     * 9. Exportar BudgetEntity (todas)
     * 10. Exportar GoalEntity (todas)
     * 11. Exportar LoanPaymentEntity (todas)
     * 12. Exportar LoanMovementEntity (todas)
     * 13. Exportar ExchangeRateEntity (todas)
     * 14. Construir BackupMetadata con metadata actual
     * 15. Retornar BackupData completo
     *
     * @param userUid UID del usuario de Firebase
     * @return BackupData con todas las entidades del usuario
     * @throws BackupExportException si el usuario no existe o ocurre un error
     */
    suspend fun export(userUid: String): BackupData
}
