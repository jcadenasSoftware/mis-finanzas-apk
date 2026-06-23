package com.myfinances.data.backup

import android.util.Log
import androidx.room.withTransaction
import com.myfinances.data.local.AppDatabase
import com.myfinances.data.local.dao.AccountDao
import com.myfinances.data.local.dao.BudgetDao
import com.myfinances.data.local.dao.CategoryDao
import com.myfinances.data.local.dao.ExchangeRateDao
import com.myfinances.data.local.dao.GoalDao
import com.myfinances.data.local.dao.LoanDao
import com.myfinances.data.local.dao.LoanMovementDao
import com.myfinances.data.local.dao.LoanPaymentDao
import com.myfinances.data.local.dao.TransactionDao
import com.myfinances.data.local.dao.TransferDao
import com.myfinances.data.local.dao.UserDao
import com.myfinances.data.local.dao.UserSettingsDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de DataImporter que restaura datos desde BackupData a Room.
 *
 * Esta clase valida el BackupData y luego lo restaura en la base de datos Room
 * usando la estrategia REPLACE_ALL (borrar todo, insertar todo).
 *
 * Características:
 * - Constructor injection para testabilidad
 * - Portable a Android y Desktop JVM
 * - Ejecuta dentro de transacción para atomicidad
 * - Valida BackupData antes de importar
 * - Maneja errores con BackupImportException
 */
@Singleton
class RoomDataImporter @Inject constructor(
    private val database: AppDatabase,
    private val userDao: UserDao,
    private val userSettingsDao: UserSettingsDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val loanDao: LoanDao,
    private val transactionDao: TransactionDao,
    private val transferDao: TransferDao,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val loanMovementDao: LoanMovementDao,
    private val exchangeRateDao: ExchangeRateDao,
    private val validator: BackupSchemaValidator
) : DataImporter {

    override suspend fun restore(userUid: String, backupData: BackupData) {
        return try {
            // 1. Sanitizar datos para tolerar backups antiguos con referencias débiles rotas
            val sanitizedBackupData = sanitizeBackupData(backupData)

            // 2. Validar BackupData sanitizado
            validator.validate(sanitizedBackupData)

            // 3. Ejecutar dentro de transacción para atomicidad
            database.withTransaction {
                // 4. Borrar datos existentes del usuario actual (orden inverso de dependencias)
                deleteUserData(userUid)

                // 5. Insertar datos mapeados al userUid actual (orden topológico de dependencias)
                insertBackupData(userUid, sanitizedBackupData)
            }
        } catch (e: SchemaValidationException) {
            throw BackupImportException("Error de validación del esquema: ${e.message}", e)
        } catch (e: BackupImportException) {
            throw e
        } catch (e: Exception) {
            throw BackupImportException(
                "Error al importar datos del backup: ${e.message ?: "inconsistencia no especificada"}",
                e
            )
        }
    }

    private fun sanitizeBackupData(backupData: BackupData): BackupData {
        val accountIds = backupData.accounts.map { it.id }.toSet()
        val transactionIds = backupData.transactions.map { it.id }.toSet()

        val loans = backupData.loans.map { loan ->
            val sanitizedAccountId = loan.accountId?.takeIf { it in accountIds }

            if (loan.accountId != null && sanitizedAccountId == null) {
                Log.w(
                    "Backup",
                    "restore: limpiando accountId inválido en préstamo id=${loan.id} con accountId=${loan.accountId} (cuenta no existe)"
                )
            }

            loan.copy(accountId = sanitizedAccountId)
        }

        val sanitizedLoanIds = loans.map { it.id }.toSet()

        val loanPayments = backupData.loanPayments.mapNotNull { payment ->
            if (payment.loanId !in sanitizedLoanIds) {
                Log.w(
                    "Backup",
                    "restore: descartando pago de préstamo id=${payment.id} porque loanId=${payment.loanId} no existe"
                )
                return@mapNotNull null
            }

            if (payment.accountId !in accountIds) {
                Log.w(
                    "Backup",
                    "restore: descartando pago de préstamo id=${payment.id} con accountId=${payment.accountId} (cuenta no existe)"
                )
                return@mapNotNull null
            }

            payment
        }

        val loanMovements = backupData.loanMovements.mapNotNull { movement ->
            if (movement.loanId !in sanitizedLoanIds) {
                Log.w(
                    "Backup",
                    "restore: descartando movimiento de préstamo id=${movement.id} porque loanId=${movement.loanId} no existe"
                )
                return@mapNotNull null
            }

            val sanitizedAccountId = movement.accountId?.takeIf { it in accountIds }
            if (movement.accountId != null && sanitizedAccountId == null) {
                Log.w(
                    "Backup",
                    "restore: limpiando accountId inválido en movimiento de préstamo id=${movement.id} con accountId=${movement.accountId} (cuenta no existe)"
                )
            }

            val sanitizedLinkedTransactionId = movement.linkedTransactionId?.takeIf { it in transactionIds }
            if (movement.linkedTransactionId != null && sanitizedLinkedTransactionId == null) {
                Log.w(
                    "Backup",
                    "restore: limpiando linkedTransactionId inválido en movimiento de préstamo id=${movement.id} con linkedTransactionId=${movement.linkedTransactionId} (transacción no existe)"
                )
            }

            movement.copy(
                accountId = sanitizedAccountId,
                linkedTransactionId = sanitizedLinkedTransactionId
            )
        }

        if (loans.size != backupData.loans.size || loanPayments.size != backupData.loanPayments.size || loanMovements.size != backupData.loanMovements.size) {
            Log.w(
                "Backup",
                "restore: backup sanitizado loans=${backupData.loans.size}->${loans.size}, loanPayments=${backupData.loanPayments.size}->${loanPayments.size}, loanMovements=${backupData.loanMovements.size}->${loanMovements.size}"
            )
        }

        return backupData.copy(
            loans = loans,
            loanPayments = loanPayments,
            loanMovements = loanMovements
        )
    }

    /**
     * Borra todos los datos del usuario en orden inverso de dependencias.
     *
     * Orden inverso:
     * 1. ExchangeRates (sin hijos)
     * 2. LoanMovements (hija de Loan)
     * 3. LoanPayments (hija de Loan)
     * 4. Loans (padre de LoanPayment/LoanMovement)
     * 5. Goals (depende de Account)
     * 6. Budgets (depende de Category)
     * 7. Transfers (depende de Account)
     * 8. Transactions (depende de Account/Category)
     * 9. Accounts (padre de muchas entidades)
     * 10. Categories (self-reference + padre de Transaction/Budget)
     * 11. UserSettings (depende de User)
     * 12. User (raíz, ÚLTIMA)
     */
    private suspend fun deleteUserData(userUid: String) {
        exchangeRateDao.deleteAllByUser(userUid)
        loanMovementDao.deleteAllByUser(userUid)
        loanPaymentDao.deleteAllByUser(userUid)
        loanDao.deleteAllByUser(userUid)
        goalDao.deleteAllByUser(userUid)
        budgetDao.deleteAllByUser(userUid)
        transferDao.deleteAllByUser(userUid)
        transactionDao.deleteAllByUser(userUid)
        accountDao.deleteAllByUser(userUid)
        categoryDao.deleteAllByUser(userUid)
        userSettingsDao.deleteAllByUser(userUid)
        userDao.delete(userUid)
    }

    /**
     * Inserta todos los datos del backup en orden topológico de dependencias,
     * mapeando todas las entidades al userUid actual.
     *
     * Orden topológico:
     * 1. UserEntity (mapeado al userUid actual)
     * 2. UserSettingsEntity (mapeado al userUid actual)
     * 3. CategoryEntity (mapeado al userUid actual)
     * 4. AccountEntity (mapeado al userUid actual)
     * 5. LoanEntity (mapeado al userUid actual)
     * 6. TransactionEntity (mapeado al userUid actual)
     * 7. TransferEntity (mapeado al userUid actual)
     * 8. BudgetEntity (mapeado al userUid actual)
     * 9. GoalEntity (mapeado al userUid actual)
     * 10. LoanPaymentEntity (mapeado al userUid actual)
     * 11. LoanMovementEntity (mapeado al userUid actual)
     * 12. ExchangeRateEntity (mapeado al userUid actual)
     */
    private suspend fun insertBackupData(userUid: String, backupData: BackupData) {
        // 1. UserEntity (mapear uid al userUid actual)
        val userEntity = backupData.user.copy(uid = userUid)
        userDao.upsert(userEntity)

        // 2. UserSettingsEntity (mapear userUid al userUid actual)
        val userSettingsEntity = backupData.userSettings.copy(userUid = userUid)
        userSettingsDao.upsert(userSettingsEntity)

        // 3. CategoryEntity (mapear userUid al userUid actual)
        if (backupData.categories.isNotEmpty()) {
            val categoriesMapped = sortCategoriesForInsert(backupData.categories)
                .map { it.copy(userUid = userUid) }
            categoryDao.insertAll(categoriesMapped)
        }

        // 4. AccountEntity (mapear userUid al userUid actual)
        if (backupData.accounts.isNotEmpty()) {
            val accountsMapped = backupData.accounts.map { it.copy(userUid = userUid) }
            accountDao.insertAll(accountsMapped)
        }

        // 5. LoanEntity (mapear userUid al userUid actual)
        if (backupData.loans.isNotEmpty()) {
            val loansMapped = backupData.loans.map { it.copy(userUid = userUid) }
            loanDao.insertAll(loansMapped)
        }

        // 6. TransactionEntity (mapear userUid al userUid actual)
        if (backupData.transactions.isNotEmpty()) {
            val transactionsMapped = backupData.transactions.map { it.copy(userUid = userUid) }
            transactionDao.insertAll(transactionsMapped)
        }

        // 7. TransferEntity (mapear userUid al userUid actual)
        if (backupData.transfers.isNotEmpty()) {
            val transfersMapped = backupData.transfers.map { it.copy(userUid = userUid) }
            transferDao.insertAll(transfersMapped)
        }

        // 8. BudgetEntity (mapear userUid al userUid actual)
        if (backupData.budgets.isNotEmpty()) {
            val budgetsMapped = backupData.budgets.map { it.copy(userUid = userUid) }
            budgetDao.insertAll(budgetsMapped)
        }

        // 9. GoalEntity (mapear userUid al userUid actual)
        if (backupData.goals.isNotEmpty()) {
            val goalsMapped = backupData.goals.map { it.copy(userUid = userUid) }
            goalDao.insertAll(goalsMapped)
        }

        // 10. LoanPaymentEntity (mapear userUid al userUid actual)
        if (backupData.loanPayments.isNotEmpty()) {
            val loanPaymentsMapped = backupData.loanPayments.map { it.copy(userUid = userUid) }
            loanPaymentDao.insertAll(loanPaymentsMapped)
        }

        // 11. LoanMovementEntity (mapear userUid al userUid actual)
        if (backupData.loanMovements.isNotEmpty()) {
            val loanMovementsMapped = backupData.loanMovements.map { it.copy(userUid = userUid) }
            loanMovementDao.insertAll(loanMovementsMapped)
        }

        // 12. ExchangeRateEntity (mapear userUid al userUid actual)
        if (backupData.exchangeRates.isNotEmpty()) {
            val exchangeRatesMapped = backupData.exchangeRates.map { it.copy(userUid = userUid) }
            exchangeRateDao.insertAll(exchangeRatesMapped)
        }
    }

    private fun sortCategoriesForInsert(categories: List<com.myfinances.data.local.entity.CategoryEntity>): List<com.myfinances.data.local.entity.CategoryEntity> {
        val byId = categories.associateBy { it.id }
        val sorted = mutableListOf<com.myfinances.data.local.entity.CategoryEntity>()
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(category: com.myfinances.data.local.entity.CategoryEntity) {
            if (category.id in visited) return
            if (!visiting.add(category.id)) {
                Log.w(
                    "Backup",
                    "restore: ciclo detectado en categorías para id=${category.id}; se conservará el orden original restante"
                )
                return
            }

            category.parentId?.let { parentId ->
                byId[parentId]?.let { parent ->
                    visit(parent)
                }
            }

            visiting.remove(category.id)
            visited.add(category.id)
            sorted.add(category)
        }

        categories.forEach { visit(it) }
        return sorted
    }
}
