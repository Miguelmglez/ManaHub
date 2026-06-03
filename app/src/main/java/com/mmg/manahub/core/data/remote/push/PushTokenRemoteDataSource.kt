package com.mmg.manahub.core.data.remote.push

import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.di.IoDispatcher
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over the three SECURITY DEFINER device-token RPCs in Supabase.
 *
 * All calls run on the IO dispatcher and rethrow on failure so the repository
 * can decide whether to retry (register/unregister) or swallow (deleteAll).
 */
@Singleton
class PushTokenRemoteDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun upsert(token: String, locale: String) = withContext(ioDispatcher) {
        val params = buildJsonObject {
            put("p_token", token)
            put("p_platform", "android")
            put("p_locale", locale)
            put("p_app_version", BuildConfig.VERSION_NAME)
        }
        supabaseClient.postgrest.rpc("upsert_device_token", params)
        Unit
    }

    suspend fun delete(token: String) = withContext(ioDispatcher) {
        val params = buildJsonObject { put("p_token", token) }
        supabaseClient.postgrest.rpc("delete_device_token", params)
        Unit
    }

    suspend fun deleteAll() = withContext(ioDispatcher) {
        supabaseClient.postgrest.rpc("delete_my_device_tokens")
        Unit
    }
}
