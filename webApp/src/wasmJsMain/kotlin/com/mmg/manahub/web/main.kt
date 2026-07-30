package com.mmg.manahub.web

/*
 * KMP web roadmap W1 — real entry point, replacing W0's throwaway runtime-smoke-spike main().
 *
 * Starts Koin (pure-Kotlin `startKoin`, NOT `androidContext()`/`androidLogger()` — those are
 * Android-only extensions from `koin-android`, unavailable and unneeded here) before mounting the
 * Compose root via `ComposeViewport`. See docs/plans/kmp-migration-plan.md §5 W1 and memory
 * `project_kmp_spike_findings` for the runtime gotchas (Node/Yarn/Binaryen toolchain, the
 * kotlinx-datetime force, etc.) that shaped this module in W0.
 */

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.mmg.manahub.web.di.webAppKoinModule
import kotlinx.browser.document
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(webAppKoinModule)
    }

    ComposeViewport(document.body!!) {
        App()
    }
}
