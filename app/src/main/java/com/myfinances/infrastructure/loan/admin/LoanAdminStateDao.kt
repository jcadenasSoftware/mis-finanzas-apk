package com.jcadenas.xpendz.infrastructure.loan.admin

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LoanAdminStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: LoanAdminStateEntity)

    @Query("SELECT * FROM loan_admin_state_v1 WHERE owner_id = :ownerId AND loan_id = :loanId LIMIT 1")
    suspend fun getByLoan(ownerId: String, loanId: String): LoanAdminStateEntity?

    @Query("SELECT * FROM loan_admin_state_v1 WHERE owner_id = :ownerId AND pending_sync = 1")
    suspend fun listPendingForSync(ownerId: String): List<LoanAdminStateEntity>

    @Query("UPDATE loan_admin_state_v1 SET pending_sync = 0 WHERE owner_id = :ownerId AND loan_id = :loanId")
    suspend fun markSynced(ownerId: String, loanId: String)
}
