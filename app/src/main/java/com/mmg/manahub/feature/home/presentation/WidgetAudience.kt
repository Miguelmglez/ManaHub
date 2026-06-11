package com.mmg.manahub.feature.home.presentation

/**
 * Describes who a widget is meaningful for.
 *
 * - [ALL]: works fully offline, no account required.
 * - [SIGNED_IN]: needs local game/collection data but no remote account.
 * - [ACCOUNT_GATED]: requires authentication; renders a sign-in placeholder when
 *   the user is unauthenticated rather than failing.
 */
enum class WidgetAudience { ALL, SIGNED_IN, ACCOUNT_GATED }
