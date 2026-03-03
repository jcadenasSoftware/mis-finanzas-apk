package com.myfinances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myfinances.data.local.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rate: ExchangeRateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rates: List<ExchangeRateEntity>)

    @Update
    suspend fun update(rate: ExchangeRateEntity)

    @Query(
        """
        SELECT * FROM exchange_rates
        WHERE user_uid = :userUid
        ORDER BY from_currency, to_currency
        """
    )
    fun observeAll(userUid: String): Flow<List<ExchangeRateEntity>>

    @Query(
        """
        SELECT * FROM exchange_rates
        WHERE user_uid = :userUid
          AND from_currency = :fromCurrency
          AND to_currency = :toCurrency
        LIMIT 1
        """
    )
    suspend fun get(userUid: String, fromCurrency: String, toCurrency: String): ExchangeRateEntity?

    @Query("DELETE FROM exchange_rates WHERE id = :id")
    suspend fun delete(id: String)
}
