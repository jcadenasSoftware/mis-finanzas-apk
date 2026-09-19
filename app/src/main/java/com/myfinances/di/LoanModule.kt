package com.jcadenas.xpendz.di

import com.jcadenas.xpendz.application.loan.LoanApplicationService
import com.jcadenas.xpendz.application.loan.LoanCommandFactory
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.data.repository.FirestoreLoanAdminStateRemotePublisher
import com.jcadenas.xpendz.data.repository.LoanAdminStateRemotePublisher
import com.jcadenas.xpendz.data.repository.LoanMovementRepository
import com.jcadenas.xpendz.data.repository.LoanPaymentRepository
import com.jcadenas.xpendz.data.repository.TransactionRepository
import com.jcadenas.xpendz.infrastructure.loan.migration.ReversedLoanMovementStore
import com.jcadenas.xpendz.infrastructure.loan.migration.ReversedLoanPaymentStore
import com.jcadenas.xpendz.infrastructure.loan.migration.ReversedLoanTransactionStore
import com.jcadenas.xpendz.domain.loan.projection.LoanProjectionQueryRepository
import com.jcadenas.xpendz.domain.loan.projection.LoanProjector
import com.jcadenas.xpendz.domain.loan.reducer.DefaultLoanReducer
import com.jcadenas.xpendz.domain.loan.reducer.LoanReducer
import com.jcadenas.xpendz.domain.loan.repository.LoanRepository
import com.jcadenas.xpendz.domain.loan.service.DefaultLoanAggregateService
import com.jcadenas.xpendz.domain.loan.service.LoanAggregateService
import com.jcadenas.xpendz.infrastructure.loan.projection.room.DefaultLoanProjector
import com.jcadenas.xpendz.infrastructure.loan.projection.room.LoanProjectionDao
import com.jcadenas.xpendz.infrastructure.loan.projection.room.RoomLoanProjectionQueryRepository
import com.jcadenas.xpendz.infrastructure.loan.room.CanonicalLoanDao
import com.jcadenas.xpendz.infrastructure.loan.room.RoomLoanAggregateExecutor
import com.jcadenas.xpendz.infrastructure.loan.room.RoomLoanRepositoryAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoanModule {

    @Provides
    @Singleton
    fun provideLoanRepository(
        database: AppDatabase,
        canonicalLoanDao: CanonicalLoanDao
    ): LoanRepository = RoomLoanRepositoryAdapter(database, canonicalLoanDao)

    @Provides
    @Singleton
    fun provideLoanAdminStateRemotePublisher(
        publisher: FirestoreLoanAdminStateRemotePublisher
    ): LoanAdminStateRemotePublisher = publisher

    @Provides
    @Singleton
    fun provideLoanReducer(): LoanReducer = DefaultLoanReducer()

    @Provides
    @Singleton
    fun provideDefaultLoanAggregateService(
        repository: LoanRepository,
        reducer: LoanReducer
    ): DefaultLoanAggregateService = DefaultLoanAggregateService(repository, reducer)

    @Provides
    @Singleton
    fun provideLoanAggregateService(
        database: AppDatabase,
        delegate: DefaultLoanAggregateService
    ): LoanAggregateService = RoomLoanAggregateExecutor(database, delegate)

    @Provides
    @Singleton
    fun provideLoanProjector(
        database: AppDatabase,
        loanProjectionDao: LoanProjectionDao
    ): LoanProjector = DefaultLoanProjector(database, loanProjectionDao)

    @Provides
    @Singleton
    fun provideLoanProjectionQueryRepository(
        loanProjectionDao: LoanProjectionDao
    ): LoanProjectionQueryRepository = RoomLoanProjectionQueryRepository(loanProjectionDao)

    @Provides
    @Singleton
    fun provideLoanApplicationService(
        aggregate: LoanAggregateService,
        projector: LoanProjector
    ): LoanApplicationService = LoanApplicationService(aggregate, projector)

    @Provides
    @Singleton
    fun provideReversedLoanPaymentStore(
        repository: LoanPaymentRepository
    ): ReversedLoanPaymentStore = repository

    @Provides
    @Singleton
    fun provideReversedLoanMovementStore(
        repository: LoanMovementRepository
    ): ReversedLoanMovementStore = repository

    @Provides
    @Singleton
    fun provideReversedLoanTransactionStore(
        repository: TransactionRepository
    ): ReversedLoanTransactionStore = repository

    @Provides
    @Singleton
    fun provideLoanCommandFactory(): LoanCommandFactory = LoanCommandFactory()
}
