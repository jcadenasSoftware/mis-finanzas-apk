package com.jcadenas.xpendz.infrastructure.loan.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "loan_journal_v1",
    indices = [
        Index(value = ["owner_id", "operation_id"], unique = true, name = "index_loan_journal_v1_owner_operation"),
        Index(value = ["owner_id", "loan_id", "occurred_at", "recorded_at", "event_id"], name = "index_loan_journal_v1_aggregate"),
        Index(value = ["owner_id", "event_id"], name = "index_loan_journal_v1_event")
    ]
)
data class CanonicalLoanEventEntity(
    @PrimaryKey @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "event_schema_version") val eventSchemaVersion: Int,
    @ColumnInfo(name = "amount_cents") val amountCents: Long?,
    @ColumnInfo(name = "account_id") val accountId: String?,
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    val note: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    @ColumnInfo(name = "actor_id") val actorId: String?,
    @ColumnInfo(name = "origin_id") val originId: String?,
    @ColumnInfo(name = "payload_loan_type") val payloadLoanType: String?,
    @ColumnInfo(name = "payload_counterparty_name") val payloadCounterpartyName: String?,
    @ColumnInfo(name = "payload_currency") val payloadCurrency: String?,
    @ColumnInfo(name = "payload_default_account_id") val payloadDefaultAccountId: String?,
    @ColumnInfo(name = "payload_notes") val payloadNotes: String?,
    @ColumnInfo(name = "payload_legacy_direction") val payloadLegacyDirection: String?,
    @ColumnInfo(name = "payload_legacy_source") val payloadLegacySource: String?,
    @ColumnInfo(name = "payload_reason") val payloadReason: String?,
    @ColumnInfo(name = "payload_target_event_id") val payloadTargetEventId: String?,
    @ColumnInfo(name = "metadata_counterparty_present") val metadataCounterpartyPresent: Boolean,
    @ColumnInfo(name = "metadata_counterparty_value") val metadataCounterpartyValue: String?,
    @ColumnInfo(name = "metadata_account_present") val metadataAccountPresent: Boolean,
    @ColumnInfo(name = "metadata_account_value") val metadataAccountValue: String?,
    @ColumnInfo(name = "metadata_notes_present") val metadataNotesPresent: Boolean,
    @ColumnInfo(name = "metadata_notes_value") val metadataNotesValue: String?
)
