package com.jcadenas.xpendz.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences("myfinances_prefs", Context.MODE_PRIVATE)
    }

    fun get(): String {
        val key = "device_id"
        val existing = prefs.getString(key, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(key, created).apply()
        return created
    }
}
