package com.myfinances.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.myfinances.data.local.dao.AccountDao
import com.myfinances.data.local.entity.AccountEntity
import com.myfinances.sync.DeviceIdProvider
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
        currency: String = "COP"
    ): AccountEntity {
        val now = System.currentTimeMillis() / 1000
        val account = AccountEntity(
            id = UUID.randomUUID().toString(),
            userUid = userUid,
            name = name,
            type = type,
            currency = currency,
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

    suspend fun delete(userUid: String, accountId: String): Boolean {
        if (accountDao.hasMovements(userUid, accountId)) {
            return false
        }
        accountDao.delete(accountId)
        deleteFromFirestore(userUid, accountId)
        return true
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

            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("accounts")
            Log.d("AccountRepository", "Querying collection: users/$userUid/accounts")

            val snapshot = collectionRef.get(Source.SERVER).await()
            Log.d("AccountRepository", "Snapshot size: ${snapshot.size()}")

            val remoteIds = snapshot.documents.map { it.id }.toSet()

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
                        type = type,
                        currency = currency,
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

            // Delete local accounts that no longer exist in Firestore.
            // Only delete if the account has no movements.
            val localAll = accountDao.getByUser(userUid)
            var deleted = 0
            for (l in localAll) {
                if (remoteIds.contains(l.id)) {
                    continue
                }
                try {
                    if (accountDao.hasMovements(userUid, l.id)) {
                        Log.w("AccountRepository", "Skip delete local account=${l.id} (has movements)")
                        continue
                    }
                    accountDao.delete(l.id)
                    deleted++
                } catch (e: Exception) {
                    Log.e("AccountRepository", "Failed to delete missing local account=${l.id}", e)
                }
            }
            Log.d("AccountRepository", "Accounts deletedMissing=$deleted")
        } catch (e: Exception) {
            Log.e("AccountRepository", "Error syncing accounts from Firestore", e)
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

            firestore.collection("users")
                .document(userUid)
                .collection("accounts")
                .document(account.id)
                .set(mapOf("updatedBy" to deviceIdProvider.get()), SetOptions.merge())
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
