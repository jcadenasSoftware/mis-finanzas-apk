package com.myfinances.data.backup

import android.util.Log
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
import com.myfinances.data.local.entity.AccountEntity
import com.myfinances.data.local.entity.BudgetEntity
import com.myfinances.data.local.entity.CategoryEntity
import com.myfinances.data.local.entity.ExchangeRateEntity
import com.myfinances.data.local.entity.GoalEntity
import com.myfinances.data.local.entity.LoanEntity
import com.myfinances.data.local.entity.LoanMovementEntity
import com.myfinances.data.local.entity.LoanPaymentEntity
import com.myfinances.data.local.entity.TransactionEntity
import com.myfinances.data.local.entity.TransferEntity
import com.myfinances.data.local.entity.UserEntity
import com.myfinances.data.local.entity.UserSettingsEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de DataExporter que exporta datos desde Room a BackupData.
 *
 * Esta clase consulta todas las entidades del usuario desde la base de datos Room
 * y las organiza en un BackupData siguiendo el orden topológico de dependencias.
 *
 * Características:
 * - Constructor injection para testabilidad
 * - Portable a Android y Desktop JVM
 * - Valida BackupData antes de devolverlo
 * - Maneja errores con BackupExportException
 */
@Singleton
class RoomDataExporter @Inject constructor(
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
) : DataExporter {

    override suspend fun export(userUid: String): BackupData {
        return try {
            // 1. Consultar UserEntity (raíz)
            val user = userDao.getByUid(userUid)
                ?: throw BackupExportException("Usuario no encontrado: $userUid")

            // 2. Consultar UserSettingsEntity (1:1 con User)
            val userSettings = userSettingsDao.get(userUid) ?: run {
                Log.w(
                    "Backup",
                    "export: no existe configuración para userUid=$userUid, usando valores por defecto"
                )
                UserSettingsEntity(
                    userUid = userUid,
                    countryCode = "CO",
                    baseCurrency = "COP",
                    updatedAtEpochSec = System.currentTimeMillis() / 1000,
                    updatedBy = null
                )
            }

            // 3. Consultar CategoryEntity (todas, incluyendo jerarquía)
            val categories: List<CategoryEntity> = categoryDao.getByUser(userUid)

            // 4. Consultar AccountEntity (todas)
            val accounts: List<AccountEntity> = accountDao.getByUser(userUid)
            val accountIds = accounts.map { it.id }.toSet()

            // 5. Consultar LoanEntity (todas)
            val rawLoans: List<LoanEntity> = loanDao.getByUser(userUid)
            
            // Filtrar préstamos con accountId inválido (huérfanos)
            val loans = rawLoans.filter { loan ->
                loan.accountId?.let { accountId ->
                    if (accountId !in accountIds) {
                        Log.w(
                            "Backup",
                            "export: filtrando préstamo huérfano id=${loan.id} con accountId=$accountId (cuenta no existe)"
                        )
                        false
                    } else {
                        true
                    }
                } ?: true // Si accountId es null, es válido
            }

            // 6. Consultar TransactionEntity (todas)
            val transactions: List<TransactionEntity> = transactionDao.getByUser(userUid)

            // 7. Consultar TransferEntity (todas)
            val transfers: List<TransferEntity> = transferDao.getByUser(userUid)

            // 8. Consultar BudgetEntity (todas)
            val budgets: List<BudgetEntity> = budgetDao.getByUser(userUid)

            // 9. Consultar GoalEntity (todas)
            val goals: List<GoalEntity> = goalDao.getByUser(userUid)

            // 10. Consultar LoanPaymentEntity (todas)
            val loanPayments: List<LoanPaymentEntity> = loanPaymentDao.getByUser(userUid)

            val transactionIds = transactions.map { it.id }.toSet()

            // 11. Consultar LoanMovementEntity (todas)
            val rawLoanMovements: List<LoanMovementEntity> = loanMovementDao.getAllByUser(userUid)

            // Filtrar o reparar movimientos de préstamo con referencias inválidas
            val loanMovements = rawLoanMovements.mapNotNull { movement ->
                val sanitizedAccountId = movement.accountId?.takeIf { accountId ->
                    accountId in accountIds
                }

                if (movement.accountId != null && sanitizedAccountId == null) {
                    Log.w(
                        "Backup",
                        "export: filtrando accountId huérfano en movimiento de préstamo id=${movement.id} con accountId=${movement.accountId} (cuenta no existe)"
                    )
                }

                val sanitizedLinkedTransactionId = movement.linkedTransactionId?.takeIf { linkedId ->
                    linkedId in transactionIds
                }

                if (movement.linkedTransactionId != null && sanitizedLinkedTransactionId == null) {
                    Log.w(
                        "Backup",
                        "export: limpiando linkedTransactionId inválido en movimiento de préstamo id=${movement.id} con linkedTransactionId=${movement.linkedTransactionId} (transacción no existe)"
                    )
                }

                movement.copy(
                    accountId = sanitizedAccountId,
                    linkedTransactionId = sanitizedLinkedTransactionId
                )
            }

            // 12. Consultar ExchangeRateEntity (todas)
            val exchangeRates: List<ExchangeRateEntity> = exchangeRateDao.getByUser(userUid)

            // 13. Construir BackupMetadata
            val metadata = BackupMetadata(
                schemaVersion = BackupMetadata.CURRENT_BACKUP_SCHEMA_VERSION,
                exportedAtEpochSec = System.currentTimeMillis() / 1000,
                userUid = userUid,
                appVersion = null // TODO: Obtener desde BuildConfig si es necesario
            )

            // 14. Construir BackupData
            val backupData = BackupData(
                metadata = metadata,
                user = user,
                userSettings = userSettings,
                categories = categories,
                accounts = accounts,
                loans = loans,
                transactions = transactions,
                transfers = transfers,
                budgets = budgets,
                goals = goals,
                loanPayments = loanPayments,
                loanMovements = loanMovements,
                exchangeRates = exchangeRates
            )

            // 15. Validar BackupData
            validator.validate(backupData)

            backupData
        } catch (e: BackupExportException) {
            throw e
        } catch (e: SchemaValidationException) {
            throw BackupExportException("Error de validación del backup: ${e.message}", e)
        } catch (e: Exception) {
            throw BackupExportException("Error al exportar datos del usuario", e)
        }
    }
}
