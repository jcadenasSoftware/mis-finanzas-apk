package com.jcadenas.xpendz.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "categories",
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
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("user_uid"), Index("parent_id")]
)
@Serializable
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_uid")
    val userUid: String,
    val name: String,
    @ColumnInfo(name = "kind")
    val kind: String = "BOTH",
    @ColumnInfo(name = "icon_key")
    val iconKey: String? = null,
    @ColumnInfo(name = "parent_id")
    val parentId: String?,
    @ColumnInfo(name = "created_at_epoch_sec")
    val createdAtEpochSec: Long,
    @ColumnInfo(name = "updated_at_epoch_sec")
    val updatedAtEpochSec: Long,
    @ColumnInfo(name = "updated_by")
    val updatedBy: String? = null
)
