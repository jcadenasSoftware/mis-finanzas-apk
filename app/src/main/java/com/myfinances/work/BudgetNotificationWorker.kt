package com.jcadenas.xpendz.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jcadenas.xpendz.data.local.dao.BudgetDao
import com.jcadenas.xpendz.data.local.dao.CategoryDao
import com.jcadenas.xpendz.data.local.dao.TransactionDao
import com.jcadenas.xpendz.data.local.dao.UserSettingsDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BudgetNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val userSettingsDao: UserSettingsDao
) : CoroutineWorker(context.applicationContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "xpendz_budget_alerts"
        const val CHANNEL_NAME = "Alertas de Presupuesto"
    }

    override suspend fun doWork(): Result {
        // El usuario eligió manejar alertas solo dentro de la app (sin notificaciones del sistema).
        return Result.success()
    }
}
