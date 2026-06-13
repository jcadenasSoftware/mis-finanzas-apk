package com.myfinances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myfinances.data.local.entity.LoanMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanMovementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movement: LoanMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movements: List<LoanMovementEntity>)

    @Update
    suspend fun update(movement: LoanMovementEntity)

    @Query("SELECT * FROM loan_movements WHERE id = :id")
    suspend fun getById(id: String): LoanMovementEntity?

    @Query("SELECT MAX(updated_at_epoch_sec) FROM loan_movements WHERE user_uid = :userUid")
    suspend fun getMaxUpdatedAtEpochSec(userUid: String): Long?

    @Query(
        """
        SELECT * FROM loan_movements
        WHERE user_uid = :userUid
          AND loan_id = :loanId
        ORDER BY occurred_at_epoch_sec ASC, created_at_epoch_sec ASC
        """
    )
    fun observeByLoan(userUid: String, loanId: String): Flow<List<LoanMovementEntity>>

    @Query(
        """
        SELECT * FROM loan_movements
        WHERE user_uid = :userUid
          AND loan_id = :loanId
        ORDER BY occurred_at_epoch_sec ASC, created_at_epoch_sec ASC
        """
    )
    suspend fun getByLoan(userUid: String, loanId: String): List<LoanMovementEntity>

    @Query(
        """
        SELECT * FROM loan_movements
        WHERE user_uid = :userUid
        ORDER BY occurred_at_epoch_sec DESC, created_at_epoch_sec DESC
        """
    )
    suspend fun getAllByUser(userUid: String): List<LoanMovementEntity>

    @Query(
        """
        SELECT COALESCE(SUM(amount_cents), 0)
        FROM loan_movements
        WHERE user_uid = :userUid
          AND loan_id = :loanId
          AND movement_type = :movementType
        """
    )
    suspend fun sumByType(userUid: String, loanId: String, movementType: String): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount_cents), 0)
        FROM loan_movements
        WHERE user_uid = :userUid
          AND loan_id = :loanId
          AND movement_type = 'TOPUP'
        """
    )
    suspend fun sumTopupCents(userUid: String, loanId: String): Long

    @Query("DELETE FROM loan_movements WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM loan_movements WHERE user_uid = :userUid")
    suspend fun deleteAllByUser(userUid: String)
}
