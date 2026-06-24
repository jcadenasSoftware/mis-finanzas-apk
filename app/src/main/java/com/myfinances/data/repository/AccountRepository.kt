package com.jcadenas.xpendz.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.jcadenas.xpendz.data.local.dao.AccountDao
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.sync.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider
) {
    private fun normalizeType(raw: String?): String {
        val t = raw?.trim()?.uppercase().orEmpty()
        if (t.isBlank()) {
            return "BANK"
        }
        if (
            t == "BANK"
            || t == "CASH"
            || t == "SAVINGS"
            || t == "VIRTUAL_WALLET"
            || t == "DIGITAL_ACCOUNT"
            || t == "CREDIT"
        ) {
            return t
        }
        if (t == "CREDIT_CARD") {
            return "CREDIT"
        }
        if (t == "INVESTMENT") {
            return "SAVINGS"
        }
        if (t == "OTHER") {
            return "BANK"
        }
        if (t == "CHECKING") {
            return "BANK"
        }
        return "BANK"
    }

    fun observeAccounts(userUid: String): Flow<List<AccountEntity>> {
        return accountDao.observeByUser(userUid)
    }

    suspend fun getAccounts(userUid: String): List<AccountEntity> {
        return accountDao.getByUser(userUid)
    }

    suspend fun getById(id: String): AccountEntity? {
        return accountDao.getById(id)
    }

    suspend fun create(
        userUid: String,
        name: String,
        type: String = "BANK",
        currency: String = "COP",
        iconKey: String? = null,
        colorHex: String? = null
    ): AccountEntity {
        val now = System.currentTimeMillis() / 1000
        val normalizedType = normalizeType(type)
        val account = AccountEntity(
            id = UUID.randomUUID().toString(),
            userUid = userUid,
            name = name,
            type = normalizedType,
            currency = currency,
            iconKey = iconKey,
            colorHex = colorHex,
            createdAtEpochSec = now,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        accountDao.insert(account)
        syncToFirestore(userUid, account)
        return account
    }

    suspend fun updateName(userUid: String, accountId: String, newName: String): AccountEntity? {
        val account = accountDao.getById(accountId) ?: return null
        val now = System.currentTimeMillis() / 1000
        val updated = account.copy(name = newName, updatedAtEpochSec = now, updatedBy = deviceIdProvider.get())
        accountDao.update(updated)
        syncToFirestore(userUid, updated)
        return updated
    }

    suspend fun updateDetails(
        userUid: String,
        accountId: String,
        name: String,
        type: String,
        iconKey: String?,
        colorHex: String?
    ): AccountEntity? {
        val account = accountDao.getById(accountId) ?: return null
        val now = System.currentTimeMillis() / 1000
        val normalizedType = normalizeType(type)
        val updated = account.copy(
            name = name,
            type = normalizedType,
            iconKey = iconKey,
            colorHex = colorHex,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        accountDao.update(updated)
        syncToFirestore(userUid, updated)
        return updated
    }

    suspend fun delete(userUid: String, accountId: String): Boolean {
        if (accountDao.hasMovements(userUid, accountId)) {
            return false
        }
        accountDao.delete(accountId)
        deleteFromFirestore(userUid, accountId)
        return true
    }

    suspend fun deleteAllByUser(userUid: String) {
        accountDao.deleteAllByUser(userUid)
        deleteAllFromFirestore(userUid)
    }

    private suspend fun deleteAllFromFirestore(userUid: String) {
        try {
            val batch = firestore.batch()
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("accounts")
            val snapshot = collectionRef.get().await()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("AccountRepository", "Error deleting all from Firestore", e)
        }
    }

    suspend fun computeBalance(userUid: String, accountId: String): Long {
        return accountDao.computeBalanceCents(userUid, accountId)
    }

    suspend fun hasMovements(userUid: String, accountId: String): Boolean {
        return accountDao.hasMovements(userUid, accountId)
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("AccountRepository", "=== SYNC FROM FIRESTORE STARTED ===")
            Log.d("AccountRepository", "Syncing accounts from Firestore for user: $userUid")

            val lastUpdatedAt = accountDao.getMaxUpdatedAtEpochSec(userUid)
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("accounts")
            Log.d("AccountRepository", "Querying collection: users/$userUid/accounts")

            val snapshot = if (lastUpdatedAt != null && lastUpdatedAt > 0L) {
                collectionRef
                    .whereGreaterThan("updatedAtEpochSec", lastUpdatedAt)
                    .get(Source.SERVER)
                    .await()
            } else {
                collectionRef.get(Source.SERVER).await()
            }
            Log.d("AccountRepository", "Snapshot size: ${snapshot.size()}")

            val accounts = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data
                    if (data == null) {
                        Log.w("AccountRepository", "Skipping doc ${doc.id}: data is null")
                        return@mapNotNull null
                    }

                    val name = (data["name"] as? String)?.trim().orEmpty()
                    if (name.isBlank()) {
                        Log.w("AccountRepository", "Skipping doc ${doc.id}: missing/blank name")
                        return@mapNotNull null
                    }

                    val type = (data["type"] as? String)?.trim().takeUnless { it.isNullOrBlank() } ?: "BANK"
                    val currency = (data["currency"] as? String)?.trim().takeUnless { it.isNullOrBlank() } ?: "COP"
                    val iconKey = (data["iconKey"] as? String)?.trim().takeUnless { it.isNullOrBlank() }
                        ?: (data["icon_key"] as? String)?.trim().takeUnless { it.isNullOrBlank() }
                    val colorHex = (data["colorHex"] as? String)?.trim().takeUnless { it.isNullOrBlank() }
                        ?: (data["color_hex"] as? String)?.trim().takeUnless { it.isNullOrBlank() }

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

                    val createdAt = anyLong("createdAtEpochSec", "created_at_epoch_sec", "createdAt")
                        ?: (System.currentTimeMillis() / 1000)
                    val updatedAt = anyLong("updatedAtEpochSec", "updated_at_epoch_sec", "updatedAt")
                        ?: createdAt

                    val updatedBy = (data["updatedBy"] as? String)?.trim().takeUnless { it.isNullOrBlank() }
                        ?: (data["updated_by"] as? String)?.trim().takeUnless { it.isNullOrBlank() }

                    val account = AccountEntity(
                        id = doc.id,
                        userUid = userUid,
                        name = name,
                        type = normalizeType(type),
                        currency = currency,
                        iconKey = iconKey,
                        colorHex = colorHex,
                        createdAtEpochSec = createdAt,
                        updatedAtEpochSec = updatedAt,
                        updatedBy = updatedBy
                    )

                    Log.d(
                        "AccountRepository",
                        "Parsed account doc=${doc.id} name=${account.name} type=${account.type} currency=${account.currency} createdAt=${account.createdAtEpochSec}"
                    )

                    account
                } catch (e: Exception) {
                    Log.e("AccountRepository", "Error parsing account doc ${doc.id}", e)
                    null
                }
            }

            Log.d("AccountRepository", "Found ${accounts.size} valid accounts in Firestore")

            if (accounts.isEmpty()) {
                Log.w("AccountRepository", "No accounts to insert")
                return
            }

            var inserted = 0
            var updated = 0
            for (a in accounts) {
                val existing = accountDao.getById(a.id)
                if (existing == null) {
                    accountDao.insert(a)
                    inserted++
                } else {
                    accountDao.update(a)
                    updated++
                }
            }
            Log.d("AccountRepository", "Accounts upserted inserted=$inserted updated=$updated")
        } catch (e: Exception) {
            Log.e("AccountRepository", "Error syncing accounts from Firestore", e)
            throw e
        }
    }

    private suspend fun syncToFirestore(userUid: String, account: AccountEntity) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("accounts")
                .document(account.id)
                .set(account, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            // Log error, data saved locally
        }
    }

    private suspend fun deleteFromFirestore(userUid: String, accountId: String) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("accounts")
                .document(accountId)
                .delete()
                .await()
        } catch (e: Exception) {
            // Log error
        }
    }
}
