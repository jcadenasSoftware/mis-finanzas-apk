package com.jcadenas.xpendz.infrastructure.loan.model

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "loan_snapshots_v1",
    primaryKeys = ["owner_id", "loan_id"]
)
data class CanonicalLoanSnapshotEntity(
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "loan_type") val loanType: String,
    @ColumnInfo(name = "counterparty_name") val counterpartyName: String,
    val currency: String,
    @ColumnInfo(name = "default_account_id") val defaultAccountId: String?,
    val notes: String?,
    @ColumnInfo(name = "principal_cents") val principalCents: Long,
    @ColumnInfo(name = "total_paid_cents") val totalPaidCents: Long,
    @ColumnInfo(name = "net_balance_cents") val netBalanceCents: Long,
    @ColumnInfo(name = "pending_cents") val pendingCents: Long,
    @ColumnInfo(name = "overpaid_cents") val overpaidCents: Long,
    val status: String,
    @ColumnInfo(name = "closed_at") val closedAt: Long?,
    @ColumnInfo(name = "last_activity_at") val lastActivityAt: Long,
    @ColumnInfo(name = "journal_event_count") val journalEventCount: Int,
    @ColumnInfo(name = "journal_fingerprint") val journalFingerprint: String,
    @ColumnInfo(name = "reducer_version") val reducerVersion: Int
)
