package com.jcadenas.xpendz.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "obligations",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["uid"],
            childColumns = ["user_uid"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["obligation_category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("user_uid"),
        Index("type"),
        Index("obligation_category_id"),
        Index("issued_at_epoch_sec"),
        Index("due_at_epoch_sec"),
        Index("updated_at_epoch_sec")
    ]
)
@Serializable
data class ObligationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_uid")
    val userUid: String,
    val type: String,
    val title: String,
    @ColumnInfo(name = "counterparty_name")
    val counterpartyName: String,
    val notes: String?,
    val reference: String?,
    @ColumnInfo(name = "obligation_category_id")
    val obligationCategoryId: String?,
    val currency: String,
    @ColumnInfo(name = "original_amount_cents")
    val originalAmountCents: Long,
    @ColumnInfo(name = "issued_at_epoch_sec")
    val issuedAtEpochSec: Long,
    @ColumnInfo(name = "due_at_epoch_sec")
    val dueAtEpochSec: Long?,
    @ColumnInfo(name = "cancelled_at_epoch_sec")
    val cancelledAtEpochSec: Long?,
    @ColumnInfo(name = "created_at_epoch_sec")
    val createdAtEpochSec: Long,
    @ColumnInfo(name = "updated_at_epoch_sec")
    val updatedAtEpochSec: Long,
    @ColumnInfo(name = "updated_by")
    val updatedBy: String? = null
) {
    companion object {
        const val TYPE_RECEIVABLE = "POR_COBRAR"
        const val TYPE_PAYABLE = "POR_PAGAR"

        fun normalizeType(type: String?): String {
            return when (type?.trim()?.uppercase()) {
                TYPE_RECEIVABLE -> TYPE_RECEIVABLE
                TYPE_PAYABLE -> TYPE_PAYABLE
                else -> throw IllegalArgumentException("type")
            }
        }
    }
}
