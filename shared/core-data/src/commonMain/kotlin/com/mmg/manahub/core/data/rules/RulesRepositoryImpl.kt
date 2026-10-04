package com.mmg.manahub.core.data.rules

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.rules.*
import com.mmg.manahub.core.model.rules.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RulesRepositoryImpl(
    private val store: RulesSnapshotStore,
    private val source: RulesOfficialSource,
    private val dispatchers: DispatcherProvider,
    private val reporter: CrashReporter,
) : RulesRepository {
    private val mutex = Mutex()
    private val updateMutex = Mutex()
    private val editions = mutableMapOf<String, RulesEdition>()
    private val indices = mutableMapOf<String, RulesSearchIndex>()
    private var activeId: String? = null
    private fun report(category: String, phase: String, message: String) {
        reporter.setCustomKey("rules_failure_category", category)
        reporter.setCustomKey("rules_phase", phase)
        reporter.recordException(IllegalStateException(message))
    }

    override suspend fun load(edition: String?): RulesEdition = withContext(dispatchers.default) {
        mutex.withLock {
            val requested = edition ?: activeId
            requested?.let { editions[it] }?.let { return@withLock it }
            val snapshot = if (edition != null) store.edition(edition) ?: store.baseline().takeIf { it.manifest.sha256 == edition } ?: error("Edition unavailable")
            else try { store.active() ?: store.baseline() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { report("storage", "read", "rules_snapshot_invalid"); store.baseline() }
            val parsed = try { RulesTextParser().parse(snapshot.text, snapshot.manifest) } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) {
                if (edition != null) throw failure
                report("candidate_invalid", "parse", "rules_snapshot_invalid")
                store.baseline().let { RulesTextParser().parse(it.text, it.manifest) }
            }
            editions[parsed.id] = parsed
            indices[parsed.id] = RulesSearchIndex(parsed)
            if (edition == null) activeId = parsed.id
            parsed
        }
    }

    override suspend fun search(edition: RulesEdition, query: String, offset: Int): RulesSearchPage = withContext(dispatchers.default) {
        val index = mutex.withLock { indices.getOrPut(edition.id) { RulesSearchIndex(edition) } }
        index.search(query, offset)
    }

    private var lastUpdate: RulesUpdateResult? = null

    override suspend fun update(): RulesUpdateResult {
        if (!updateMutex.tryLock()) return updateMutex.withLock { lastUpdate ?: RulesUpdateResult.Failed(RulesFailure.NETWORK) }
        try { return performUpdate().also { lastUpdate = it } } finally { updateMutex.unlock() }
    }

    private suspend fun performUpdate(): RulesUpdateResult {
        return try {
            val previous = load()
            val candidate = withContext(dispatchers.io) { source.download() }
            val parsed = try { withContext(dispatchers.default) { RulesTextParser().parse(candidate.text, candidate.manifest) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { report("candidate_invalid", "parse", "rules_update_candidate_invalid"); return RulesUpdateResult.Failed(RulesFailure.INVALID_SOURCE) }
            require(parsed.nodes.size >= previous.nodes.size * 0.95 && parsed.manifest.effectiveDate >= previous.manifest.effectiveDate)
            for (kind in RulesNodeKind.entries) {
                val old = previous.nodes.count { it.kind == kind }
                require(parsed.nodes.count { it.kind == kind } >= old * 0.95) { "Suspicious rules inventory reduction" }
            }
            val oldNotices = previous.nodes.filter { it.kind == RulesNodeKind.NOTICE }.flatMap { it.paragraphs }
            val newNotices = parsed.nodes.filter { it.kind == RulesNodeKind.NOTICE }.flatMap { it.paragraphs }
            require(newNotices.size >= oldNotices.size && newNotices.sumOf { it.length } >= oldNotices.sumOf { it.length } * 0.95)
            val oldLegal = oldNotices.filter { '©' in it || it.contains("All Rights Reserved", ignoreCase = true) }
            val newLegal = newNotices.filter { '©' in it || it.contains("All Rights Reserved", ignoreCase = true) }
            require(newLegal.size >= oldLegal.size && oldLegal.indices.all { newLegal[it].length >= oldLegal[it].length }) { "Legal notice reduction requires review" }
            require(newNotices.lastOrNull()?.trimEnd()?.lastOrNull() in setOf('.', '!', '”', '"'))
            if (parsed.id == previous.id) return RulesUpdateResult.Unchanged
            try { withContext(dispatchers.io) { store.install(candidate.copy(manifest = parsed.manifest)) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { report("storage", "install", "rules_update_storage_failed"); return RulesUpdateResult.Failed(RulesFailure.STORAGE) }
            mutex.withLock { editions[parsed.id] = parsed; indices[parsed.id] = RulesSearchIndex(parsed); activeId = parsed.id }
            RulesUpdateResult.Installed(parsed)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { report("candidate_invalid", "validate", "rules_update_candidate_invalid"); RulesUpdateResult.Failed(RulesFailure.INVALID_SOURCE) }
        catch (_: Exception) { RulesUpdateResult.Failed(RulesFailure.NETWORK) }
    }
}
