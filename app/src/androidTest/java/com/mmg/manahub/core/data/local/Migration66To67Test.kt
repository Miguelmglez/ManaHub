package com.mmg.manahub.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// MTG Today migration must preserve master transfer data and validate the exact Room schema.
@RunWith(AndroidJUnit4::class)
class Migration66To67Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MtgDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate66To67_preservesSources_addsNullSiteUrl_andDropsCompetitiveCaches() {
        helper.createDatabase(TEST_DB, 66).apply {
            execSQL(
                """
                INSERT INTO content_sources
                    (id, name, feed_url, type, is_enabled, is_default, icon_url, language,
                     last_fetched_at, etag, last_modified)
                VALUES
                    ('custom_1', 'My Blog', 'https://example.com/feed', 'ARTICLE', 0, 0, NULL, 'es',
                     1234, 'etag-1', 'lm-1')
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO competitive_meta_cache (format, response_json, cached_at) VALUES ('standard', '{}', 1)"
            )
            execSQL(
                "INSERT INTO competitive_limited_ratings_cache (set_code, format, response_json, cached_at) " +
                    "VALUES ('fin', 'PremierDraft', '{}', 1)"
            )
            execSQL(
                "INSERT INTO collection_transfer_receipts (id, auth_generation, captured_owner, phase, received_bytes, created_at, consumed_at, error) " +
                    "VALUES ('receipt_1', 7, 'account:fixture', 'RECEIVED', 456, 123, NULL, NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 67, true, MIGRATION_66_67)

        db.query(
            "SELECT name, feed_url, is_enabled, language, last_fetched_at, etag, last_modified, site_url " +
                "FROM content_sources WHERE id = 'custom_1'"
        ).use { cursor ->
            assertTrue("the source row must survive", cursor.moveToFirst())
            assertEquals("My Blog", cursor.getString(0))
            assertEquals("https://example.com/feed", cursor.getString(1))
            assertEquals("the follow state must be untouched", 0, cursor.getInt(2))
            assertEquals("es", cursor.getString(3))
            assertEquals(1234L, cursor.getLong(4))
            assertEquals("etag-1", cursor.getString(5))
            assertEquals("lm-1", cursor.getString(6))
            assertTrue("site_url must be NULL for existing rows", cursor.isNull(7))
        }
        db.query("SELECT auth_generation, captured_owner, received_bytes FROM collection_transfer_receipts WHERE id = 'receipt_1'").use { cursor ->
            assertTrue("the transfer receipt must survive", cursor.moveToFirst())
            assertEquals(7L, cursor.getLong(0))
            assertEquals("account:fixture", cursor.getString(1))
            assertEquals(456L, cursor.getLong(2))
        }
        assertFalse(tableExists(db, "competitive_meta_cache"))
        assertFalse(tableExists(db, "competitive_limited_ratings_cache"))
        assertTrue(tableExists(db, "news_saved_items"))
        db.close()
    }

    @Test
    fun migrate66To67_savedItemsTableAcceptsASnapshot() {
        helper.createDatabase(TEST_DB, 66).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 67, true, MIGRATION_66_67)
        db.execSQL(
            """
            INSERT INTO news_saved_items
                (id, kind, title, description, image_url, published_at, source_id, source_name, url,
                 author, video_id, channel_name, duration, saved_at)
            VALUES
                ('v1', 'VIDEO', 'Title', 'Desc', NULL, 10, 'src', 'Channel', 'https://youtube.com/watch?v=v1',
                 NULL, 'v1', 'Channel', NULL, 20)
            """.trimIndent()
        )

        db.query("SELECT kind, saved_at FROM news_saved_items WHERE id = 'v1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("VIDEO", cursor.getString(0))
            assertEquals(20L, cursor.getLong(1))
        }
        db.close()
    }

    @Test
    fun migrate66To67_isIdempotent_onRetryAfterCrash() {
        helper.createDatabase(TEST_DB, 66).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 67, true, MIGRATION_66_67)
        MIGRATION_66_67.migrate(db)
        db.close()
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { it.count > 0 }

    private companion object {
        const val TEST_DB = "migration-test-mtg-today"
    }
}
