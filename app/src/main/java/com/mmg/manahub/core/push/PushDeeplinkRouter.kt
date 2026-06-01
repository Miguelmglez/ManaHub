package com.mmg.manahub.core.push

/**
 * Bridges FCM background deep links (delivered to the Activity as Intent extras) into the
 * Compose [androidx.navigation.NavController], which lives inside the composition and is not
 * accessible as an Activity field.
 *
 * The Activity enqueues an incoming deeplink via [enqueue]; the navigation layer registers a
 * navigation callback via [setNavigator] once the NavController is composed. If a deeplink
 * arrives before the NavController is ready (cold start from a notification tap), it is buffered
 * and flushed as soon as the navigator registers.
 *
 * No Activity or NavController reference is retained here as a strong field beyond the lambda
 * passed to [setNavigator], which the navigation layer must clear on dispose to avoid leaks.
 */
object PushDeeplinkRouter {

    @Volatile
    private var navigator: ((String) -> Unit)? = null

    @Volatile
    private var pendingDeeplink: String? = null

    /** Registers the navigation callback and flushes any deeplink buffered before composition. */
    fun setNavigator(navigator: ((String) -> Unit)?) {
        this.navigator = navigator
        if (navigator != null) {
            pendingDeeplink?.let { link ->
                pendingDeeplink = null
                navigator(link)
            }
        }
    }

    /** Routes a deeplink now if the navigator is ready, otherwise buffers it for cold starts. */
    fun enqueue(deeplink: String) {
        val nav = navigator
        if (nav != null) {
            nav(deeplink)
        } else {
            pendingDeeplink = deeplink
        }
    }
}
