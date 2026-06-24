package com.jcadenas.xpendz.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "goals",
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
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("user_uid"),
        Index("account_id")
    ]
)
@Serializable
data class GoalEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_uid")
    val userUid: String,
    val name: String,
    val currency: String,
    @ColumnInfo(name = "target_cents")
    val targetCents: Long,
    @ColumnInfo(name = "target_date_epoch_sec")
    val targetDateEpochSec: Long,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    val status: String,
    @ColumnInfo(name = "created_at_epoch_sec")
    val createdAtEpochSec: Long,
    @ColumnInfo(name = "updated_at_epoch_sec")
    val updatedAtEpochSec: Long,
    @ColumnInfo(name = "updated_by")
    val updatedBy: String? = null
)
