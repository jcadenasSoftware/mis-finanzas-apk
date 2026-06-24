package com.jcadenas.xpendz.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.data.local.dao.AccountDao
import com.jcadenas.xpendz.data.local.dao.BudgetDao
import com.jcadenas.xpendz.data.local.dao.CategoryDao
import com.jcadenas.xpendz.data.local.dao.ExchangeRateDao
import com.jcadenas.xpendz.data.local.dao.GoalDao
import com.jcadenas.xpendz.data.local.dao.LoanDao
import com.jcadenas.xpendz.data.local.dao.LoanMovementDao
import com.jcadenas.xpendz.data.local.dao.LoanPaymentDao
import com.jcadenas.xpendz.data.local.dao.TransactionDao
import com.jcadenas.xpendz.data.local.dao.TransferDao
import com.jcadenas.xpendz.data.local.dao.UserSettingsDao
import com.jcadenas.xpendz.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "myfinances.db"
        ).addMigrations(
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
            AppDatabase.MIGRATION_11_12
        )
            .build()
    }

    @Provides
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    @Provides
    fun provideAccountDao(database: AppDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTransactionDao(database: AppDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideTransferDao(database: AppDatabase): TransferDao = database.transferDao()

    @Provides
    fun provideBudgetDao(database: AppDatabase): BudgetDao = database.budgetDao()

    @Provides
    fun provideGoalDao(database: AppDatabase): GoalDao = database.goalDao()

    @Provides
    fun provideLoanDao(database: AppDatabase): LoanDao = database.loanDao()

    @Provides
    fun provideLoanPaymentDao(database: AppDatabase): LoanPaymentDao = database.loanPaymentDao()

    @Provides
    fun provideLoanMovementDao(database: AppDatabase): LoanMovementDao = database.loanMovementDao()

    @Provides
    fun provideExchangeRateDao(database: AppDatabase): ExchangeRateDao = database.exchangeRateDao()

    @Provides
    fun provideUserSettingsDao(database: AppDatabase): UserSettingsDao = database.userSettingsDao()

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("myfinances_prefs", Context.MODE_PRIVATE)
    }
}
