package com.mmg.manahub.feature.rules

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.rules.*
import com.mmg.manahub.core.model.rules.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RulesStateHolderTest {
    private val edition = RulesEdition(RulesManifest("official", "2026-09-25", "a".repeat(64), 1, 1, 1), listOf(RulesNode("101", RulesNodeKind.SECTION, "General", emptyList())))
    private val reporter = object : CrashReporter {
        override fun log(message: String) = Unit
        override fun setCustomKey(key: String, value: String) = Unit
        override fun recordException(throwable: Throwable) = Unit
    }
    private class FakeRepository(val edition: RulesEdition) : RulesRepository {
        var loadGate: CompletableDeferred<Unit>? = null
        var queryHandler: suspend (String, Int) -> RulesSearchPage = { _, offset -> RulesSearchPage(edition.nodes, offset, 200) }
        override suspend fun load(edition: String?): RulesEdition { loadGate?.await(); return this.edition }
        override suspend fun search(edition: RulesEdition, query: String, offset: Int): RulesSearchPage = queryHandler(query, offset)
        override suspend fun update() = RulesUpdateResult.Unchanged
    }
    private fun holder(repository: RulesRepository, scope: CoroutineScope) = RulesStateHolder(LoadRulesUseCase(repository), SearchRulesUseCase(repository), UpdateRulesUseCase(repository), reporter, scope)

    @Test fun typingDuringInitialLoadSurvives() = runTest {
        val repository = FakeRepository(edition)
        repository.loadGate = CompletableDeferred()
        val holder = holder(repository, backgroundScope)
        holder.open(null, null, "")
        runCurrent()
        holder.changeQuery("deathtouch")
        repository.loadGate!!.complete(Unit)
        runCurrent(); advanceTimeBy(251); runCurrent()
        assertEquals("deathtouch", holder.state.value.query)
        assertFalse(holder.state.value.searching)
    }
    @Test fun nonCooperativeOldSearchCannotPublish() = runTest {
        val repository = FakeRepository(edition)
        val late = CompletableDeferred<Unit>()
        repository.queryHandler = { query, offset ->
            if (query == "old") withContext(NonCancellable) { late.await() }
            RulesSearchPage(listOf(RulesNode(query, RulesNodeKind.RULE, query, emptyList())), offset, 1)
        }
        val holder = holder(repository, backgroundScope)
        holder.open(null, null, "old")
        runCurrent(); advanceTimeBy(251); runCurrent()
        holder.changeQuery("new")
        advanceTimeBy(251); runCurrent()
        late.complete(Unit); runCurrent()
        assertEquals("new", holder.state.value.results.nodes.single().id)
    }
    @Test fun stalePageCannotOverwriteNewPage() = runTest {
        val repository = FakeRepository(edition)
        val late = CompletableDeferred<Unit>()
        repository.queryHandler = { _, offset ->
            if (offset == 50) withContext(NonCancellable) { late.await() }
            RulesSearchPage(edition.nodes, offset, 200)
        }
        val holder = holder(repository, backgroundScope)
        holder.open(null, null, "query")
        runCurrent(); advanceTimeBy(251); runCurrent()
        holder.page(50); runCurrent(); holder.page(100); runCurrent()
        late.complete(Unit); runCurrent()
        assertEquals(100, holder.state.value.results.offset)
        assertFalse(holder.state.value.searching)
    }
    @Test fun searchFailureIsSeparateFromNoMatches() = runTest {
        val repository = FakeRepository(edition)
        repository.queryHandler = { _, _ -> error("private external content") }
        val holder = holder(repository, backgroundScope)
        holder.open(null, null, "query")
        runCurrent(); advanceTimeBy(251); runCurrent()
        assertTrue(holder.state.value.searchFailed)
        holder.changeQuery("")
        assertFalse(holder.state.value.searchFailed)
        assertFalse(holder.state.value.searching)
    }
    @Test fun restoredPageUsesCapturedOffset() = runTest {
        val repository = FakeRepository(edition)
        val holder = holder(repository, backgroundScope)
        holder.open(edition.id, null, "query", 100)
        runCurrent(); advanceTimeBy(251); runCurrent()
        assertEquals(100, holder.state.value.results.offset)
    }
}
