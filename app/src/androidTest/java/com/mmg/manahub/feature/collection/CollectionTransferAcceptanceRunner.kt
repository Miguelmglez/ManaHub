package com.mmg.manahub.feature.collection

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.test.runner.AndroidJUnitRunner

/** Opt-in emulator runner requires a dedicated framework user before application DI starts. */
class CollectionTransferAcceptanceRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "The isolated runner is emulator-only" }
        check(Process.myUid() / 100000 > 0) { "The isolated runner cannot use the primary Android user" }
        return super.newApplication(cl, className, context)
    }

    override fun onCreate(arguments: Bundle) {
        check(arguments.getString("isolatedUiFixture") == "true") { "The isolated runner requires explicit fixture consent" }
        check(arguments.getString("class") in setOf(
            "com.mmg.manahub.feature.collection.CollectionTransferDownstreamUiAcceptanceTest#realScreensAfterStreamedGuestImport",
            "com.mmg.manahub.feature.collection.CollectionTransferDownstreamUiAcceptanceTest#realScreensAfterRetainedStreamedGuestImport",
            "com.mmg.manahub.feature.collection.CollectionTransferDownstreamUiAcceptanceTest#realScreensAfterStreamedSmallGuestControl",
            "com.mmg.manahub.feature.collection.CollectionTransferDownstreamUiAcceptanceTest#retainedLargeSelectionPhaseTimings",
            "com.mmg.manahub.feature.collection.CollectionTransferLifecycleAcceptanceTest#prepareReceiptForExternalLifecycle",
            "com.mmg.manahub.feature.collection.CollectionTransferLifecycleAcceptanceTest#verifyLedgerAfterExternalTaskRestoration",
            "com.mmg.manahub.feature.collection.CollectionTransferLifecycleAcceptanceTest#retainedReviewAfterDeviceReboot",
        )) { "The isolated runner only supports downstream fixtures" }
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "The isolated runner is emulator-only" }
        val expectedUser = arguments.getString("fixtureUser")?.toIntOrNull()
        check(expectedUser != null && expectedUser > 0 && Process.myUid() / 100000 == expectedUser) { "The fixture user must match the process" }
        if(arguments.getString("class")?.contains("CollectionTransferLifecycleAcceptanceTest#")==true)
            check(arguments.getString("lifecycleAcceptance")=="true") { "The lifecycle fixture requires explicit phase consent" }
        super.onCreate(arguments)
    }
}
