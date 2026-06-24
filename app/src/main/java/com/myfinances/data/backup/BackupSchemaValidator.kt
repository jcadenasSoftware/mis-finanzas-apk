package com.jcadenas.xpendz.data.backup

/**
 * Validador del esquema de backup.
 *
 * Responsabilidades:
 * - Validar schemaVersion del backup
 * - Validar consistencia entre metadata y datos
 * - Validar referencias internas simples
 * - Validar integridad estructural básica
 *
 * Características:
 * - Independiente de Room (no valida contra base de datos)
 * - Portable a Android y Desktop JVM
 * - Validaciones defensivas antes de importar
 * - Lanza SchemaValidationException ante inconsistencias
 *
 * No valida:
 * - Foreign Keys contra Room (eso es responsabilidad de DataImporter)
 * - Datos duplicados (eso es responsabilidad de Room constraints)
 * - Lógica de negocio (eso es responsabilidad de la capa de dominio)
 */
class BackupSchemaValidator {

    /**
     * Valida un BackupData completo.
     *
     * Ejecuta todas las validaciones en orden:
     * 1. Valida schemaVersion
     * 2. Valida consistencia de usuario
     * 3. Valida que las listas no sean nulas
     * 4. Valida referencias internas (parentId, accountId, linkedTransactionId)
     *
     * @param backupData Datos de backup a validar
     * @throws SchemaValidationException si alguna validación falla
     */
    fun validate(backupData: BackupData) {
        validateSchemaVersion(backupData.metadata.schemaVersion)
        validateUserConsistency(backupData)
        validateListsNotNull(backupData)
        validateCategoryParentIds(backupData)
        validateAccountReferences(backupData)
        validateLinkedTransactionReferences(backupData)
    }

    /**
     * Valida que la versión del esquema sea soportada.
     *
     * @param schemaVersion Versión del esquema del backup
     * @throws SchemaValidationException si la versión no es soportada
     */
    private fun validateSchemaVersion(schemaVersion: Int) {
        if (schemaVersion != BackupMetadata.CURRENT_BACKUP_SCHEMA_VERSION) {
            throw SchemaValidationException(
                "Versión de esquema no soportada: $schemaVersion (esperada: ${BackupMetadata.CURRENT_BACKUP_SCHEMA_VERSION})"
            )
        }
    }

    /**
     * Valida que el userUid en metadata coincida con el user.uid.
     *
     * @param backupData Datos de backup a validar
     * @throws SchemaValidationException si los UIDs no coinciden
     */
    private fun validateUserConsistency(backupData: BackupData) {
        if (backupData.user.uid != backupData.metadata.userUid) {
            throw SchemaValidationException(
                "Inconsistencia de usuario: metadata.userUid (${backupData.metadata.userUid}) " +
                "no coincide con user.uid (${backupData.user.uid})"
            )
        }

        if (backupData.userSettings.userUid != backupData.user.uid) {
            throw SchemaValidationException(
                "Inconsistencia de usuario: userSettings.userUid (${backupData.userSettings.userUid}) " +
                "no coincide con user.uid (${backupData.user.uid})"
            )
        }
    }

    /**
     * Valida que todas las listas no sean nulas.
     *
     * Las listas pueden estar vacías, pero nunca nulas.
     *
     * @param backupData Datos de backup a validar
     * @throws SchemaValidationException si alguna lista es nula
     */
    private fun validateListsNotNull(backupData: BackupData) {
        // Las listas en BackupData tienen default = emptyList(), pero validamos por seguridad
        val lists = mapOf(
            "categories" to backupData.categories,
            "accounts" to backupData.accounts,
            "loans" to backupData.loans,
            "transactions" to backupData.transactions,
            "transfers" to backupData.transfers,
            "budgets" to backupData.budgets,
            "goals" to backupData.goals,
            "loanPayments" to backupData.loanPayments,
            "loanMovements" to backupData.loanMovements,
            "exchangeRates" to backupData.exchangeRates
        )

        lists.forEach { (name, list) ->
            if (list == null) {
                throw SchemaValidationException("Lista '$name' es nula (debe ser lista vacía si está vacía)")
            }
        }
    }

    /**
     * Valida que los parentId de categorías referencien categorías válidas.
     *
     * Un parentId debe ser null o referenciar una categoría en la misma lista.
     *
     * @param backupData Datos de backup a validar
     * @throws SchemaValidationException si algún parentId es inválido
     */
    private fun validateCategoryParentIds(backupData: BackupData) {
        val categoryIds = backupData.categories.map { it.id }.toSet()

        backupData.categories.forEach { category ->
            category.parentId?.let { parentId ->
                if (parentId !in categoryIds) {
                    throw SchemaValidationException(
                        "Categoría '${category.id}' tiene parentId inválido: '$parentId' " +
                        "(no existe en la lista de categorías)"
                    )
                }

                // Validar que no haya ciclos directos
                if (parentId == category.id) {
                    throw SchemaValidationException(
                        "Categoría '${category.id}' tiene parentId que referencia a sí misma (ciclo directo)"
                    )
                }
            }
        }
    }

    /**
     * Valida que los accountId opcionales referencien cuentas válidas.
     *
     * Para LoanEntity, LoanMovementEntity, accountId puede ser null.
     * Si no es null, debe referenciar una cuenta en la lista.
     *
     * @param backupData Datos de backup a validar
     * @throws SchemaValidationException si algún accountId es inválido
     */
    private fun validateAccountReferences(backupData: BackupData) {
        val accountIds = backupData.accounts.map { it.id }.toSet()

        // Validar LoanEntity.accountId (opcional)
        backupData.loans.forEach { loan ->
            loan.accountId?.let { accountId ->
                if (accountId !in accountIds) {
                    throw SchemaValidationException(
                        "Préstamo '${loan.id}' tiene accountId inválido: '$accountId' " +
                        "(no existe en la lista de cuentas)"
                    )
                }
            }
        }

        // Validar LoanMovementEntity.accountId (opcional)
        backupData.loanMovements.forEach { movement ->
            movement.accountId?.let { accountId ->
                if (accountId !in accountIds) {
                    throw SchemaValidationException(
                        "Movimiento de préstamo '${movement.id}' tiene accountId inválido: '$accountId' " +
                        "(no existe en la lista de cuentas)"
                    )
                }
            }
        }
    }

    /**
     * Valida que los linkedTransactionId referencien transacciones válidas.
     *
     * LoanMovementEntity.linkedTransactionId es una referencia débil sin FK.
     * Si no es null, debe referenciar una transacción en la lista.
     *
     * @param backupData Datos de backup a validar
     * @throws SchemaValidationException si algún linkedTransactionId es inválido
     */
    private fun validateLinkedTransactionReferences(backupData: BackupData) {
        val transactionIds = backupData.transactions.map { it.id }.toSet()

        backupData.loanMovements.forEach { movement ->
            movement.linkedTransactionId?.let { linkedId ->
                if (linkedId !in transactionIds) {
                    throw SchemaValidationException(
                        "Movimiento de préstamo '${movement.id}' tiene linkedTransactionId inválido: '$linkedId' " +
                        "(no existe en la lista de transacciones)"
                    )
                }
            }
        }
    }
}
