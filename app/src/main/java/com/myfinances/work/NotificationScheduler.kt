package com.myfinances.work

import android.content.Context
import androidx.work.WorkManager

object NotificationScheduler {

    private const val WORK_NAME = "xpendz_budget_check"

    fun schedule(context: Context) {
        // Alertas de presupuesto ahora se manejan solo dentro de la app.
        // Cancelamos cualquier worker programado de versiones anteriores.
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
