package com.mmg.manahub.tools.tagpipeline.upload

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SupabaseCredentialsTest {

    private fun tempLocalProperties(content: String): java.nio.file.Path {
        val dir = Files.createTempDirectory("supabase-credentials-test")
        val path = dir.resolve("local.properties")
        Files.writeString(path, content)
        return path
    }

    // ── resolveUrl ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `cli override wins over everything else`() {
        val props = tempLocalProperties("SUPABASE_URL=https://from-properties.test\n")
        val url = SupabaseCredentials.resolveUrl(
            cliOverride = "https://from-cli.test",
            localPropertiesPath = props,
            envLookup = { "https://from-env.test" },
        )
        assertEquals("https://from-cli.test", url)
    }

    @Test
    fun `env var wins over local properties when no cli override`() {
        val props = tempLocalProperties("SUPABASE_URL=https://from-properties.test\n")
        val url = SupabaseCredentials.resolveUrl(
            cliOverride = null,
            localPropertiesPath = props,
            envLookup = { "https://from-env.test" },
        )
        assertEquals("https://from-env.test", url)
    }

    @Test
    fun `local properties is the fallback when no cli override and no env var`() {
        val props = tempLocalProperties("SUPABASE_URL=https://from-properties.test\n")
        val url = SupabaseCredentials.resolveUrl(cliOverride = null, localPropertiesPath = props, envLookup = { null })
        assertEquals("https://from-properties.test", url)
    }

    @Test
    fun `resolveUrl throws a clear, secret-free error when nothing is configured`() {
        val missing = Files.createTempDirectory("supabase-credentials-test").resolve("local.properties")
        val exception = assertFailsWith<IllegalStateException> {
            SupabaseCredentials.resolveUrl(cliOverride = null, localPropertiesPath = missing, envLookup = { null })
        }
        assertTrue(exception.message!!.contains("SUPABASE_URL"))
    }

    // ── resolveServiceRoleKey ────────────────────────────────────────────────────────────────

    @Test
    fun `service role key env var wins over local properties`() {
        val props = tempLocalProperties("SUPABASE_SERVICE_ROLE_KEY=from-properties-key\n")
        val key = SupabaseCredentials.resolveServiceRoleKey(localPropertiesPath = props, envLookup = { "from-env-key" })
        assertEquals("from-env-key", key)
    }

    @Test
    fun `service role key falls back to local properties`() {
        val props = tempLocalProperties("SUPABASE_SERVICE_ROLE_KEY=from-properties-key\n")
        val key = SupabaseCredentials.resolveServiceRoleKey(localPropertiesPath = props, envLookup = { null })
        assertEquals("from-properties-key", key)
    }

    @Test
    fun `resolveServiceRoleKey throws a clear error that NEVER contains a stray key value when missing`() {
        val missing = Files.createTempDirectory("supabase-credentials-test").resolve("local.properties")
        val exception = assertFailsWith<IllegalStateException> {
            SupabaseCredentials.resolveServiceRoleKey(localPropertiesPath = missing, envLookup = { null })
        }
        assertTrue(exception.message!!.contains("SUPABASE_SERVICE_ROLE_KEY"))
        // The error message is a static, hand-written string — assert it never accidentally
        // echoes back an empty-string env var value or similar.
        assertTrue(!exception.message!!.contains("null"))
    }

    @Test
    fun `blank env var value is treated as absent, falls through to local properties`() {
        val props = tempLocalProperties("SUPABASE_SERVICE_ROLE_KEY=from-properties-key\n")
        val key = SupabaseCredentials.resolveServiceRoleKey(localPropertiesPath = props, envLookup = { "   " })
        assertEquals("from-properties-key", key)
    }
}
