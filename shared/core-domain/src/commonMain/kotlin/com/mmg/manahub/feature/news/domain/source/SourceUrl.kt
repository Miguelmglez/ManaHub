package com.mmg.manahub.feature.news.domain.source

/** Minimal absolute-URL split for the source helpers (commonMain has no `java.net.URI`). */
data class SourceUrl(
    val scheme: String,
    val host: String,
    val port: Int?,
    val path: String,
    val query: String?,
) {
    val origin: String get() = "$scheme://$host" + (port?.let { ":$it" } ?: "")

    override fun toString(): String = origin + path + (query?.let { "?$it" } ?: "")

    fun queryParameter(name: String): String? =
        query?.split('&')
            ?.firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")

    companion object {
        private val SCHEME_PREFIX = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://")
        private val HOST = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

        fun hasScheme(raw: String): Boolean = SCHEME_PREFIX.containsMatchIn(raw.trim())

        /** Returns null for anything that is not a well-formed absolute URL with a dotted host. */
        fun parse(raw: String): SourceUrl? {
            val trimmed = raw.trim()
            if (trimmed.any { it.isWhitespace() }) return null
            val schemeMatch = SCHEME_PREFIX.find(trimmed) ?: return null
            val scheme = schemeMatch.groupValues[1].lowercase()
            val rest = trimmed.substring(schemeMatch.range.last + 1).substringBefore('#')
            val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it == -1) rest.length else it }
            val authority = rest.substring(0, authorityEnd).substringAfterLast('@')
            val host = authority.substringBefore(':').lowercase()
            if (!HOST.matches(host)) return null
            val portText = authority.substringAfter(':', "")
            val port = if (portText.isEmpty()) null else portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val pathAndQuery = rest.substring(authorityEnd)
            val path = pathAndQuery.substringBefore('?')
            val query = pathAndQuery.substringAfter('?', "").takeIf { '?' in pathAndQuery && it.isNotEmpty() }
            return SourceUrl(scheme, host, port, path, query)
        }
    }
}
