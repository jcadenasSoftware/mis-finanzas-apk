package com.jcadenas.xpendz.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["uid"],
            childColumns = ["user_uid"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("user_uid"),
        Index("from_account_id"),
        Index("to_account_id"),
        Index("occurred_at_epoch_sec")
    ]
)
@Serializable
data class TransferEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_uid")
    val userUid: String,
    @ColumnInfo(name = "from_account_id")
    val fromAccountId: String,
    @ColumnInfo(name = "to_account_id")
    val toAccountId: String,
    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,
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
