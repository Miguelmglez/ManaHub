package com.mmg.magicfolder.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.priceDataStore by preferencesDataStore(name = "price_prefs")

@Singleton
class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val LAST_PRICE_REFRESH_KEY = longPreferencesKey("last_price_refresh")
    private val AUTO_REFRESH_KEY       = booleanPreferencesKey("auto_refresh_prices")

    val lastPriceRefreshFlow: Flow<Long?> = context.priceDataStore.data
        .map { it[LAST_PRICE_REFRESH_KEY] }

    val autoRefreshPricesFlow: Flow<Boolean> = context.priceDataStore.data
        .map { it[AUTO_REFRESH_KEY] ?: false }

    suspend fun saveLastPriceRefresh(timestamp: Long) {
        context.priceDataStore.edit { it[LAST_PRICE_REFRESH_KEY] = timestamp }
    }

    suspend fun saveAutoRefreshPrices(enabled: Boolean) {
        context.priceDataStore.edit { it[AUTO_REFRESH_KEY] = enabled }
    }
}
