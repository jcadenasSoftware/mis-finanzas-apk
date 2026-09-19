package com.jcadenas.xpendz.infrastructure.loan.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jcadenas.xpendz.infrastructure.loan.model.CanonicalLoanEventEntity
import com.jcadenas.xpendz.infrastructure.loan.model.CanonicalLoanSnapshotEntity

@Dao
interface CanonicalLoanDao {
    @Query(
        "SELECT * FROM loan_journal_v1 WHERE owner_id = :ownerId AND loan_id = :loanId " +
            "ORDER BY occurred_at, recorded_at, event_id"
    )
    fun getJournal(ownerId: String, loanId: String): List<CanonicalLoanEventEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertEvent(event: CanonicalLoanEventEntity)

    @Query("SELECT * FROM loan_journal_v1 WHERE owner_id = :ownerId AND operation_id = :operationId LIMIT 1")
    fun findByOperationId(ownerId: String, operationId: String): CanonicalLoanEventEntity?

    @Query("SELECT * FROM loan_journal_v1 WHERE owner_id = :ownerId AND event_id = :eventId LIMIT 1")
    fun findByEventId(ownerId: String, eventId: String): CanonicalLoanEventEntity?

    @Query(
        "SELECT * FROM loan_journal_v1 WHERE owner_id = :ownerId AND event_type = 'REVERSAL' " +
            "AND payload_target_event_id IS NOT NULL"
    )
    fun reversalEvents(ownerId: String): List<CanonicalLoanEventEntity>

    @Query(
        "SELECT * FROM loan_journal_v1 WHERE owner_id = :ownerId AND event_type = 'PAYMENT' " +
            "AND transaction_id IS NOT NULL"
    )
    fun paymentEventsWithTransaction(ownerId: String): List<CanonicalLoanEventEntity>

    @Query(
        "SELECT DISTINCT transaction_id FROM loan_journal_v1 " +
            "WHERE owner_id = :ownerId AND loan_id != :loanId AND transaction_id IS NOT NULL"
    )
    fun journalTransactionIdsOfOtherLoans(ownerId: String, loanId: String): List<String>

    @Query("SELECT * FROM loan_snapshots_v1 WHERE owner_id = :ownerId AND loan_id = :loanId LIMIT 1")
    fun loadSnapshot(ownerId: String, loanId: String): CanonicalLoanSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun replaceSnapshot(snapshot: CanonicalLoanSnapshotEntity)

    @Query("DELETE FROM loan_journal_v1 WHERE owner_id = :ownerId AND loan_id = :loanId")
    fun deleteJournal(ownerId: String, loanId: String)

    @Query("DELETE FROM loan_snapshots_v1 WHERE owner_id = :ownerId AND loan_id = :loanId")
    fun deleteSnapshot(ownerId: String, loanId: String)
}
