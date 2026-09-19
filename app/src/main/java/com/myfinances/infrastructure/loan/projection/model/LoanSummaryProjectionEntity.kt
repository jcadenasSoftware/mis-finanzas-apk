package com.jcadenas.xpendz.infrastructure.loan.projection.model

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "loan_summary_projection_v1",
    primaryKeys = ["owner_id", "loan_id"]
)
data class LoanSummaryProjectionEntity(
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "counterparty") val counterparty: String,
    @ColumnInfo(name = "loan_type") val loanType: String,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "default_account_id") val defaultAccountId: String?,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "principal_cents") val principalCents: Long,
    @ColumnInfo(name = "total_paid_cents") val totalPaidCents: Long,
    @ColumnInfo(name = "pending_cents") val pendingCents: Long,
    @ColumnInfo(name = "overpaid_cents") val overpaidCents: Long,
    @ColumnInfo(name = "payment_count") val paymentCount: Int,
    @ColumnInfo(name = "last_payment_at") val lastPaymentAt: Long?,
    @ColumnInfo(name = "progress_percent") val progressPercent: Int,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "closed_at") val closedAt: Long?,
    @ColumnInfo(name = "last_activity") val lastActivity: Long,
    @ColumnInfo(name = "journal_fingerprint") val journalFingerprint: String
)
