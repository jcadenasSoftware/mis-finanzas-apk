package com.myfinances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myfinances.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<GoalEntity>)

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: String): GoalEntity?

    @Query(
        """
        SELECT * FROM goals
        WHERE user_uid = :userUid
        ORDER BY updated_at_epoch_sec DESC, created_at_epoch_sec DESC
        """
    )
    fun observeByUser(userUid: String): Flow<List<GoalEntity>>

    @Query(
        """
        SELECT * FROM goals
        WHERE user_uid = :userUid
        ORDER BY updated_at_epoch_sec DESC, created_at_epoch_sec DESC
        """
    )
    suspend fun getByUser(userUid: String): List<GoalEntity>

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: String)
}
