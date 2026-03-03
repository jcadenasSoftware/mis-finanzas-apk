package com.myfinances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myfinances.data.local.entity.LoanPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanPaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: LoanPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<LoanPaymentEntity>)

    @Update
    suspend fun update(payment: LoanPaymentEntity)

    @Query("SELECT * FROM loan_payments WHERE id = :id")
    suspend fun getById(id: String): LoanPaymentEntity?

    @Query(
        """
        SELECT * FROM loan_payments
        WHERE user_uid = :userUid
          AND loan_id = :loanId
        ORDER BY occurred_at_epoch_sec DESC, created_at_epoch_sec DESC
        """
    )
    fun observeByLoan(userUid: String, loanId: String): Flow<List<LoanPaymentEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(principal_cents), 0)
        FROM loan_payments
        WHERE user_uid = :userUid
          AND loan_id = :loanId
        """
    )
    suspend fun sumPrincipalByLoan(userUid: String, loanId: String): Long

    @Query("SELECT * FROM loan_payments WHERE user_uid = :userUid")
    suspend fun getByUser(userUid: String): List<LoanPaymentEntity>

    @Query("DELETE FROM loan_payments WHERE id = :id")
    suspend fun delete(id: String)
}
