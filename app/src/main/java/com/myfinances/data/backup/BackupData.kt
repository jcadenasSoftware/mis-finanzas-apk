package com.jcadenas.xpendz.data.backup

import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.data.local.entity.BudgetEntity
import com.jcadenas.xpendz.data.local.entity.CategoryEntity
import com.jcadenas.xpendz.data.local.entity.ExchangeRateEntity
import com.jcadenas.xpendz.data.local.entity.GoalEntity
import com.jcadenas.xpendz.data.local.entity.LoanEntity
import com.jcadenas.xpendz.data.local.entity.LoanMovementEntity
import com.jcadenas.xpendz.data.local.entity.LoanPaymentEntity
import com.jcadenas.xpendz.data.local.entity.TransactionEntity
import com.jcadenas.xpendz.data.local.entity.TransferEntity
import com.jcadenas.xpendz.data.local.entity.UserEntity
import com.jcadenas.xpendz.data.local.entity.UserSettingsEntity
import kotlinx.serialization.Serializable

/**
 * Representación completa de un backup de datos de Xpendz.
 *
 * Contiene todas las entidades exportables del esquema Room en orden topológico
 * de dependencias. Este orden es crítico para garantizar que al restaurar,
 * todas las referencias de Foreign Keys existan.
 *
 * Orden de entidades (topológico de dependencias):
 * 1. UserEntity (raíz, sin dependencias)
 * 2. UserSettingsEntity (1:1 con User)
 * 3. CategoryEntity (depende de User, self-reference)
 * 4. AccountEntity (depende de User)
 * 5. LoanEntity (depende de User)
 * 6. TransactionEntity (depende de User, Account, Category)
 * 7. TransferEntity (depende de User, Account)
 * 8. BudgetEntity (depende de User, Category)
 * 9. GoalEntity (depende de User, Account)
 * 10. LoanPaymentEntity (depende de User, Loan, Account)
 * 11. LoanMovementEntity (depende de User, Loan, Account)
 * 12. ExchangeRateEntity (depende de User)
 *
 * @property metadata Metadatos del backup (versión, usuario, timestamp)
 * @property user Usuario raíz del backup
 * @property userSettings Configuración del usuario (1:1 con user)
 * @property categories Lista de categorías (incluye jerarquía con parentId)
 * @property accounts Lista de cuentas bancarias y similares
 * @property loans Lista de préstamos
 * @property transactions Lista de transacciones financieras
 * @property transfers Lista de transferencias entre cuentas
 * @property budgets Lista de presupuestos mensuales por categoría
 * @property goals Lista de metas de ahorro
 * @property loanPayments Lista de pagos de préstamos
 * @property loanMovements Lista de movimientos de préstamos (intereses, ajustes)
 * @property exchangeRates Lista de tasas de cambio de moneda
 */
@Serializable
data class BackupData(
    val metadata: BackupMetadata,

    // ===== ENTIDAD RAÍZ =====
    /**
     * Usuario raíz del backup.
     * Debe coincidir con metadata.userUid.
     * Es la entidad base de la que dependen todas las demás.
     */
    val user: UserEntity,

    // ===== ENTIDADES 1:1 =====
    /**
     * Configuración del usuario.
     * Relación 1:1 con UserEntity.
     * Contiene país, moneda base y preferencias.
     */
    val userSettings: UserSettingsEntity,

    // ===== ENTIDADES PADRE =====
    /**
     * Categorías de transacciones.
     * Incluye jerarquía mediante parentId (self-reference).
     * Orden: todas las categorías deben exportarse juntas para validar parentId.
     */
    val categories: List<CategoryEntity> = emptyList(),

    /**
     * Cuentas bancarias, efectivo, ahorros, etc.
     * Dependencia: UserEntity.
     */
    val accounts: List<AccountEntity> = emptyList(),

    /**
     * Préstamos (prestados o tomados).
     * Dependencia: UserEntity.
     */
    val loans: List<LoanEntity> = emptyList(),

    // ===== ENTIDADES HIJAS =====
    /**
     * Transacciones financieras individuales.
     * Dependencias: UserEntity, AccountEntity, CategoryEntity.
     */
    val transactions: List<TransactionEntity> = emptyList(),

    /**
     * Transferencias entre cuentas del mismo usuario.
     * Dependencias: UserEntity, AccountEntity (x2: from/to).
     */
    val transfers: List<TransferEntity> = emptyList(),

    /**
     * Presupuestos mensuales por categoría.
     * Dependencias: UserEntity, CategoryEntity.
     * Unique constraint: (user_uid, month, currency, category_id).
     */
    val budgets: List<BudgetEntity> = emptyList(),

    /**
     * Metas de ahorro con fecha objetivo.
     * Dependencias: UserEntity, AccountEntity.
     */
    val goals: List<GoalEntity> = emptyList(),

    // ===== ENTIDADES HIJAS DE LOAN =====
    /**
     * Pagos de préstamos.
     * Dependencias: UserEntity, LoanEntity, AccountEntity.
     */
    val loanPayments: List<LoanPaymentEntity> = emptyList(),

    /**
     * Movimientos de préstamos (intereses, ajustes).
     * Dependencias: UserEntity, LoanEntity, AccountEntity (opcional).
     * Nota: linkedTransactionId es una referencia débil sin FK.
     */
    val loanMovements: List<LoanMovementEntity> = emptyList(),

    // ===== DATOS VOLÁTILES =====
    /**
     * Tasas de cambio de moneda por usuario.
     * Dependencia: UserEntity.
     * Unique constraint: (user_uid, from_currency, to_currency).
     * Nota: Son datos volátiles que cambian frecuentemente.
     */
    val exchangeRates: List<ExchangeRateEntity> = emptyList()
) {
    companion object {
        /**
         * Cantidad total de entidades en BackupData.
         * Útil para validación y estadísticas.
         */
        const val ENTITY_COUNT = 12
    }
}
