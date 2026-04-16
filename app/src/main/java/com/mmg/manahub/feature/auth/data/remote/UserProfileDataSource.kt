package com.mmg.manahub.feature.auth.data.remote

import android.util.Log
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class UserProfileDataSource(
    private val supabase: SupabaseClient,
    private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun upsertUserProfile(user: AuthUser) = withContext(ioDispatcher) {
        try {
            supabase.from("user_profiles").upsert(
                mapOf(
                    "id" to user.id,
                    "email" to user.email,
                    "display_name" to user.displayName,
                    "avatar_url" to user.avatarUrl,
                    "provider" to user.provider,
                    "updated_at" to System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            // Non-fatal: the user is already authenticated even if profile sync fails.
            // Only log the full stack trace in debug builds to avoid leaking internal
            // Supabase error details (table names, constraint violations) in production logs.
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "upsertUserProfile failed — profile sync skipped", e)
            } else {
                Log.w(TAG, "upsertUserProfile failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private companion object {
        private const val TAG = "UserProfileDataSource"
    }
}
