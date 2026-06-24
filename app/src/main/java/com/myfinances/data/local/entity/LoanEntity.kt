package com.jcadenas.xpendz.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["uid"],
            childColumns = ["user_uid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("user_uid"),
        Index("account_id"),
        Index("type"),
        Index("status"),
        Index("currency")
    ]
)
@Serializable
data class LoanEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_uid")
    val userUid: String,
    val type: String,
    @ColumnInfo(name = "counterparty_name")
    val counterpartyName: String,
    @ColumnInfo(name = "account_id")
    val accountId: String?,
    val currency: String,
    @ColumnInfo(name = "principal_cents")
    val principalCents: Long,
    val status: String,
    val notes: String?,
    @ColumnInfo(name = "created_at_epoch_sec")
    val createdAtEpochSec: Long,
    @ColumnInfo(name = "updated_at_epoch_sec")
    val updatedAtEpochSec: Long,
    @ColumnInfo(name = "updated_by")
    val updatedBy: String? = null
)
