package com.jcadenas.xpendz.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jcadenas.xpendz.data.local.entity.ObligationSettlementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ObligationSettlementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(settlement: ObligationSettlementEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(settlements: List<ObligationSettlementEntity>)

    @Update
    suspend fun update(settlement: ObligationSettlementEntity)

    @Query("SELECT * FROM obligation_settlements WHERE id = :id")
    suspend fun getById(id: String): ObligationSettlementEntity?

    @Query(
        """
        SELECT * FROM obligation_settlements
        WHERE linked_transaction_id = :linkedTransactionId
        LIMIT 1
        """
    )
    suspend fun getByLinkedTransactionId(linkedTransactionId: String): ObligationSettlementEntity?

    @Query(
        """
        SELECT * FROM obligation_settlements
        WHERE obligation_id = :obligationId
        ORDER BY occurred_at_epoch_sec ASC, created_at_epoch_sec ASC
        """
    )
    fun observeByObligation(obligationId: String): Flow<List<ObligationSettlementEntity>>

    @Query(
        """
        SELECT * FROM obligation_settlements
        WHERE obligation_id = :obligationId
        ORDER BY occurred_at_epoch_sec ASC, created_at_epoch_sec ASC
        """
    )
    suspend fun getByObligation(obligationId: String): List<ObligationSettlementEntity>

    @Query(
        """
        SELECT * FROM obligation_settlements
        WHERE user_uid = :userUid
        ORDER BY occurred_at_epoch_sec DESC, created_at_epoch_sec DESC
        """
    )
    suspend fun getByUser(userUid: String): List<ObligationSettlementEntity>

    @Query(
        """
        SELECT COALESCE(SUM(amount_cents), 0)
        FROM obligation_settlements
        WHERE obligation_id = :obligationId
        """
    )
    suspend fun getTotalSettledCents(obligationId: String): Long

    @Query("DELETE FROM obligation_settlements WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM obligation_settlements WHERE user_uid = :userUid")
    suspend fun deleteAllByUser(userUid: String)
}
