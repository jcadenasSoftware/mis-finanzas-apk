package com.jcadenas.xpendz.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jcadenas.xpendz.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: String): BudgetEntity?

    @Query("SELECT MAX(updated_at_epoch_sec) FROM budgets WHERE user_uid = :userUid")
    suspend fun getMaxUpdatedAtEpochSec(userUid: String): Long?

    @Query(
        """
        SELECT * FROM budgets
        WHERE user_uid = :userUid
          AND month = :month
          AND currency = :currency
        ORDER BY category_id
        """
    )
    fun observeByMonth(userUid: String, month: String, currency: String): Flow<List<BudgetEntity>>

    @Query(
        """
        SELECT * FROM budgets
        WHERE user_uid = :userUid
          AND month = :month
          AND currency = :currency
        ORDER BY category_id
        """
    )
    suspend fun getByMonth(userUid: String, month: String, currency: String): List<BudgetEntity>

    @Query(
        """
        SELECT * FROM budgets
        WHERE user_uid = :userUid
          AND month = :month
          AND currency = :currency
          AND category_id = :categoryId
        LIMIT 1
        """
    )
    suspend fun getByUnique(userUid: String, month: String, currency: String, categoryId: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE user_uid = :userUid")
    suspend fun getByUser(userUid: String): List<BudgetEntity>

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM budgets WHERE user_uid = :userUid")
    suspend fun deleteAllByUser(userUid: String)
}
