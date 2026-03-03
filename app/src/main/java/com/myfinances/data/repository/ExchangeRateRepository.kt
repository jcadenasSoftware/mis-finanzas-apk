package com.myfinances.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myfinances.data.local.dao.ExchangeRateDao
import com.myfinances.data.local.entity.ExchangeRateEntity
import com.myfinances.sync.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExchangeRateRepository @Inject constructor(
    private val exchangeRateDao: ExchangeRateDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider
) {
    fun observeAll(userUid: String): Flow<List<ExchangeRateEntity>> {
        return exchangeRateDao.observeAll(userUid)
    }

    suspend fun upsert(userUid: String, fromCurrency: String, toCurrency: String, rate: Double): ExchangeRateEntity {
        val now = System.currentTimeMillis() / 1000
        val existing = exchangeRateDao.get(userUid, fromCurrency, toCurrency)
        val entity = if (existing == null) {
            ExchangeRateEntity(
                id = UUID.randomUUID().toString(),
                userUid = userUid,
                fromCurrency = fromCurrency,
                toCurrency = toCurrency,
                rate = rate,
                updatedAtEpochSec = now,
                updatedBy = deviceIdProvider.get()
            )
        } else {
            existing.copy(
                rate = rate,
                updatedAtEpochSec = now,
                updatedBy = deviceIdProvider.get()
            )
        }

        exchangeRateDao.insert(entity)
        syncToFirestore(userUid, entity)
        return entity
    }

    suspend fun delete(userUid: String, rateId: String) {
        exchangeRateDao.delete(rateId)
        deleteFromFirestore(userUid, rateId)
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("ExchangeRateRepository", "Syncing exchangeRates from Firestore user=$userUid")
            val snapshot = firestore.collection("users")
                .document(userUid)
                .collection("exchangeRates")
                .get()
                .await()

            val rates = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null

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

                    fun anyDouble(vararg keys: String): Double? {
                        for (k in keys) {
                            val v = data[k]
                            when (v) {
                                is Number -> return v.toDouble()
                                is String -> v.toDoubleOrNull()?.let { return it }
                            }
                        }
                        return null
                    }

                    val from = anyString("fromCurrency", "from_currency") ?: return@mapNotNull null
                    val to = anyString("toCurrency", "to_currency") ?: return@mapNotNull null
                    val rate = anyDouble("rate") ?: return@mapNotNull null
                    val updatedAt = anyLong("updatedAtEpochSec", "updated_at_epoch_sec") ?: (System.currentTimeMillis() / 1000)
                    val updatedBy = anyString("updatedBy", "updated_by")

                    ExchangeRateEntity(
                        id = doc.id,
                        userUid = userUid,
                        fromCurrency = from,
                        toCurrency = to,
                        rate = rate,
                        updatedAtEpochSec = updatedAt,
                        updatedBy = updatedBy
                    )
                } catch (e: Exception) {
                    Log.e("ExchangeRateRepository", "Error parsing exchangeRate doc=${doc.id}", e)
                    null
                }
            }

            exchangeRateDao.insertAll(rates)
        } catch (e: Exception) {
            Log.e("ExchangeRateRepository", "Error syncing exchangeRates", e)
        }
    }

    private suspend fun syncToFirestore(userUid: String, rate: ExchangeRateEntity) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("exchangeRates")
                .document(rate.id)
                .set(rate, SetOptions.merge())
                .await()

            firestore.collection("users")
                .document(userUid)
                .collection("exchangeRates")
                .document(rate.id)
                .set(mapOf("updatedBy" to deviceIdProvider.get()), SetOptions.merge())
                .await()
        } catch (_: Exception) {
        }
    }

    private suspend fun deleteFromFirestore(userUid: String, rateId: String) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("exchangeRates")
                .document(rateId)
                .delete()
                .await()
        } catch (_: Exception) {
        }
    }
}
