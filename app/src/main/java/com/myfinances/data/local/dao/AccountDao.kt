package com.jcadenas.xpendz.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE user_uid = :userUid ORDER BY name")
    fun observeByUser(userUid: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE user_uid = :userUid ORDER BY name")
    suspend fun getByUser(userUid: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT MAX(updated_at_epoch_sec) FROM accounts WHERE user_uid = :userUid")
    suspend fun getMaxUpdatedAtEpochSec(userUid: String): Long?

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM accounts WHERE user_uid = :userUid")
    suspend fun deleteAllByUser(userUid: String)

    @Query("""
        SELECT (
            COALESCE((
                SELECT SUM(
                    CASE
                        WHEN kind IN ('INCOME', 'LOAN_BORROWED_IN', 'LOAN_BORROWED_TOPUP', 'LOAN_BORROWED_CORRECTION', 'LOAN_LENT_CORRECTION_IN', 'LOAN_BORROWED_CORRECTION_IN', 'LOAN_REPAYMENT_PRINCIPAL_IN') THEN amount_cents
                        WHEN kind IN ('EXPENSE', 'LOAN_LENT_OUT', 'LOAN_LENT_TOPUP', 'LOAN_LENT_CORRECTION', 'LOAN_LENT_CORRECTION_OUT', 'LOAN_BORROWED_CORRECTION_OUT', 'LOAN_REPAYMENT_PRINCIPAL_OUT') THEN -amount_cents
                        ELSE 0
                    END
                )
                FROM transactions
                WHERE user_uid = :userUid AND account_id = :accountId
            ), 0)
            + COALESCE((SELECT SUM(amount_cents) FROM transfers WHERE user_uid = :userUid AND to_account_id = :accountId), 0)
            - COALESCE((SELECT SUM(amount_cents) FROM transfers WHERE user_uid = :userUid AND from_account_id = :accountId), 0)
        ) AS balance
    """)
    suspend fun computeBalanceCents(userUid: String, accountId: String): Long

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM transactions WHERE user_uid = :userUid AND account_id = :accountId
            UNION
            SELECT 1 FROM transfers WHERE user_uid = :userUid AND (from_account_id = :accountId OR to_account_id = :accountId)
        )
    """)
    suspend fun hasMovements(userUid: String, accountId: String): Boolean
}
