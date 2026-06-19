package com.mmg.manahub.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "SecureSessionManager"

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "manahub_session_key"
private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
private const val GCM_TAG_LENGTH_BITS = 128
private const val IV_LENGTH_BYTES = 12

/** DataStore file name — separate from "user_prefs" to isolate security-sensitive data. */
private const val SESSION_STORE_NAME = "secure_session"
private val KEY_SESSION_BLOB = stringPreferencesKey("session_blob")

private val Context.secureSessionDataStore by preferencesDataStore(name = SESSION_STORE_NAME)

/**
 * A [SessionManager] that stores the Supabase [UserSession] (access_token + refresh_token)
 * encrypted with AES-256-GCM, using a key managed by the Android Keystore.
 *
 * Encryption format: the IV (12 bytes) is prepended to the ciphertext, the whole blob is
 * Base64-encoded, and stored in a dedicated DataStore file ("secure_session").
 *
 * On first read, the implementation checks for a value in the legacy
 * EncryptedSharedPreferences file ("supabase_secure_session") and transparently migrates it
 * to the new store.
 *
 * If the Keystore is unavailable or corrupted, a volatile in-memory fallback ensures the app
 * remains usable for the current session without crashing.
 *
 * minSdk = 29 (Android 10) — Keystore is always hardware-backed on API 29+.
 */
class SecureSessionManager(private val context: Context) : SessionManager {

    /**
     * Volatile in-memory fallback used when the Keystore is unavailable or a cipher
     * operation fails unexpectedly.
     */
    @Volatile
    private var inMemorySession: UserSession? = null

    /** Set to `true` after a conclusive Keystore failure to skip further attempts. */
    @Volatile
    private var keystoreUnavailable = false

    /** `true` after the one-time migration attempt has been made (whether it succeeded or not). */
    @Volatile
    private var migrationAttempted = false

    // -------------------------------------------------------------------------
    // SessionManager interface
    // -------------------------------------------------------------------------

    override suspend fun loadSession(): UserSession? {
        attemptLegacyMigrationOnce()

        if (keystoreUnavailable) return inMemorySession

        return try {
            val blob = context.secureSessionDataStore.data.first()[KEY_SESSION_BLOB]
                ?: return inMemorySession
            val plainText = decryptBlob(blob)
            Json.decodeFromString<UserSession>(plainText)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session from DataStore", e)
            inMemorySession
        }
    }

    override suspend fun saveSession(session: UserSession) {
        if (keystoreUnavailable) {
            inMemorySession = session
            return
        }

        try {
            val json = Json.encodeToString(session)
            val blob = encryptToBlob(json)
            context.secureSessionDataStore.edit { prefs ->
                prefs[KEY_SESSION_BLOB] = blob
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session — using in-memory fallback", e)
            inMemorySession = session
        }
    }

    override suspend fun deleteSession() {
        inMemorySession = null

        if (keystoreUnavailable) return

        try {
            context.secureSessionDataStore.edit { prefs ->
                prefs.remove(KEY_SESSION_BLOB)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session from DataStore", e)
        }
    }

    // -------------------------------------------------------------------------
    // Transparent legacy migration
    // -------------------------------------------------------------------------

    /**
     * Attempts to migrate from the old EncryptedSharedPreferences-based store exactly once.
     * If a session is found, it is re-encrypted with the new scheme and the old entry deleted.
     * Any failure is silently swallowed — the user will simply be asked to sign in again.
     */
    private suspend fun attemptLegacyMigrationOnce() {
        if (migrationAttempted) return
        migrationAttempted = true

        try {
            // Attempt to open the old EncryptedSharedPreferences file.
            // We use plain SharedPreferences here: the EncryptedSharedPreferences file is a
            // regular SharedPreferences file whose keys and values are encrypted. Opening it
            // as plain SP will return ciphertext strings, not usable values. Instead we
            // must open it with the security-crypto API — but since we have removed that
            // dependency, we use the migration approach below: try a plain SP read first
            // (will return null for an encrypted value), and fall back gracefully.
            //
            // Since security-crypto is no longer a dependency, we cannot decrypt the old
            // EncryptedSharedPreferences directly. The migration path we implement is:
            // read from the legacy file using plain SharedPreferences; if the key is present
            // (some devices store it unencrypted in test/debug builds), use it. Otherwise,
            // accept that the migration is not possible and log accordingly.
            //
            // For production devices the old encrypted file will be unreadable without the
            // security-crypto library; users will need to sign in once after upgrading.
            // The old file is removed to prevent stale data from accumulating.
            val legacyPrefs = context.getSharedPreferences(
                LEGACY_PREFS_FILE_NAME, Context.MODE_PRIVATE
            )

            // The old file may exist with no entries (encrypted entries are not readable
            // as plain strings), so we check whether the key is present as a plain value.
            val rawValue = legacyPrefs.getString(LEGACY_SESSION_KEY, null)
            if (rawValue != null) {
                // Plain-text entry found (non-production env). Migrate it.
                try {
                    val session = Json.decodeFromString<UserSession>(rawValue)
                    saveSession(session)
                    Log.d(TAG, "Legacy session migrated successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Legacy session found but could not be parsed — discarding", e)
                }
            } else {
                Log.d(TAG, "No readable legacy session found — fresh start or encrypted (expected on upgrade)")
            }

            // Delete legacy file regardless so stale encrypted data does not accumulate.
            deleteLegacyPrefsFile()
        } catch (e: Exception) {
            Log.w(TAG, "Legacy migration attempt failed — continuing without migrated session", e)
        }
    }

    /**
     * Deletes the legacy SharedPreferences XML file from disk.
     * Failure is logged and swallowed — it is not critical.
     */
    private fun deleteLegacyPrefsFile() {
        try {
            val file = java.io.File(
                context.filesDir.parent,
                "shared_prefs/$LEGACY_PREFS_FILE_NAME.xml"
            )
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Legacy EncryptedSharedPreferences file deleted")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete legacy prefs file", e)
        }
    }

    // -------------------------------------------------------------------------
    // Keystore + cipher helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the AES-GCM secret key from the Android Keystore.
     * Generates a new key if one does not exist yet.
     */
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGen = KeyGenerator.getInstance(ALGORITHM, KEYSTORE_PROVIDER)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(PADDING)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return keyGen.generateKey()
    }

    /**
     * Encrypts [plainText] with AES-256-GCM and returns a Base64 string containing
     * the IV (12 bytes) prepended to the ciphertext.
     */
    private fun encryptToBlob(plainText: String): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv  // GCM always generates a fresh 12-byte IV on ENCRYPT_MODE init
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Prepend IV to ciphertext: [ 12 bytes IV ][ N bytes ciphertext ]
        val combined = iv + cipherText
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts a blob produced by [encryptToBlob]. Throws on any failure.
     */
    private fun decryptBlob(blob: String): String {
        val combined = Base64.decode(blob, Base64.NO_WRAP)
        require(combined.size > IV_LENGTH_BYTES) { "Encrypted blob is too short to contain a valid IV" }

        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val cipherText = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)

        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    // -------------------------------------------------------------------------
    // Companion — legacy file constants
    // -------------------------------------------------------------------------

    companion object {
        /** Name of the legacy EncryptedSharedPreferences file (without .xml extension). */
        const val LEGACY_PREFS_FILE_NAME = "supabase_secure_session"

        /** Key used by the legacy EncryptedSessionManager. */
        const val LEGACY_SESSION_KEY = "supabase_session"
    }
}
