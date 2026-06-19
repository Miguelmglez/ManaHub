package com.mmg.manahub.feature.online.presentation.deeplink

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mmg.manahub.core.ui.theme.MagicTheme
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.feature.online.presentation.deeplink.JoinByDeepLinkActivity.Companion.EXTRA_JOIN_CODE
import dagger.hilt.android.AndroidEntryPoint

/**
 * Processes incoming deep links of the form `manahub://join/{code}` and routes
 * the user to [LobbyJoinScreen] with the extracted code pre-filled.
 *
 * This Activity is transparent/pass-through: it extracts the code from the URI,
 * navigates to the main app's join screen, then calls [finish] so it does not
 * remain in the back stack.
 *
 * Authentication: if the user is not signed in, the join screen will handle the
 * unauthenticated state gracefully (e.g., prompt anonymous sign-in or redirect to auth).
 *
 * TODO: add the following intent-filter in AndroidManifest.xml:
 * ```xml
 * <activity
 *     android:name=".feature.online.presentation.deeplink.JoinByDeepLinkActivity"
 *     android:exported="true"
 *     android:launchMode="singleTop">
 *     <intent-filter android:autoVerify="true">
 *         <action android:name="android.intent.action.VIEW" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *         <category android:name="android.intent.category.BROWSABLE" />
 *         <data android:scheme="manahub" android:host="join" />
 *     </intent-filter>
 * </activity>
 * ```
 */
@AndroidEntryPoint
class JoinByDeepLinkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val code = extractCodeFromIntent(intent)

        if (code == null) {
            // Malformed deep link — navigate to main app and exit
            navigateToMainApp(prefilledCode = "")
            return
        }

        setContent {
            MagicTheme {
                val mc = MaterialTheme.magicColors
                var launched by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (!launched) {
                        launched = true
                        navigateToMainApp(prefilledCode = code)
                    }
                }

                // Splash-like loading screen while the navigation intent fires
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = mc.primaryAccent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle the case where the Activity is already running (singleTop mode)
        val code = extractCodeFromIntent(intent) ?: ""
        navigateToMainApp(prefilledCode = code)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts the 6-character join code from a `manahub://join/{code}` deep link URI.
     *
     * Expected path format: `/XK9T2A` or just the path segment `XK9T2A`.
     *
     * Validation rules (all must pass, otherwise null is returned):
     * - URI scheme must be "manahub" and host must be "join".
     * - The first path segment must be exactly 6 characters long.
     * - The code must contain only alphanumeric characters (A-Z, 0-9 after uppercasing).
     *   This blocks path-traversal attempts (e.g. `../`, `%2F`) and any injection payload
     *   that could be forwarded to the backend or to Compose navigation.
     *
     * @return The uppercased, validated code string, or null if the URI is absent or invalid.
     */
    private fun extractCodeFromIntent(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "manahub" || uri.host != "join") return null

        // Path segments: manahub://join/XK9T2A → ["XK9T2A"]
        val pathSegments = uri.pathSegments
        val code = pathSegments.firstOrNull()?.uppercase() ?: return null
        if (code.length != 6) return null

        // Reject any code that contains characters outside A-Z and 0-9.
        // Uri.pathSegments already percent-decodes the segment, so URL-encoded payloads
        // (e.g. %2F, %27) are decoded before this check runs — no bypass is possible.
        if (!code.all { it.isLetterOrDigit() }) return null

        return code
    }

    /**
     * Navigates to the main [MainActivity] with the join code as an extra,
     * then finishes this pass-through Activity.
     *
     * The main Activity reads [EXTRA_JOIN_CODE] from its incoming Intent and
     * navigates directly to [LobbyJoinScreen] with the code pre-filled.
     */
    private fun navigateToMainApp(prefilledCode: String) {
        val mainIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_JOIN_CODE, prefilledCode)
            }

        if (mainIntent != null) {
            startActivity(mainIntent)
        }
        finish()
    }

    companion object {
        /**
         * Intent extra key carrying the pre-filled join code.
         * Read by the main Activity's NavGraph to navigate to [LobbyJoinScreen].
         */
        const val EXTRA_JOIN_CODE = "extra_online_join_code"
    }
}
