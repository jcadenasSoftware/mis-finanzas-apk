package com.myfinances.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exchange_rates",
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
        Index(value = ["user_uid", "from_currency", "to_currency"], unique = true)
    ]
)
data class ExchangeRateEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_uid")
    val userUid: String,
    @ColumnInfo(name = "from_currency")
    val fromCurrency: String,
    @ColumnInfo(name = "to_currency")
    val toCurrency: String,
    val rate: Double,
    @ColumnInfo(name = "updated_at_epoch_sec")
    val updatedAtEpochSec: Long,
    @ColumnInfo(name = "updated_by")
    val updatedBy: String? = null
)
