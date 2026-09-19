package com.jcadenas.xpendz.infrastructure.loan.projection.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jcadenas.xpendz.infrastructure.loan.projection.model.LoanPaymentProjectionEntity
import com.jcadenas.xpendz.infrastructure.loan.projection.model.LoanSummaryProjectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanProjectionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertPaymentProjection(payment: LoanPaymentProjectionEntity)

    @Query("DELETE FROM loan_payment_projection_v1 WHERE source_event_id = :sourceEventId")
    fun deletePaymentProjection(sourceEventId: String)

    @Query("DELETE FROM loan_payment_projection_v1 WHERE owner_id = :ownerId AND loan_id = :loanId")
    fun deletePaymentProjections(ownerId: String, loanId: String)

    @Query("DELETE FROM loan_summary_projection_v1 WHERE owner_id = :ownerId AND loan_id = :loanId")
    fun deleteSummaryProjection(ownerId: String, loanId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun replaceSummaryProjection(summary: LoanSummaryProjectionEntity)

    @Query("SELECT * FROM loan_payment_projection_v1 WHERE owner_id = :ownerId AND loan_id = :loanId ORDER BY occurred_at")
    fun getPaymentProjections(ownerId: String, loanId: String): List<LoanPaymentProjectionEntity>

    @Query("SELECT * FROM loan_summary_projection_v1 WHERE owner_id = :ownerId AND loan_id = :loanId LIMIT 1")
    fun getSummaryProjection(ownerId: String, loanId: String): LoanSummaryProjectionEntity?

    @Query("SELECT * FROM loan_summary_projection_v1 WHERE owner_id = :ownerId AND (:loanType IS NULL OR loan_type = :loanType) AND (:status IS NULL OR status = :status)")
    fun listSummaryProjections(ownerId: String, loanType: String?, status: String?): List<LoanSummaryProjectionEntity>

    @Query(
        "SELECT s.* FROM loan_summary_projection_v1 s " +
            "LEFT JOIN loan_admin_state_v1 a ON a.owner_id = s.owner_id AND a.loan_id = s.loan_id " +
            "WHERE s.owner_id = :ownerId AND (:loanType IS NULL OR s.loan_type = :loanType) " +
            "AND s.status = 'OPEN' AND s.pending_cents > 0 AND COALESCE(a.archived, 0) = 0"
    )
    fun observeActiveSummaryProjections(ownerId: String, loanType: String?): Flow<List<LoanSummaryProjectionEntity>>
}
