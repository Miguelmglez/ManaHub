package com.mmg.manahub.core.domain.collection.transfer

/**
 * Platform file access for collection import/export. Locations are opaque strings (a
 * `content://` URI on Android) produced by the platform's own document pickers.
 */
interface CollectionFileGateway {

    /**
     * Reads the text document at [location].
     *
     * @throws FileTooLargeException when it exceeds [maxBytes].
     */
    suspend fun readText(location: String, maxBytes: Long): String

    /** Writes [content] into the document the user picked at [location]. */
    suspend fun writeText(location: String, content: String)

    /**
     * Writes [content] to a private share-only file named [fileName], replacing earlier exports,
     * and returns a location other apps can be granted read access to.
     */
    suspend fun writeShareableFile(fileName: String, content: String): String
}

/** Thrown by [CollectionFileGateway.readText] when the document is larger than allowed. */
class FileTooLargeException(val maxBytes: Long) : RuntimeException("File exceeds $maxBytes bytes")
