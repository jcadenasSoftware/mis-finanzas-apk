package com.myfinances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myfinances.data.local.entity.TransferEntity
import kotlinx.coroutines.flow.Flow

data class TransferWithDetails(
    val id: String,
    val userUid: String,
    val fromAccountId: String,
    val fromAccountName: String,
    val toAccountId: String,
    val toAccountName: String,
    val amountCents: Long,
    val occurredAtEpochSec: Long,
    val note: String?
)

@Dao
interface TransferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transfer: TransferEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transfers: List<TransferEntity>)

    @Update
    suspend fun update(transfer: TransferEntity)

    @Query("""
        SELECT tr.id, tr.user_uid AS userUid, 
               tr.from_account_id AS fromAccountId, a_from.name AS fromAccountName,
               tr.to_account_id AS toAccountId, a_to.name AS toAccountName,
               tr.amount_cents AS amountCents, tr.occurred_at_epoch_sec AS occurredAtEpochSec, tr.note
        FROM transfers tr
        INNER JOIN accounts a_from ON a_from.id = tr.from_account_id
        INNER JOIN accounts a_to ON a_to.id = tr.to_account_id
        WHERE tr.user_uid = :userUid
        ORDER BY tr.occurred_at_epoch_sec DESC, tr.created_at_epoch_sec DESC
        LIMIT :limit
    """)
    fun observeRecent(userUid: String, limit: Int = 50): Flow<List<TransferWithDetails>>

    @Query("""
        SELECT tr.id, tr.user_uid AS userUid, 
               tr.from_account_id AS fromAccountId, a_from.name AS fromAccountName,
               tr.to_account_id AS toAccountId, a_to.name AS toAccountName,
               tr.amount_cents AS amountCents, tr.occurred_at_epoch_sec AS occurredAtEpochSec, tr.note
        FROM transfers tr
        INNER JOIN accounts a_from ON a_from.id = tr.from_account_id
        INNER JOIN accounts a_to ON a_to.id = tr.to_account_id
        WHERE tr.user_uid = :userUid
        ORDER BY tr.occurred_at_epoch_sec DESC, tr.created_at_epoch_sec DESC
        LIMIT :limit
    """)
    suspend fun getRecent(userUid: String, limit: Int = 50): List<TransferWithDetails>

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun getById(id: String): TransferEntity?

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM transfers WHERE user_uid = :userUid")
    suspend fun deleteAllByUser(userUid: String)

    @Query("SELECT * FROM transfers WHERE user_uid = :userUid")
    suspend fun getByUser(userUid: String): List<TransferEntity>

    @Query("""
        SELECT tr.id, tr.user_uid AS userUid, 
               tr.from_account_id AS fromAccountId, a_from.name AS fromAccountName,
               tr.to_account_id AS toAccountId, a_to.name AS toAccountName,
               tr.amount_cents AS amountCents, tr.occurred_at_epoch_sec AS occurredAtEpochSec, tr.note
        FROM transfers tr
        INNER JOIN accounts a_from ON a_from.id = tr.from_account_id
        INNER JOIN accounts a_to ON a_to.id = tr.to_account_id
        WHERE tr.user_uid = :userUid
          AND (:accountId IS NULL OR tr.from_account_id = :accountId OR tr.to_account_id = :accountId)
          AND (:fromEpochSec IS NULL OR tr.occurred_at_epoch_sec >= :fromEpochSec)
          AND (:toEpochSec IS NULL OR tr.occurred_at_epoch_sec <= :toEpochSec)
        ORDER BY tr.occurred_at_epoch_sec DESC, tr.created_at_epoch_sec DESC
        LIMIT :limit
    """)
    suspend fun getFiltered(
        userUid: String,
        accountId: String?,
        fromEpochSec: Long?,
        toEpochSec: Long?,
        limit: Int = 100
    ): List<TransferWithDetails>
}
