package com.myfinances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myfinances.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

data class TransactionWithDetails(
    val id: String,
    val userUid: String,
    val accountId: String,
    val accountName: String,
    val categoryId: String,
    val categoryName: String,
    val kind: String,
    val amountCents: Long,
    val occurredAtEpochSec: Long,
    val note: String?
)

data class MonthlyCategoryTotal(
    val rootCategoryId: String,
    val rootCategoryName: String,
    val month: Int,
    val totalAmountCents: Long
)

data class MonthlyCategoryDetailTotal(
    val rootCategoryId: String,
    val categoryId: String,
    val categoryName: String,
    val month: Int,
    val totalAmountCents: Long
)

data class RootCategorySpentTotal(
    val rootCategoryId: String,
    val rootCategoryName: String,
    val totalSpentCents: Long
)

data class CategorySpentTotal(
    val categoryId: String,
    val categoryName: String,
    val totalSpentCents: Long
)

data class CategorySpentResult(
    val categoryId: String,
    val totalSpentCents: Long
)

data class HierarchyCategoryTotal(
    val rootCategoryId: String,
    val rootCategoryName: String,
    val subCategoryId: String,
    val subCategoryName: String,
    val totalCents: Long
)

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("""
        SELECT t.id, t.user_uid AS userUid, t.account_id AS accountId, a.name AS accountName,
               t.category_id AS categoryId, c.name AS categoryName,
               t.kind, t.amount_cents AS amountCents, t.occurred_at_epoch_sec AS occurredAtEpochSec, t.note
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.account_id
        INNER JOIN categories c ON c.id = t.category_id
        WHERE t.user_uid = :userUid
        ORDER BY t.occurred_at_epoch_sec DESC, t.created_at_epoch_sec DESC
        LIMIT :limit
    """)
    fun observeRecent(userUid: String, limit: Int = 50): Flow<List<TransactionWithDetails>>

    @Query("""
        SELECT t.id, t.user_uid AS userUid, t.account_id AS accountId, a.name AS accountName,
               t.category_id AS categoryId, c.name AS categoryName,
               t.kind, t.amount_cents AS amountCents, t.occurred_at_epoch_sec AS occurredAtEpochSec, t.note
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.account_id
        INNER JOIN categories c ON c.id = t.category_id
        WHERE t.user_uid = :userUid
        ORDER BY t.occurred_at_epoch_sec DESC, t.created_at_epoch_sec DESC
        LIMIT :limit
    """)
    suspend fun getRecent(userUid: String, limit: Int = 50): List<TransactionWithDetails>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT MAX(updated_at_epoch_sec) FROM transactions WHERE user_uid = :userUid")
    suspend fun getMaxUpdatedAtEpochSec(userUid: String): Long?

    @Query("SELECT MAX(updated_at_epoch_sec) FROM transactions WHERE user_uid = :userUid")
    fun observeMaxUpdatedAtEpochSec(userUid: String): Flow<Long?>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM transactions WHERE user_uid = :userUid")
    suspend fun deleteAllByUser(userUid: String)

    @Query("""
        SELECT t.id, t.user_uid AS userUid, t.account_id AS accountId, a.name AS accountName,
               t.category_id AS categoryId, c.name AS categoryName,
               t.kind, t.amount_cents AS amountCents, t.occurred_at_epoch_sec AS occurredAtEpochSec, t.note
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.account_id
        INNER JOIN categories c ON c.id = t.category_id
        WHERE t.user_uid = :userUid
          AND (:accountId IS NULL OR t.account_id = :accountId)
          AND (:categoryId IS NULL OR t.category_id = :categoryId)
          AND (:fromEpochSec IS NULL OR t.occurred_at_epoch_sec >= :fromEpochSec)
          AND (:toEpochSec IS NULL OR t.occurred_at_epoch_sec <= :toEpochSec)
        ORDER BY t.occurred_at_epoch_sec DESC, t.created_at_epoch_sec DESC
        LIMIT :limit
    """)
    suspend fun getFiltered(
        userUid: String,
        accountId: String?,
        categoryId: String?,
        fromEpochSec: Long?,
        toEpochSec: Long?,
        limit: Int = 100
    ): List<TransactionWithDetails>

    @Query(
        """
        SELECT strftime('%Y-%m', datetime(t.occurred_at_epoch_sec, 'unixepoch')) AS monthKey
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.account_id
        WHERE t.user_uid = :userUid
          AND a.currency = :currency
          AND t.kind = 'EXPENSE'
        GROUP BY monthKey
        ORDER BY monthKey DESC
        LIMIT :limit
        """
    )
    suspend fun getExpenseMonths(userUid: String, currency: String, limit: Int = 24): List<String>

    @Query(
        """
        SELECT
            r.id AS rootCategoryId,
            r.name AS rootCategoryName,
            CAST(strftime('%m', datetime(t.occurred_at_epoch_sec, 'unixepoch')) AS INTEGER) AS month,
            SUM(ABS(t.amount_cents)) AS totalAmountCents
        FROM transactions t
        INNER JOIN categories c ON c.id = t.category_id
        INNER JOIN categories r ON r.id = CASE WHEN c.parent_id IS NULL THEN c.id ELSE c.parent_id END
        WHERE t.user_uid = :userUid
          AND (:accountId IS NULL OR t.account_id = :accountId)
          AND t.kind = :kind
          AND CAST(strftime('%Y', datetime(t.occurred_at_epoch_sec, 'unixepoch')) AS INTEGER) = :year
        GROUP BY r.id, r.name, month
        ORDER BY month ASC
        """
    )
    suspend fun getMonthlyTotalsByRootCategory(
        userUid: String,
        accountId: String?,
        year: Int,
        kind: String
    ): List<MonthlyCategoryTotal>

    @Query(
        """
        SELECT
            r.id AS rootCategoryId,
            c.id AS categoryId,
            c.name AS categoryName,
            CAST(strftime('%m', datetime(t.occurred_at_epoch_sec, 'unixepoch')) AS INTEGER) AS month,
            SUM(ABS(t.amount_cents)) AS totalAmountCents
        FROM transactions t
        INNER JOIN categories c ON c.id = t.category_id
        INNER JOIN categories r ON r.id = CASE WHEN c.parent_id IS NULL THEN c.id ELSE c.parent_id END
        WHERE t.user_uid = :userUid
          AND (:accountId IS NULL OR t.account_id = :accountId)
          AND t.kind = :kind
          AND CAST(strftime('%Y', datetime(t.occurred_at_epoch_sec, 'unixepoch')) AS INTEGER) = :year
        GROUP BY r.id, c.id, c.name, month
        ORDER BY month ASC
        """
    )
    suspend fun getMonthlyTotalsBySubcategory(
        userUid: String,
        accountId: String?,
        year: Int,
        kind: String
    ): List<MonthlyCategoryDetailTotal>

    @Query(
        """
        SELECT
            r.id AS rootCategoryId,
            r.name AS rootCategoryName,
            SUM(ABS(t.amount_cents)) AS totalSpentCents
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.account_id
        INNER JOIN categories c ON c.id = t.category_id
        INNER JOIN categories r ON r.id = CASE WHEN c.parent_id IS NULL THEN c.id ELSE c.parent_id END
        WHERE t.user_uid = :userUid
          AND t.kind = 'EXPENSE'
          AND a.currency = :currency
          AND t.occurred_at_epoch_sec >= :fromEpochSec
          AND t.occurred_at_epoch_sec <= :toEpochSec
        GROUP BY r.id, r.name
        ORDER BY r.name ASC
        """
    )
    suspend fun getExpenseTotalsByRootCategoryInRange(
        userUid: String,
        currency: String,
        fromEpochSec: Long,
        toEpochSec: Long
    ): List<RootCategorySpentTotal>

    @Query(
        """
        SELECT
            c.id AS categoryId,
            c.name AS categoryName,
            SUM(ABS(t.amount_cents)) AS totalSpentCents
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.account_id
        INNER JOIN categories c ON c.id = t.category_id
        WHERE t.user_uid = :userUid
          AND t.kind = 'EXPENSE'
          AND a.currency = :currency
          AND t.occurred_at_epoch_sec >= :fromEpochSec
          AND t.occurred_at_epoch_sec <= :toEpochSec
        GROUP BY c.id, c.name
        ORDER BY c.name ASC
        """
    )
    suspend fun getExpenseTotalsByCategoryInRange(
        userUid: String,
        currency: String,
        fromEpochSec: Long,
        toEpochSec: Long
    ): List<CategorySpentTotal>

    @Query(
        """
        SELECT
            r.id AS rootCategoryId,
            r.name AS rootCategoryName,
            SUM(ABS(t.amount_cents)) AS totalSpentCents
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.account_id
        INNER JOIN categories c ON c.id = t.category_id
        INNER JOIN categories r ON r.id = CASE WHEN c.parent_id IS NULL THEN c.id ELSE c.parent_id END
        WHERE t.user_uid = :userUid
          AND t.kind = 'INCOME'
          AND a.currency = :currency
          AND t.occurred_at_epoch_sec >= :fromEpochSec
          AND t.occurred_at_epoch_sec <= :toEpochSec
        GROUP BY r.id, r.name
        ORDER BY totalSpentCents DESC
        """
    )
    suspend fun getIncomeTotalsByRootCategoryInRange(
        userUid: String,
        currency: String,
        fromEpochSec: Long,
        toEpochSec: Long
    ): List<RootCategorySpentTotal>

    @Query(
        """
        SELECT
            r.id   AS rootCategoryId,
            r.name AS rootCategoryName,
            c.id   AS subCategoryId,
            c.name AS subCategoryName,
            SUM(ABS(t.amount_cents)) AS totalCents
        FROM transactions t
        INNER JOIN accounts a  ON a.id = t.account_id
        INNER JOIN categories c ON c.id = t.category_id
        INNER JOIN categories r ON r.id = CASE WHEN c.parent_id IS NULL THEN c.id ELSE c.parent_id END
        WHERE t.user_uid = :userUid
          AND t.kind IN (:kinds)
          AND a.currency = :currency
          AND t.occurred_at_epoch_sec >= :fromEpochSec
          AND t.occurred_at_epoch_sec <= :toEpochSec
        GROUP BY r.id, r.name, c.id, c.name
        ORDER BY r.name ASC, totalCents DESC
        """
    )
    suspend fun getHierarchyTotalsInRange(
        userUid: String,
        kinds: List<String>,
        currency: String,
        fromEpochSec: Long,
        toEpochSec: Long
    ): List<HierarchyCategoryTotal>

    @Query("""
        SELECT t.category_id AS categoryId, COALESCE(SUM(t.amount_cents), 0) AS totalSpentCents
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.account_id
        WHERE t.user_uid = :userUid
          AND t.kind = 'EXPENSE'
          AND a.currency = :currency
          AND strftime('%Y-%m', datetime(t.occurred_at_epoch_sec, 'unixepoch')) = :month
        GROUP BY t.category_id
    """)
    suspend fun getSpentPerCategoryForMonth(
        userUid: String,
        currency: String,
        month: String
    ): List<CategorySpentResult>

    @Query("""
        SELECT DISTINCT CAST(strftime('%Y', datetime(t.occurred_at_epoch_sec, 'unixepoch')) AS INTEGER) AS year
        FROM transactions t
        WHERE t.user_uid = :userUid
        ORDER BY year DESC
    """)
    suspend fun getYearsWithTransactions(userUid: String): List<Int>

    @Query("SELECT * FROM transactions WHERE user_uid = :userUid")
    suspend fun getByUser(userUid: String): List<TransactionEntity>
}
