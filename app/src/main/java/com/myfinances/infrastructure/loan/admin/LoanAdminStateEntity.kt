package com.jcadenas.xpendz.infrastructure.loan.admin

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "loan_admin_state_v1",
    primaryKeys = ["owner_id", "loan_id"],
    indices = [Index(value = ["owner_id", "archived"], name = "index_loan_admin_state_v1_owner_archived")]
)
data class LoanAdminStateEntity(
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "archived") val archived: Boolean,
    @ColumnInfo(name = "archived_at_epoch_sec") val archivedAtEpochSec: Long?,
    @ColumnInfo(name = "updated_at_epoch_sec") val updatedAtEpochSec: Long,
    @ColumnInfo(name = "updated_by") val updatedBy: String?,
    @ColumnInfo(name = "pending_sync", defaultValue = "0") val pendingSync: Boolean = false
)
