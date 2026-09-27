package com.jcadenas.xpendz.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseMigrationTest {
    private lateinit var context: Context
    private val databaseNames = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun migratesVersion12To18AndCreatesLoanPaymentProjectionIndex() {
        val databaseName = trackDatabaseName("migration-12-18-${UUID.randomUUID()}.db")
        createVersion12Database(databaseName)

        val database = openRoomDatabase(databaseName)
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(18, readUserVersion(sqlite))
            assertLoanPaymentProjectionAggregateIndex(sqlite)
        } finally {
            database.close()
        }
    }

    @Test
    fun migratesVersion17To18AndPreservesLoanPaymentProjectionIndex() {
        val databaseName = trackDatabaseName("migration-17-18-${UUID.randomUUID()}.db")
        createVersion17Database(databaseName)

        val database = openRoomDatabase(databaseName)
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(18, readUserVersion(sqlite))
            assertLoanPaymentProjectionAggregateIndex(sqlite)
        } finally {
            database.close()
        }
    }

    private fun trackDatabaseName(databaseName: String): String {
        databaseNames += databaseName
        return databaseName
    }

    private fun openRoomDatabase(databaseName: String): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        databaseName
    ).allowMainThreadQueries().addMigrations(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4,
        AppDatabase.MIGRATION_4_5,
        AppDatabase.MIGRATION_5_6,
        AppDatabase.MIGRATION_6_7,
        AppDatabase.MIGRATION_7_8,
        AppDatabase.MIGRATION_8_9,
        AppDatabase.MIGRATION_9_10,
        AppDatabase.MIGRATION_10_11,
        AppDatabase.MIGRATION_11_12,
        AppDatabase.MIGRATION_12_13,
        AppDatabase.MIGRATION_13_14,
        AppDatabase.MIGRATION_14_15,
        AppDatabase.MIGRATION_15_16,
        AppDatabase.MIGRATION_16_17,
        AppDatabase.MIGRATION_17_18
    ).build()

    private fun createVersion12Database(databaseName: String) {
        createDatabase(databaseName, 12) { db ->
            VERSION_12_SQL.forEach(db::execSQL)
        }
    }

    private fun createVersion17Database(databaseName: String) {
        createDatabase(databaseName, 17) { db ->
            VERSION_12_SQL.forEach(db::execSQL)
            VERSION_13_TO_17_SQL.forEach(db::execSQL)
        }
    }

    private fun createDatabase(
        databaseName: String,
        version: Int,
        schemaBuilder: (SQLiteDatabase) -> Unit
    ) {
        context.deleteDatabase(databaseName)
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        database.beginTransaction()
        try {
            schemaBuilder(database)
            database.execSQL("PRAGMA user_version = $version")
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
            database.close()
        }
    }

    private fun assertLoanPaymentProjectionAggregateIndex(db: SupportSQLiteDatabase) {
        val indices = mutableMapOf<String, Boolean>()
        db.query("PRAGMA index_list(`loan_payment_projection_v1`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val uniqueIndex = cursor.getColumnIndexOrThrow("unique")
            while (cursor.moveToNext()) {
                indices[cursor.getString(nameIndex)] = cursor.getInt(uniqueIndex) == 1
            }
        }
        assertTrue(indices.containsKey(INDEX_NAME))
        assertEquals(false, indices[INDEX_NAME])

        val columns = mutableListOf<Pair<Int, String>>()
        db.query("PRAGMA index_info(`$INDEX_NAME`)").use { cursor ->
            val seqnoIndex = cursor.getColumnIndexOrThrow("seqno")
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                columns += cursor.getInt(seqnoIndex) to cursor.getString(nameIndex)
            }
        }
        assertEquals(
            listOf("owner_id", "loan_id", "occurred_at"),
            columns.sortedBy { it.first }.map { it.second }
        )
    }

    private fun readUserVersion(db: SupportSQLiteDatabase): Int =
        db.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    companion object {
        private const val INDEX_NAME = "index_loan_payment_projection_v1_aggregate"

        private val VERSION_12_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `users` (`uid` TEXT NOT NULL, `email` TEXT NOT NULL, `created_at_epoch_sec` INTEGER NOT NULL, `updated_at_epoch_sec` INTEGER NOT NULL, PRIMARY KEY(`uid`))",
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` TEXT NOT NULL, `user_uid` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `currency` TEXT NOT NULL, `icon_key` TEXT, `color_hex` TEXT, `created_at_epoch_sec` INTEGER NOT NULL, `updated_at_epoch_sec` INTEGER NOT NULL, `updated_by` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`user_uid`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_accounts_user_uid` ON `accounts` (`user_uid`)",
            "CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `user_uid` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `icon_key` TEXT, `parent_id` TEXT, `created_at_epoch_sec` INTEGER NOT NULL, `updated_at_epoch_sec` INTEGER NOT NULL, `updated_by` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`user_uid`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`parent_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_categories_user_uid` ON `categories` (`user_uid`)",
            "CREATE INDEX IF NOT EXISTS `index_categories_parent_id` ON `categories` (`parent_id`)",
            "CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `user_uid` TEXT NOT NULL, `account_id` TEXT NOT NULL, `category_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `amount_cents` INTEGER NOT NULL, `occurred_at_epoch_sec` INTEGER NOT NULL, `note` TEXT, `created_at_epoch_sec` INTEGER NOT NULL, `updated_at_epoch_sec` INTEGER NOT NULL, `updated_by` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`user_uid`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_transactions_user_uid` ON `transactions` (`user_uid`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_account_id` ON `transactions` (`account_id`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_category_id` ON `transactions` (`category_id`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_occurred_at_epoch_sec` ON `transactions` (`occurred_at_epoch_sec`)",
            "CREATE TABLE IF NOT EXISTS `transfers` (`id` TEXT NOT NULL, `user_uid` TEXT NOT NULL, `from_account_id` TEXT NOT NULL, `to_account_id` TEXT NOT NULL, `amount_cents` INTEGER NOT NULL, `occurred_at_epoch_sec` INTEGER NOT NULL, `note` TEXT, `created_at_epoch_sec` INTEGER NOT NULL, `updated_at_epoch_sec` INTEGER NOT NULL, `updated_by` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`user_uid`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`from_account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`to_account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_transfers_user_uid` ON `transfers` (`user_uid`)",
            "CREATE INDEX IF NOT EXISTS `index_transfers_from_account_id` ON `transfers` (`from_account_id`)",
            "CREATE INDEX IF NOT EXISTS `index_transfers_to_account_id` ON `transfers` (`to_account_id`)",
            "CREATE INDEX IF NOT EXISTS `index_transfers_occurred_at_epoch_sec` ON `transfers` (`occurred_at_epoch_sec`)",
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` TEXT NOT NULL, `user_uid` TEXT NOT NULL, `month` TEXT NOT NULL, `category_id` TEXT NOT NULL, `currency` TEXT NOT NULL, `limit_cents` INTEGER NOT NULL, `created_at_epoch_sec` INTEGER NOT NULL, `updated_at_epoch_sec` INTEGER NOT NULL, `updated_by` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`user_uid`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_budgets_user_uid` ON `budgets` (`user_uid`)",
            "CREATE INDEX IF NOT EXISTS `index_budgets_category_id` ON `budgets` (`category_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_user_uid_month_currency_category_id` ON `budgets` (`user_uid`, `month`, `currency`, `category_id`)",
            "CREATE TABLE IF NOT EXISTS `goals` (`id` TEXT NOT NULL, `user_uid` TEXT NOT NULL, `name` TEXT NOT NULL, `currency` TEXT NOT NULL, `target_cents` INTEGER NOT NULL, `target_date_epoch_sec` INTEGER NOT NULL, `account_id` TEXT NOT NULL, `status` TEXT NOT NULL, `created_at_epoch_sec` INTEGER NOT NULL, `updated_at_epoch_sec` INTEGER NOT NULL, `updated_by` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`user_uid`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_goals_user_uid` ON `goals` (`user_uid`)",
            "CREATE INDEX IF NOT EXISTS `index_goals_account_id` ON `goals` (`account_id`)",
            "CREATE TABLE IF NOT EXISTS `loans` (`id` TEXT NOT NULL, `user_uid` TEXT NOT NULL, `type` TEXT NOT NULL, `counterparty_name` TEXT NOT NULL, `account_id` TEXT, `currency` TEXT NOT NULL, `principal_cents` INTEGER NOT NULL, `status` TEXT NOT NULL, `notes` TEXT, `created_at_epoch_sec` INTEGER NOT NULL, `updated_at_epoch_sec` INTEGER NOT NULL, `updated_by` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`user_uid`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_loans_user_uid` ON `loans` (`user_uid`)",
            "CREATE INDEX IF NOT EXISTS `index_loans_account_id` ON `loans` (`account_id`)",
            "CREATE INDEX IF NOT EXISTS `index_loans_type` ON `loans` (`type`)",
            "CREATE INDEX IF NOT EXISTS `index_loans_status` ON `loans` (`status`)",
            "CREATE INDEX IF NOT EXISTS `index_loans_currency` ON `loans` (`currency`)",
            "CREATE TABLE IF NOT EXISTS `loan_payments` (`id` TEXT NOT NULL, `user_uid` TEXT NOT NULL, `loan_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `principal_cents` INTEGER NOT NULL, `occurred_at_epoch_sec` INTEGER NOT NULL, `note` TEXT, `linked_transaction_id` TEXT, `created_at_epoch_sec` INTEGER NOT NULL, `updated_at_epoch_sec` INTEGER NOT NULL, `updated_by` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`user_uid`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`loan_id`) REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_loan_payments_user_uid` ON `loan_payments` (`user_uid`)",
            "CREATE INDEX IF NOT EXISTS `index_loan_payments_loan_id` ON `loan_payments` (`loan_id`)",
            "CREATE INDEX IF NOT EXISTS `index_loan_payments_account_id` ON `loan_payments` (`account_id`)",
            "CREATE INDEX IF NOT EXISTS `index_loan_payments_occurred_at_epoch_sec` ON `loan_payments` (`occurred_at_epoch_sec`)",
            "CREATE INDEX IF NOT EXISTS `index_loan_payments_linked_transaction_id` ON `loan_payments` (`linked_transaction_id`)",
            "CREATE TABLE IF NOT EXISTS `loan_movements` (`id` TEXT NOT NULL, `user_uid` TEXT NOT NULL, `loan_id` TEXT NOT NULL, `movement_type` TEXT NOT NULL, `amount_cents` INTEGER NOT NULL, `account_id` TEXT, `linked_transaction_id` TEXT, `note` TEXT, `occurred_at_epoch_sec` INTEGER NOT NULL, `created_at_epoch_sec` INTEGER NOT NULL, `updated_at_epoch_sec` INTEGER NOT NULL, `updated_by` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`user_uid`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`loan_id`) REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_loan_movements_user_uid` ON `loan_movements` (`user_uid`)",
            "CREATE INDEX IF NOT EXISTS `index_loan_movements_loan_id` ON `loan_movements` (`loan_id`)",
            "CREATE INDEX IF NOT EXISTS `index_loan_movements_account_id` ON `loan_movements` (`account_id`)",
            "CREATE INDEX IF NOT EXISTS `index_loan_movements_movement_type` ON `loan_movements` (`movement_type`)",
            "CREATE INDEX IF NOT EXISTS `index_loan_movements_occurred_at_epoch_sec` ON `loan_movements` (`occurred_at_epoch_sec`)",
            "CREATE INDEX IF NOT EXISTS `index_loan_movements_linked_transaction_id` ON `loan_movements` (`linked_transaction_id`)",
            "CREATE INDEX IF NOT EXISTS `index_loan_movements_user_uid_loan_id_occurred_created` ON `loan_movements` (`user_uid`, `loan_id`, `occurred_at_epoch_sec`, `created_at_epoch_sec`)",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`id` TEXT NOT NULL, `user_uid` TEXT NOT NULL, `from_currency` TEXT NOT NULL, `to_currency` TEXT NOT NULL, `rate` REAL NOT NULL, `updated_at_epoch_sec` INTEGER NOT NULL, `updated_by` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`user_uid`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_exchange_rates_user_uid` ON `exchange_rates` (`user_uid`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_rates_user_uid_from_currency_to_currency` ON `exchange_rates` (`user_uid`, `from_currency`, `to_currency`)",
            "CREATE TABLE IF NOT EXISTS `user_settings` (`user_uid` TEXT NOT NULL, `country_code` TEXT NOT NULL, `base_currency` TEXT NOT NULL, `updated_at_epoch_sec` INTEGER NOT NULL, `updated_by` TEXT, PRIMARY KEY(`user_uid`), FOREIGN KEY(`user_uid`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_user_settings_user_uid` ON `user_settings` (`user_uid`)"
        )

        private val VERSION_13_TO_17_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS loan_journal_v1 (event_id TEXT NOT NULL, operation_id TEXT NOT NULL, loan_id TEXT NOT NULL, owner_id TEXT NOT NULL, event_type TEXT NOT NULL, event_schema_version INTEGER NOT NULL, amount_cents INTEGER, account_id TEXT, transaction_id TEXT, note TEXT, occurred_at INTEGER NOT NULL, recorded_at INTEGER NOT NULL, actor_id TEXT, origin_id TEXT, payload_loan_type TEXT, payload_counterparty_name TEXT, payload_currency TEXT, payload_default_account_id TEXT, payload_notes TEXT, payload_legacy_direction TEXT, payload_legacy_source TEXT, payload_reason TEXT, payload_target_event_id TEXT, metadata_counterparty_present INTEGER NOT NULL, metadata_counterparty_value TEXT, metadata_account_present INTEGER NOT NULL, metadata_account_value TEXT, metadata_notes_present INTEGER NOT NULL, metadata_notes_value TEXT, PRIMARY KEY(event_id))",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_loan_journal_v1_owner_operation ON loan_journal_v1(owner_id, operation_id)",
            "CREATE INDEX IF NOT EXISTS index_loan_journal_v1_aggregate ON loan_journal_v1(owner_id, loan_id, occurred_at, recorded_at, event_id)",
            "CREATE INDEX IF NOT EXISTS index_loan_journal_v1_event ON loan_journal_v1(owner_id, event_id)",
            "CREATE TABLE IF NOT EXISTS loan_snapshots_v1 (loan_id TEXT NOT NULL, owner_id TEXT NOT NULL, loan_type TEXT NOT NULL, counterparty_name TEXT NOT NULL, currency TEXT NOT NULL, default_account_id TEXT, notes TEXT, principal_cents INTEGER NOT NULL, total_paid_cents INTEGER NOT NULL, net_balance_cents INTEGER NOT NULL, pending_cents INTEGER NOT NULL, overpaid_cents INTEGER NOT NULL, status TEXT NOT NULL, closed_at INTEGER, last_activity_at INTEGER NOT NULL, journal_event_count INTEGER NOT NULL, journal_fingerprint TEXT NOT NULL, reducer_version INTEGER NOT NULL, PRIMARY KEY(owner_id, loan_id))",
            "CREATE TABLE IF NOT EXISTS loan_payment_projection_v1 (source_event_id TEXT NOT NULL, operation_id TEXT NOT NULL, owner_id TEXT NOT NULL, loan_id TEXT NOT NULL, account_id TEXT, transaction_id TEXT, occurred_at INTEGER NOT NULL, amount_cents INTEGER NOT NULL, direction TEXT, note TEXT, PRIMARY KEY(source_event_id))",
            "CREATE INDEX IF NOT EXISTS index_loan_payment_projection_v1_aggregate ON loan_payment_projection_v1(owner_id, loan_id, occurred_at)",
            "CREATE TABLE IF NOT EXISTS loan_summary_projection_v1 (loan_id TEXT NOT NULL, owner_id TEXT NOT NULL, counterparty TEXT NOT NULL, loan_type TEXT NOT NULL, currency TEXT NOT NULL, principal_cents INTEGER NOT NULL, total_paid_cents INTEGER NOT NULL, pending_cents INTEGER NOT NULL, overpaid_cents INTEGER NOT NULL, status TEXT NOT NULL, closed_at INTEGER, last_activity INTEGER NOT NULL, journal_fingerprint TEXT NOT NULL, PRIMARY KEY(owner_id, loan_id))",
            "ALTER TABLE loan_summary_projection_v1 ADD COLUMN default_account_id TEXT",
            "ALTER TABLE loan_summary_projection_v1 ADD COLUMN notes TEXT",
            "ALTER TABLE loan_summary_projection_v1 ADD COLUMN payment_count INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE loan_summary_projection_v1 ADD COLUMN last_payment_at INTEGER",
            "ALTER TABLE loan_summary_projection_v1 ADD COLUMN progress_percent INTEGER NOT NULL DEFAULT 0",
            "CREATE TABLE IF NOT EXISTS loan_admin_state_v1 (loan_id TEXT NOT NULL, owner_id TEXT NOT NULL, archived INTEGER NOT NULL, archived_at_epoch_sec INTEGER, updated_at_epoch_sec INTEGER NOT NULL, updated_by TEXT, PRIMARY KEY(owner_id, loan_id))",
            "CREATE INDEX IF NOT EXISTS index_loan_admin_state_v1_owner_archived ON loan_admin_state_v1(owner_id, archived)",
            "ALTER TABLE loan_admin_state_v1 ADD COLUMN pending_sync INTEGER NOT NULL DEFAULT 0"
        )
    }
}
