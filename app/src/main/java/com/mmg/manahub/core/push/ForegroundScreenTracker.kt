package com.mmg.manahub.core.push

/**
 * In-memory flag tracking the deeplink of the screen the user is currently viewing.
 *
 * Used by [ManaHubMessagingService] to suppress a foreground notification when the
 * incoming push targets the exact screen already on display (e.g. the user is reading
 * the very trade thread that just received a message).
 *
 * Backed by a single [Volatile] field — no coroutines or synchronization needed because
 * reads and writes are independent atomic operations on a reference.
 */
object ForegroundScreenTracker {

    @Volatile
    private var currentDeeplink: String? = null

    /** Records the deeplink of the screen currently in the foreground, or `null` when none. */
    fun setCurrentDeeplink(deeplink: String?) {
        currentDeeplink = deeplink
    }

    /** Returns `true` if the user is currently viewing the screen targeted by [incomingDeeplink]. */
    fun isViewingDeeplink(incomingDeeplink: String): Boolean {
        val current = currentDeeplink ?: return false
        return current == incomingDeeplink
    }
}
