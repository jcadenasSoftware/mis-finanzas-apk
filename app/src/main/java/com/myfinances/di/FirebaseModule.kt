package com.jcadenas.xpendz.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jcadenas.xpendz.data.repository.FirestoreGoalRemoteStore
import com.jcadenas.xpendz.data.repository.FirestoreObligationRemoteStore
import com.jcadenas.xpendz.data.repository.FirestoreObligationSettlementRemoteStore
import com.jcadenas.xpendz.data.repository.GoalRemoteStore
import com.jcadenas.xpendz.data.repository.ObligationRemoteStore
import com.jcadenas.xpendz.data.repository.ObligationSettlementRemoteStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideGoalRemoteStore(store: FirestoreGoalRemoteStore): GoalRemoteStore = store

    @Provides
    @Singleton
    fun provideObligationRemoteStore(store: FirestoreObligationRemoteStore): ObligationRemoteStore = store

    @Provides
    @Singleton
    fun provideObligationSettlementRemoteStore(store: FirestoreObligationSettlementRemoteStore): ObligationSettlementRemoteStore = store
}
