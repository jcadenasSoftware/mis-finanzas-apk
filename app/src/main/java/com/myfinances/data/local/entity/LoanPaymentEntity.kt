package com.myfinances.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "loan_payments",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["uid"],
            childColumns = ["user_uid"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loan_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("user_uid"),
        Index("loan_id"),
        Index("account_id"),
        Index("occurred_at_epoch_sec")
    ]
)
data class LoanPaymentEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_uid")
    val userUid: String,
    @ColumnInfo(name = "loan_id")
    val loanId: String,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "principal_cents")
    val principalCents: Long,
    @ColumnInfo(name = "occurred_at_epoch_sec")
    val occurredAtEpochSec: Long,
    val note: String?,
    @ColumnInfo(name = "created_at_epoch_sec")
    val createdAtEpochSec: Long,
    @ColumnInfo(name = "updated_at_epoch_sec")
    val updatedAtEpochSec: Long,
    @ColumnInfo(name = "updated_by")
    val updatedBy: String? = null
)
