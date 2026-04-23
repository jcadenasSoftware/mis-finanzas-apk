package com.myfinances.di

import android.content.Context
import androidx.room.Room
import com.myfinances.data.local.AppDatabase
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
            AppDatabase.MIGRATION_8_9
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
    fun provideExchangeRateDao(database: AppDatabase): ExchangeRateDao = database.exchangeRateDao()

    @Provides
    fun provideUserSettingsDao(database: AppDatabase): UserSettingsDao = database.userSettingsDao()
}
