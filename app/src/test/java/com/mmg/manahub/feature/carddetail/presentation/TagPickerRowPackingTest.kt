package com.mmg.manahub.feature.carddetail.presentation

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class TagPickerRowPackingTest {
    private val tags = List(4) { CardTag("keyword_$it", TagCategory.KEYWORD) }

    @Test
    fun exactFitIncludesSpacingAndPreservesEveryTag() {
        val widths = tags.associate { it.key to 48 }
        val rows = packTagRows(tags, widths, availableWidth = 104, gap = 8)

        assertEquals(listOf(tags.take(2), tags.drop(2)), rows)
        assertEquals(tags, rows.flatten())
        assertEquals(4, packTagRows(tags, widths, availableWidth = 103, gap = 8).size)
    }

    @Test
    fun longLabelsOccupyTheirOwnBoundedRow() {
        val widths = tags.associate { it.key to 48 } + (tags[1].key to 500)
        val rows = packTagRows(tags, widths, availableWidth = 104, gap = 8)

        assertEquals(listOf(listOf(tags[0]), listOf(tags[1]), tags.drop(2)), rows)
    }

    @Test
    fun changedFontMeasurementsRepackInsteadOfKeepingOldRows() {
        val compact = tags.associate { it.key to 48 }
        val enlarged = tags.associate { it.key to 72 }

        assertEquals(2, packTagRows(tags, compact, availableWidth = 104, gap = 8).size)
        assertEquals(4, packTagRows(tags, enlarged, availableWidth = 104, gap = 8).size)
        assertEquals(emptyList<List<CardTag>>(), packTagRows(emptyList(), emptyMap(), 104, 8))
    }
}
