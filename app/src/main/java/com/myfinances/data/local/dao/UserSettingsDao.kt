package com.jcadenas.xpendz.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jcadenas.xpendz.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettingsEntity)

    @Update
    suspend fun update(settings: UserSettingsEntity)

    @Query("SELECT * FROM user_settings WHERE user_uid = :userUid LIMIT 1")
    suspend fun get(userUid: String): UserSettingsEntity?

    @Query("SELECT * FROM user_settings WHERE user_uid = :userUid LIMIT 1")
    fun observe(userUid: String): Flow<UserSettingsEntity?>

    @Query("DELETE FROM user_settings WHERE user_uid = :userUid")
    suspend fun deleteAllByUser(userUid: String)
}
