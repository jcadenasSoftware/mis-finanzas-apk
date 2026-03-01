package com.myfinances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myfinances.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE user_uid = :userUid ORDER BY name")
    fun observeByUser(userUid: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE user_uid = :userUid ORDER BY name")
    suspend fun getByUser(userUid: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE user_uid = :userUid AND parent_id IS NULL ORDER BY name")
    fun observeRoots(userUid: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE user_uid = :userUid AND parent_id IS NULL ORDER BY name")
    suspend fun getRoots(userUid: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE user_uid = :userUid AND parent_id = :parentId ORDER BY name")
    fun observeChildren(userUid: String, parentId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE user_uid = :userUid AND parent_id = :parentId ORDER BY name")
    suspend fun getChildren(userUid: String, parentId: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM categories WHERE user_uid = :userUid")
    suspend fun deleteAllByUser(userUid: String)
}
