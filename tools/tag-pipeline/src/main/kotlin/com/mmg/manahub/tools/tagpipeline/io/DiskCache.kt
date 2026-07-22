package com.mmg.manahub.tools.tagpipeline.io

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Minimal on-disk cache directory helper. Every network-facing part of the pipeline (Scryfall bulk
 * downloads, EDHREC theme pages) routes through this so raw responses are cached to disk and repeat
 * dev runs never re-fetch unchanged data — a plan requirement ("Cache raw downloads to disk (don't
 * re-fetch on every dev run)").
 *
 * Deliberately just a thin wrapper over [java.nio.file] — no external cache library needed for "one
 * file per key" storage.
 */
class DiskCache(private val cacheDir: Path) {

    init {
        Files.createDirectories(cacheDir)
    }

    fun path(name: String): Path = cacheDir.resolve(name)

    fun exists(name: String): Boolean = Files.exists(path(name))

    fun readText(name: String): String? {
        val p = path(name)
        return if (Files.exists(p)) Files.readString(p) else null
    }

    /** Writes atomically (write to a temp file, then move) so a crash mid-write never leaves a
     *  half-written cache entry that a later run would misread as valid. */
    fun writeText(name: String, content: String) {
        val p = path(name)
        val tmp = Files.createTempFile(cacheDir, "$name.", ".tmp")
        Files.writeString(tmp, content)
        Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
