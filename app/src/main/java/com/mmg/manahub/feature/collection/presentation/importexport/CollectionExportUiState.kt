package com.mmg.manahub.feature.collection.presentation.importexport

import com.mmg.manahub.core.domain.collection.transfer.CollectionFileFormat

/** Export sheet state, owned by `CollectionViewModel` (it exports the Cards tab's visible rows). */
data class CollectionExportUiState(
    val isSheetVisible: Boolean = false,
    val format: CollectionFileFormat = CollectionFileFormat.TEXT,
    val isExporting: Boolean = false,
    /** A written share file waiting for the screen to launch the chooser. */
    val pendingShare: PendingExportShare? = null,
    val message: CollectionExportMessage? = null,
)

/** `location` is a content URI other apps may be granted read access to. */
data class PendingExportShare(val location: String, val mimeType: String)

/** Where an export goes. */
enum class CollectionExportAction(val telemetryKey: String) { SAVE("save"), SHARE("share") }

/** One-shot export outcome shown as a toast. */
sealed interface CollectionExportMessage {
    /** @property skippedRows rows left out because their card could not be resolved. */
    data class Completed(val action: CollectionExportAction, val rows: Int, val skippedRows: Int) : CollectionExportMessage
    data class NothingToExport(val skippedRows: Int) : CollectionExportMessage
    data object Failed : CollectionExportMessage
}
