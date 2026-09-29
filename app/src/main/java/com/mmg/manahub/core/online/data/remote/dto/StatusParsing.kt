package com.mmg.manahub.core.online.data.remote.dto

import com.mmg.manahub.core.util.recordNonFatal

/**
 * Resolves a server status string to an enum entry, or to [unknown] (never a terminal state)
 * when the value is not part of this client build, recording the raw value as a non-fatal.
 */
internal fun <T : Enum<T>> parseStatusOrUnknown(raw: String, entries: List<T>, unknown: T, tag: String): T =
    entries.firstOrNull { it != unknown && it.name == raw }
        ?: unknown.also { recordNonFatal("$tag: raw=${raw.take(MAX_RAW_STATUS_LENGTH)}") }

private const val MAX_RAW_STATUS_LENGTH = 32
