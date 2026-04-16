package com.myfinances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myfinances.data.local.entity.LoanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(loan: LoanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(loans: List<LoanEntity>)

    @Update
    suspend fun update(loan: LoanEntity)

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getById(id: String): LoanEntity?

    @Query("SELECT MAX(updated_at_epoch_sec) FROM loans WHERE user_uid = :userUid")
    suspend fun getMaxUpdatedAtEpochSec(userUid: String): Long?

    @Query(
        """
        SELECT * FROM loans
        WHERE user_uid = :userUid
          AND (:type IS NULL OR type = :type)
          AND (:status IS NULL OR status = :status)
          AND (:currency IS NULL OR currency = :currency)
        ORDER BY updated_at_epoch_sec DESC, created_at_epoch_sec DESC
        """
    )
    fun observeFiltered(userUid: String, type: String?, status: String?, currency: String?): Flow<List<LoanEntity>>

    @Query(
        """
        SELECT * FROM loans
        WHERE user_uid = :userUid
          AND (:type IS NULL OR type = :type)
          AND (:status IS NULL OR status = :status)
          AND (:currency IS NULL OR currency = :currency)
        ORDER BY updated_at_epoch_sec DESC, created_at_epoch_sec DESC
        """
    )
    suspend fun getFiltered(userUid: String, type: String?, status: String?, currency: String?): List<LoanEntity>

    @Query("SELECT * FROM loans WHERE user_uid = :userUid")
    suspend fun getByUser(userUid: String): List<LoanEntity>

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM loans WHERE user_uid = :userUid")
    suspend fun deleteAllByUser(userUid: String)
}
