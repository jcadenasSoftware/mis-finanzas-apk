package com.myfinances.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myfinances.data.local.dao.AccountDao
import com.myfinances.data.local.dao.CategoryDao
import com.myfinances.data.local.dao.TransactionDao
import com.myfinances.data.local.dao.TransferDao
import com.myfinances.data.local.dao.UserDao
import com.myfinances.data.local.entity.AccountEntity
import com.myfinances.data.local.entity.CategoryEntity
import com.myfinances.data.local.entity.TransactionEntity
import com.myfinances.data.local.entity.TransferEntity
import com.myfinances.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransferEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transferDao(): TransferDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN updated_by TEXT")
                db.execSQL("ALTER TABLE categories ADD COLUMN updated_by TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN updated_by TEXT")
                db.execSQL("ALTER TABLE transfers ADD COLUMN updated_by TEXT")
            }
        }
    }
}
