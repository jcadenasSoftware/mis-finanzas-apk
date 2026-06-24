package com.jcadenas.xpendz

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.work.BudgetAlertHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class XpendzApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appDatabase: AppDatabase

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        try {
            BudgetAlertHelper.init(this, appDatabase)
            Log.d("BudgetAlert", "BudgetAlertHelper init OK")
        } catch (e: Exception) {
            Log.e("BudgetAlert", "BudgetAlertHelper init FAILED: ${e.message}", e)
        }
    }
}
