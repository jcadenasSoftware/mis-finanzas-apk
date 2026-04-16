package com.myfinances.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myfinances.data.local.dao.AccountDao
import com.myfinances.data.local.dao.BudgetDao
import com.myfinances.data.local.dao.CategoryDao
import com.myfinances.data.local.dao.ExchangeRateDao
import com.myfinances.data.local.dao.GoalDao
import com.myfinances.data.local.dao.LoanDao
import com.myfinances.data.local.dao.LoanPaymentDao
import com.myfinances.data.local.dao.TransactionDao
import com.myfinances.data.local.dao.TransferDao
import com.myfinances.data.local.dao.UserSettingsDao
import com.myfinances.data.local.dao.UserDao
import com.myfinances.data.local.entity.AccountEntity
import com.myfinances.data.local.entity.BudgetEntity
import com.myfinances.data.local.entity.CategoryEntity
import com.myfinances.data.local.entity.ExchangeRateEntity
import com.myfinances.data.local.entity.GoalEntity
import com.myfinances.data.local.entity.LoanEntity
import com.myfinances.data.local.entity.LoanPaymentEntity
import com.myfinances.data.local.entity.TransactionEntity
import com.myfinances.data.local.entity.TransferEntity
import com.myfinances.data.local.entity.UserSettingsEntity
import com.myfinances.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransferEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        LoanEntity::class,
        LoanPaymentEntity::class,
        ExchangeRateEntity::class,
        UserSettingsEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transferDao(): TransferDao

    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun loanDao(): LoanDao
    abstract fun loanPaymentDao(): LoanPaymentDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN updated_by TEXT")
                db.execSQL("ALTER TABLE categories ADD COLUMN updated_by TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN updated_by TEXT")
                db.execSQL("ALTER TABLE transfers ADD COLUMN updated_by TEXT")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS budgets (
                        id TEXT NOT NULL,
                        user_uid TEXT NOT NULL,
                        month TEXT NOT NULL,
                        category_id TEXT NOT NULL,
                        currency TEXT NOT NULL,
                        limit_cents INTEGER NOT NULL,
                        created_at_epoch_sec INTEGER NOT NULL,
                        updated_at_epoch_sec INTEGER NOT NULL,
                        updated_by TEXT,
                        PRIMARY KEY(id),
                        FOREIGN KEY(user_uid) REFERENCES users(uid) ON DELETE CASCADE,
                        FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_user_uid ON budgets(user_uid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_category_id ON budgets(category_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_budgets_user_uid_month_currency_category_id ON budgets(user_uid, month, currency, category_id)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS loans (
                        id TEXT NOT NULL,
                        user_uid TEXT NOT NULL,
                        type TEXT NOT NULL,
                        counterparty_name TEXT NOT NULL,
                        account_id TEXT,
                        currency TEXT NOT NULL,
                        principal_cents INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        notes TEXT,
                        created_at_epoch_sec INTEGER NOT NULL,
                        updated_at_epoch_sec INTEGER NOT NULL,
                        updated_by TEXT,
                        PRIMARY KEY(id),
                        FOREIGN KEY(user_uid) REFERENCES users(uid) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loans_user_uid ON loans(user_uid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loans_account_id ON loans(account_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loans_type ON loans(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loans_status ON loans(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loans_currency ON loans(currency)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS loan_payments (
                        id TEXT NOT NULL,
                        user_uid TEXT NOT NULL,
                        loan_id TEXT NOT NULL,
                        account_id TEXT NOT NULL,
                        principal_cents INTEGER NOT NULL,
                        occurred_at_epoch_sec INTEGER NOT NULL,
                        note TEXT,
                        created_at_epoch_sec INTEGER NOT NULL,
                        updated_at_epoch_sec INTEGER NOT NULL,
                        updated_by TEXT,
                        PRIMARY KEY(id),
                        FOREIGN KEY(user_uid) REFERENCES users(uid) ON DELETE CASCADE,
                        FOREIGN KEY(loan_id) REFERENCES loans(id) ON DELETE CASCADE,
                        FOREIGN KEY(account_id) REFERENCES accounts(id) ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loan_payments_user_uid ON loan_payments(user_uid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loan_payments_loan_id ON loan_payments(loan_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loan_payments_account_id ON loan_payments(account_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loan_payments_occurred_at_epoch_sec ON loan_payments(occurred_at_epoch_sec)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS exchange_rates (
                        id TEXT NOT NULL,
                        user_uid TEXT NOT NULL,
                        from_currency TEXT NOT NULL,
                        to_currency TEXT NOT NULL,
                        rate REAL NOT NULL,
                        updated_at_epoch_sec INTEGER NOT NULL,
                        updated_by TEXT,
                        PRIMARY KEY(id),
                        FOREIGN KEY(user_uid) REFERENCES users(uid) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exchange_rates_user_uid ON exchange_rates(user_uid)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_exchange_rates_user_uid_from_currency_to_currency ON exchange_rates(user_uid, from_currency, to_currency)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_settings (
                        user_uid TEXT NOT NULL,
                        country_code TEXT NOT NULL,
                        base_currency TEXT NOT NULL,
                        updated_at_epoch_sec INTEGER NOT NULL,
                        updated_by TEXT,
                        PRIMARY KEY(user_uid),
                        FOREIGN KEY(user_uid) REFERENCES users(uid) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_settings_user_uid ON user_settings(user_uid)")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE loans ADD COLUMN account_id TEXT")
                } catch (_: Exception) {
                }
                try {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_loans_account_id ON loans(account_id)")
                } catch (_: Exception) {
                }
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS goals (
                        id TEXT NOT NULL,
                        user_uid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        currency TEXT NOT NULL,
                        target_cents INTEGER NOT NULL,
                        target_date_epoch_sec INTEGER NOT NULL,
                        account_id TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at_epoch_sec INTEGER NOT NULL,
                        updated_at_epoch_sec INTEGER NOT NULL,
                        updated_by TEXT,
                        PRIMARY KEY(id),
                        FOREIGN KEY(user_uid) REFERENCES users(uid) ON DELETE CASCADE,
                        FOREIGN KEY(account_id) REFERENCES accounts(id) ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_goals_user_uid ON goals(user_uid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_goals_account_id ON goals(account_id)")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN icon_key TEXT")
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN kind TEXT NOT NULL DEFAULT 'BOTH'")
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN icon_key TEXT")
                db.execSQL("ALTER TABLE accounts ADD COLUMN color_hex TEXT")
            }
        }
    }
}
