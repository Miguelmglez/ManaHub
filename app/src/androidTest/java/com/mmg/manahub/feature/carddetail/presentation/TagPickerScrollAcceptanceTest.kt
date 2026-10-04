package com.mmg.manahub.feature.carddetail.presentation

import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.TagDictionaryEntry
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.core.ui.theme.MagicThemeAndroid
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises the real sheet's large inventory, virtualization, repeated scrolling and selection. */
class TagPickerScrollAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun largeKeywordCatalogRemainsVirtualizedAndSelectableInLightTheme() =
        verifyLargeCatalog(AppTheme.HallowedPrint)

    @Test fun largeKeywordCatalogRemainsVirtualizedAndSelectableInDarkTheme() =
        verifyLargeCatalog(AppTheme.NeonVoid)

    private fun verifyLargeCatalog(theme: AppTheme) {
        val catalog = List(837) { index ->
            TagDictionaryEntry(
                "keyword_$index", TagCategory.KEYWORD,
                mapOf("en" to "Keyword ${index.toString().padStart(3, '0')}"), emptyList(),
            )
        }
        compose.setContent {
            var selected by remember { mutableStateOf(emptyList<CardTag>()) }
            MagicThemeAndroid(theme) {
                TagPickerSheet(
                    cardAutoTags = emptyList(), currentUserTags = selected,
                    suggestedTags = emptyList(), catalogEntries = catalog,
                    userDefinedTags = emptyList(), catalogLoading = false, catalogError = false,
                    onRetryCatalog = {}, onAddUserTag = { selected = selected + it },
                    onRemoveUserTag = { tag -> selected = selected.filterNot { it.key == tag.key } },
                    onDismiss = {},
                )
            }
        }
        compose.onAllNodes(isSelectable()).assertCountEquals(0)
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Keyword"))
        compose.onNodeWithText("Keyword", useUnmergedTree = true).performClick()
        repeat(2) { pass ->
            list.performScrollToNode(hasText("Keyword 000"))
            val gestureStarted = SystemClock.elapsedRealtime()
            repeat(8) {
                list.performTouchInput { swipeUp() }
                compose.waitForIdle()
                val composedChips = compose.onAllNodes(isSelectable()).fetchSemanticsNodes().size
                assertTrue("Gesture scrolling must remain virtualized: $composedChips", composedChips in 1..40)
            }
            Log.i("TagPickerAcceptance", "gesture_pass=$pass gestures=8 elapsed_ms=${SystemClock.elapsedRealtime() - gestureStarted}")
            val started = SystemClock.elapsedRealtime()
            for (index in listOf(0, 100, 200, 300, 400, 500, 600, 700, 836)) {
                list.performScrollToNode(hasText("Keyword ${index.toString().padStart(3, '0')}"))
                val composedChips = compose.onAllNodes(isSelectable()).fetchSemanticsNodes().size
                assertTrue("837 entries must not be eagerly composed: $composedChips", composedChips in 1..40)
            }
            Log.i("TagPickerAcceptance", "scroll_pass=$pass elapsed_ms=${SystemClock.elapsedRealtime() - started}")
        }
        compose.onNodeWithText("Keyword 836").performClick()
        compose.onNodeWithText("Search catalog tags").performTextInput("Keyword 836")
        compose.onNode(isSelectable() and hasText("Keyword 836")).assertIsSelected().performClick()
    }
}
