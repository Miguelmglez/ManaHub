package com.mmg.manahub.core.domain.collection.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamingCollectionImportParserTest {
    private class Sink(private val retain: Boolean = true) : TransferRecordSink {
        val records = mutableListOf<TransferParsedRecord>()
        var count = 0L
        var completed: TransferParseSummary? = null
        var rejection: TransferParseError? = null
        override suspend fun record(record: TransferParsedRecord) {
            count++
            if (retain) records += record
        }
        override suspend fun completed(summary: TransferParseSummary) { completed = summary }
        override suspend fun rejected(error: TransferParseError) { rejection = error }
    }

    private fun source(text: String, chunk: Int = 7): TransferCharacterSource {
        var position = 0
        return TransferCharacterSource { buffer, length ->
            val count = minOf(chunk, length, text.length - position)
            text.toCharArray(buffer, 0, position, position + count)
            position += count
            count
        }
    }

    @Test
    fun bomCrLfAndQuotedMultilineRemainStableForEverySmallChunk() = runTest {
        val input = "\uFEFF# Export\r\nsep=,\r\nName,Quantity,Set code,Collector number,Foil,Condition,Language,Binder\r\n" +
            "\"Fire, \"\"Ice\"\"\r\n😀\",10000,mh2,290,etched,excellent,Japanese,Private\r\n"
        for (chunk in 1..17) {
            val sink = Sink()
            val result = StreamingCollectionImportParser().parse(source(input, chunk), sink)!!
            assertEquals(4L, result.records)
            assertEquals(2L, result.preambleRecords)
            assertEquals(1L, result.headerRecords)
            assertEquals(1L, result.dataRecords)
            assertEquals(10000L, result.copies)
            assertEquals(listOf("binder"), result.unrepresentedColumns)
            val row = sink.records.last()
            assertEquals("Fire, \"Ice\"\r\n😀", row.line!!.name)
            assertEquals("EX", row.line.condition)
            assertEquals("ja", row.line.language)
            assertTrue(row.line.isFoil)
            val expectedRecord = "\"Fire, \"\"Ice\"\"\r\n😀\",10000,mh2,290,etched,excellent,Japanese,Private"
            val bytes = input.encodeToByteArray()
            assertEquals(expectedRecord, bytes.copyOfRange(row.range.start.toInt(), row.range.endExclusive.toInt()).decodeToString())
            assertEquals(listOf(1L, 2L, 3L, 4L), sink.records.map { it.ordinal })
        }
    }

    @Test
    fun quantitiesAreExactAndDoNotMergeOrClamp() = runTest {
        val sink = Sink()
        val result = StreamingCollectionImportParser().parse(source(
            "Deck\n9999 Lightning Bolt (2X2) 117 *F*\n10000 Lightning Bolt (2X2) 117 *F*\n2147483647 +2 Mace\n2147483648 Opt\n0 Opt\n-1 Opt\nno Opt",
        ), sink)!!
        assertEquals(7L, result.dataRecords)
        assertEquals(3L, result.validRecords)
        assertEquals(4L, result.invalidRecords)
        assertEquals(2147503646L, result.copies)
        assertEquals(listOf(9999, 10000, Int.MAX_VALUE), sink.records.mapNotNull { it.line?.quantity })
        assertEquals("+2 Mace", sink.records.mapNotNull { it.line }.last().name)
        assertEquals("2x2", sink.records[1].line!!.setCode)
    }

    @Test
    fun invalidCsvAttributesAndExplicitIdsAreReportedInsteadOfSilentlyChanged() = runTest {
        val sink = Sink()
        val result = StreamingCollectionImportParser().parse(source(
            "Name,Quantity,Scryfall ID,Foil,Condition,Language\n" +
                "Opt,1,bad-id,normal,NM,en\nOpt,2,,foil,unknown,en\nOpt,3,,normal,NM,klingon\nOpt,4,,rainbow,NM,en\nOpt,5,,normal,NM,en",
        ), sink)!!
        assertEquals(4L, result.invalidRecords)
        assertEquals(1L, result.validRecords)
        assertEquals(TransferParseError.INVALID_IDENTIFIER, sink.records[1].error)
        assertTrue(sink.records.drop(2).dropLast(1).all { it.error == TransferParseError.INVALID_ATTRIBUTES })
    }

    @Test
    fun structuralFailuresNeverMarkAnyPrefixComplete() = runTest {
        val cases = listOf(
            "Name,Count\nOpt,1\n\"unterminated,2" to TransferParseError.INVALID_CSV,
            "Name,Count\nOpt,1\n\"bad\"junk,2" to TransferParseError.INVALID_CSV,
            "Name,Count\nOpt,1\n\u0000,2" to TransferParseError.BINARY_CONTENT,
            "Name,Count\nOpt,1\n" + List(257) { "x" }.joinToString(",") to TransferParseError.TOO_MANY_COLUMNS,
            "Name,Count,Count\nOpt,1,1" to TransferParseError.INVALID_HEADER,
            "sep=;\nName;Count\nOpt;1" to TransferParseError.UNSUPPORTED_SEPARATOR,
        )
        for ((input, category) in cases) {
            val sink = Sink()
            assertNull(StreamingCollectionImportParser().parse(source(input, 1), sink))
            assertEquals(category, sink.rejection)
            assertNull(sink.completed)
        }
    }

    @Test
    fun oneMiBRecordIsInclusiveAndOneAdditionalByteRejectsTheFile() = runTest {
        val prefix = "Name,Count,Notes\nOpt,1,"
        val sink = Sink(false)
        val suffix = "x".repeat(TransferLimits.MAX_RECORD_BYTES - "Opt,1,".length)
        assertEquals(1L, StreamingCollectionImportParser().parse(source(prefix + suffix, 16384), sink)!!.validRecords)
        val rejected = Sink(false)
        assertNull(StreamingCollectionImportParser().parse(source(prefix + suffix + "x", 16384), rejected))
        assertEquals(TransferParseError.RECORD_TOO_LARGE, rejected.rejection)
        assertNull(rejected.completed)
    }

    @Test
    fun supplementaryUnicodeCountsUtf8BytesAcrossSurrogateChunkBoundaries() = runTest {
        val row = "1 " + "😀".repeat((TransferLimits.MAX_RECORD_BYTES - 2) / 4)
        val sink = Sink(false)
        assertEquals(1L, StreamingCollectionImportParser().parse(source(row, 511), sink)!!.validRecords)
        val rejected = Sink(false)
        assertNull(StreamingCollectionImportParser().parse(source(row + "😀", 511), rejected))
        assertEquals(TransferParseError.RECORD_TOO_LARGE, rejected.rejection)
    }

    @Test
    fun selectedRecordLimitIsCountedWithoutAcceptingATruncatedPrefix() = runTest {
        for (size in listOf(2001, 10000, 100000, 100001)) {
            var emitted = 0
            val source = TransferCharacterSource { buffer, _ ->
                if (emitted == size) 0 else {
                    "1 Opt\n".toCharArray(buffer, 0, 0, 6)
                    emitted++
                    6
                }
            }
            val sink = Sink(false)
            val result = StreamingCollectionImportParser().parse(source, sink)!!
            assertEquals(size.toLong(), result.records)
            assertEquals(size.toLong(), result.dataRecords)
            assertEquals(size.toLong(), sink.count)
            assertEquals(size > 100000, result.exceedsSelectionBudget)
        }
    }

    @Test
    fun preambleBudgetIsSeparateFromDataBudget() = runTest {
        var emitted = 0
        val source = TransferCharacterSource { buffer, _ ->
            val line = when {
                emitted < 100001 -> "# x\n"
                emitted == 100001 -> "1 Opt\n"
                else -> ""
            }
            emitted++
            line.toCharArray(buffer, 0, 0, line.length)
            line.length
        }
        val result = StreamingCollectionImportParser().parse(source, Sink(false))!!
        assertEquals(100001L, result.preambleRecords)
        assertEquals(1L, result.dataRecords)
        assertTrue(result.exceedsSelectionBudget)
    }

    @Test
    fun titleAndMalformedHeaderDoNotLoseOrdinals() = runTest {
        val sink = Sink()
        val summary = StreamingCollectionImportParser().parse(source("Collection export\nName,Count\nOpt,2"), sink)!!
        assertEquals(1L, summary.preambleRecords)
        assertEquals(1L, summary.headerRecords)
        assertEquals(2L, summary.copies)
        assertEquals(listOf(1L, 2L, 3L), sink.records.map { it.ordinal })
        val bad = Sink()
        assertNull(StreamingCollectionImportParser().parse(source("Name,Foil\nOpt,normal"), bad))
        assertEquals(TransferParseError.INVALID_HEADER, bad.rejection)
    }

    @Test
    fun sourceAndSinkCancellationPropagateWithoutCompletionOrRejection() = runTest {
        val sink = Sink()
        assertFailsWith<CancellationException> {
            StreamingCollectionImportParser().parse(TransferCharacterSource { _, _ -> throw CancellationException() }, sink)
        }
        assertNull(sink.completed)
        assertNull(sink.rejection)
        val cancellingSink = object : TransferRecordSink {
            override suspend fun record(record: TransferParsedRecord) { throw CancellationException() }
            override suspend fun completed(summary: TransferParseSummary) { error("Unexpected completion") }
            override suspend fun rejected(error: TransferParseError) { error("Unexpected rejection") }
        }
        assertFailsWith<CancellationException> { StreamingCollectionImportParser().parse(source("1 Opt\n"), cancellingSink) }
    }

    @Test
    fun moxfieldAndArenaKeepRepresentableFieldsAndFileFormats() = runTest {
        val csv = Sink()
        val result = StreamingCollectionImportParser().parse(source("Count,Name,Edition,Collector Number,Foil,Condition,Language\n2,Opt,eld,59,foil,Lightly Played,English"), csv)!!
        assertEquals(CollectionFileFormat.MOXFIELD_CSV, result.format)
        assertEquals("LP", csv.records.last().line!!.condition)
        val txt = Sink()
        val text = StreamingCollectionImportParser().parse(source("Commander (1)\r1x Fire // Ice (MH2) 290 *E*\r\n"), txt)!!
        assertEquals(CollectionFileFormat.TEXT, text.format)
        assertEquals(1L, text.preambleRecords)
        assertFalse(text.exceedsSelectionBudget)
        assertEquals("Fire // Ice", txt.records.last().line!!.name)
        assertTrue(txt.records.last().line!!.isFoil)
    }
}
