package com.mmg.manahub.tools.tagpipeline.upload

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Resolves the Supabase project URL + **service-role** key the [SupabaseUploader] needs to write to
 * `card_strategy_tags`/`pipeline_runs` (plan §5 Phase 5b — "writes ONLY via service-role upload
 * script"). Follows this project's EXISTING secrets convention rather than inventing a new one:
 * repo-root `local.properties` (already gitignored — see `.gitignore` §1 — and already the file
 * `SUPABASE_URL`/`SUPABASE_ANON_KEY` live in for `:app`'s build) is the local-dev fallback, with an
 * environment variable taking priority for CI/scripted use (mirrors `CLAUDE.md`: "In CI, inject
 * secrets via environment variables").
 *
 * **The service-role key is NEVER logged, printed, or included in any exception message** — only
 * ITS ABSENCE is reported. `local.properties` currently has no `SUPABASE_SERVICE_ROLE_KEY` entry;
 * this is intentional (nobody should commit it even to a gitignored file by accident during a
 * copy-paste) — the recommended path is the environment variable. A human running the real
 * production upload must set one of the two themselves; this class never fabricates or defaults it.
 */
object SupabaseCredentials {

    private const val URL_ENV = "SUPABASE_URL"
    private const val URL_PROPERTY = "SUPABASE_URL"
    private const val SERVICE_ROLE_ENV = "SUPABASE_SERVICE_ROLE_KEY"
    private const val SERVICE_ROLE_PROPERTY = "SUPABASE_SERVICE_ROLE_KEY"

    /** [cliOverride] (an explicit `--supabase-url` flag) wins, then the env var, then
     *  `local.properties`' existing `SUPABASE_URL` entry (already there for `:app`). [envLookup] is
     *  injectable (defaults to [System.getenv]) so tests can exercise the priority order without
     *  touching real process environment variables. */
    fun resolveUrl(
        cliOverride: String?,
        localPropertiesPath: Path = DEFAULT_LOCAL_PROPERTIES,
        envLookup: (String) -> String? = System::getenv,
    ): String =
        cliOverride?.takeIf { it.isNotBlank() }
            ?: envLookup(URL_ENV)?.takeIf { it.isNotBlank() }
            ?: readLocalProperty(localPropertiesPath, URL_PROPERTY)
            ?: throw IllegalStateException(
                "Supabase URL not found. Pass --supabase-url, set the $URL_ENV environment variable, " +
                    "or add `$URL_PROPERTY=...` to the repo-root local.properties.",
            )

    /** Env var wins (CI-friendly), then `local.properties`' `SUPABASE_SERVICE_ROLE_KEY` entry (you
     *  must add this key yourself — see class KDoc for why it is never pre-populated). Never returns
     *  a placeholder/empty value; throws with a clear, secret-free message instead. [envLookup] is
     *  injectable for the same testability reason as [resolveUrl]. */
    fun resolveServiceRoleKey(
        localPropertiesPath: Path = DEFAULT_LOCAL_PROPERTIES,
        envLookup: (String) -> String? = System::getenv,
    ): String =
        envLookup(SERVICE_ROLE_ENV)?.takeIf { it.isNotBlank() }
            ?: readLocalProperty(localPropertiesPath, SERVICE_ROLE_PROPERTY)
            ?: throw IllegalStateException(
                "$SERVICE_ROLE_ENV not found. Set it as an environment variable (preferred — CI-" +
                    "friendly, never touches disk) or add `$SERVICE_ROLE_PROPERTY=...` to the repo-root " +
                    "local.properties (already gitignored). Get the key from the Supabase dashboard: " +
                    "Project Settings > API > service_role (secret). NEVER commit this value or pass it " +
                    "as a bare CLI argument (shell history / process list would expose it).",
            )

    private fun readLocalProperty(path: Path, key: String): String? {
        if (!Files.exists(path)) return null
        val props = Properties()
        Files.newBufferedReader(path).use { props.load(it) }
        return props.getProperty(key)?.takeIf { it.isNotBlank() }
    }

    private val DEFAULT_LOCAL_PROPERTIES: Path = Path.of("local.properties")
}
