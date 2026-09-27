package com.jcadenas.xpendz.infrastructure.loan.projection.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "loan_payment_projection_v1",
    primaryKeys = ["source_event_id"],
    indices = [Index(value = ["owner_id", "loan_id", "occurred_at"], name = "index_loan_payment_projection_v1_aggregate")]
)
data class LoanPaymentProjectionEntity(
    @ColumnInfo(name = "source_event_id") val sourceEventId: String,
    @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "account_id") val accountId: String?,
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "direction") val direction: String?,
    @ColumnInfo(name = "note") val note: String?
)
