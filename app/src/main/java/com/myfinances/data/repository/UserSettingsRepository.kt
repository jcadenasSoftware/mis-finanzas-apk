package com.jcadenas.xpendz.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jcadenas.xpendz.data.local.dao.UserSettingsDao
import com.jcadenas.xpendz.data.local.entity.UserSettingsEntity
import com.jcadenas.xpendz.sync.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSettingsRepository @Inject constructor(
    private val userSettingsDao: UserSettingsDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider
) {
    fun observe(userUid: String): Flow<UserSettingsEntity?> {
        return userSettingsDao.observe(userUid)
    }

    suspend fun get(userUid: String): UserSettingsEntity? {
        return userSettingsDao.get(userUid)
    }

    suspend fun upsert(userUid: String, countryCode: String, baseCurrency: String): UserSettingsEntity {
        val now = System.currentTimeMillis() / 1000
        val entity = UserSettingsEntity(
            userUid = userUid,
            countryCode = countryCode,
            baseCurrency = baseCurrency,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        userSettingsDao.upsert(entity)
        syncToFirestore(userUid, entity)
        return entity
    }

    suspend fun deleteAllByUser(userUid: String) {
        deleteAllLocalByUser(userUid)
        try {
            deleteAllRemoteByUser(userUid)
        } catch (e: Exception) {
            Log.e("UserSettingsRepository", "Error al eliminar datos remotos en deleteAllByUser", e)
        }
    }

    internal suspend fun deleteAllLocalByUser(userUid: String) {
        userSettingsDao.deleteAllByUser(userUid)
    }

    internal suspend fun deleteAllRemoteByUser(userUid: String) {
        firestore.collection("users")
            .document(userUid)
            .collection("settings")
            .document("user")
            .delete()
            .await()
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("UserSettingsRepository", "Syncing settings from Firestore user=$userUid")
            val doc = firestore.collection("users")
                .document(userUid)
                .collection("settings")
                .document("user")
                .get()
                .await()

            val data = doc.data ?: return

            fun anyLong(vararg keys: String): Long? {
                for (k in keys) {
                    val v = data[k]
                    when (v) {
                        is Number -> return v.toLong()
                        is String -> v.toLongOrNull()?.let { return it }
                    }
                }
                return null
            }

            fun anyString(vararg keys: String): String? {
                for (k in keys) {
                    val v = data[k]
                    if (v is String && v.isNotBlank()) return v
                }
                return null
            }

            val country = anyString("countryCode", "country_code") ?: return
            val baseCurrency = anyString("baseCurrency", "base_currency") ?: return
            val updatedAt = anyLong("updatedAtEpochSec", "updated_at_epoch_sec") ?: (System.currentTimeMillis() / 1000)
            val updatedBy = anyString("updatedBy", "updated_by")

            val local = userSettingsDao.get(userUid)
            if (local != null && updatedAt <= local.updatedAtEpochSec) {
                return
            }

            userSettingsDao.upsert(
                UserSettingsEntity(
                    userUid = userUid,
                    countryCode = country,
                    baseCurrency = baseCurrency,
                    updatedAtEpochSec = updatedAt,
                    updatedBy = updatedBy
                )
            )
        } catch (e: Exception) {
            Log.e("UserSettingsRepository", "Error syncing settings", e)
        }
    }

    private suspend fun syncToFirestore(userUid: String, settings: UserSettingsEntity) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("settings")
                .document("user")
                .set(settings, SetOptions.merge())
                .await()
        } catch (_: Exception) {
        }
    }
}
