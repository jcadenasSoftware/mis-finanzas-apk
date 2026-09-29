package com.jcadenas.xpendz.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jcadenas.xpendz.data.local.entity.ObligationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ObligationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(obligation: ObligationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(obligations: List<ObligationEntity>)

    @Update
    suspend fun update(obligation: ObligationEntity)

    @Query("SELECT * FROM obligations WHERE id = :id")
    suspend fun getById(id: String): ObligationEntity?

    @Query("SELECT MAX(updated_at_epoch_sec) FROM obligations WHERE user_uid = :userUid")
    suspend fun getMaxUpdatedAtEpochSec(userUid: String): Long?

    @Query(
        """
        SELECT * FROM obligations
        WHERE user_uid = :userUid
        ORDER BY updated_at_epoch_sec DESC, created_at_epoch_sec DESC
        """
    )
    fun observeByUser(userUid: String): Flow<List<ObligationEntity>>

    @Query(
        """
        SELECT * FROM obligations
        WHERE user_uid = :userUid
        ORDER BY updated_at_epoch_sec DESC, created_at_epoch_sec DESC
        """
    )
    suspend fun getByUser(userUid: String): List<ObligationEntity>

    @Query("DELETE FROM obligations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM obligations WHERE user_uid = :userUid")
    suspend fun deleteAllByUser(userUid: String)
}
