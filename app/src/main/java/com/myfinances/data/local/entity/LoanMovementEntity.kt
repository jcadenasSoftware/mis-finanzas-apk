package com.myfinances.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "loan_movements",
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
        Index("movement_type"),
        Index("occurred_at_epoch_sec"),
        Index(
            value = ["user_uid", "loan_id", "occurred_at_epoch_sec", "created_at_epoch_sec"],
            name = "index_loan_movements_user_uid_loan_id_occurred_created"
        )
    ]
)
@Serializable
data class LoanMovementEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_uid")
    val userUid: String,
    @ColumnInfo(name = "loan_id")
    val loanId: String,
    @ColumnInfo(name = "movement_type")
    val movementType: String,
    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,
    @ColumnInfo(name = "account_id")
    val accountId: String?,
    @ColumnInfo(name = "linked_transaction_id")
    val linkedTransactionId: String?,
    val note: String?,
    @ColumnInfo(name = "occurred_at_epoch_sec")
    val occurredAtEpochSec: Long,
    @ColumnInfo(name = "created_at_epoch_sec")
    val createdAtEpochSec: Long,
    @ColumnInfo(name = "updated_at_epoch_sec")
    val updatedAtEpochSec: Long,
    @ColumnInfo(name = "updated_by")
    val updatedBy: String? = null
)
