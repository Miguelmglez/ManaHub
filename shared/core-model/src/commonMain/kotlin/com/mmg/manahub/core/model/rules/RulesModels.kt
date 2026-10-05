package com.mmg.manahub.core.model.rules

data class RulesManifest(
    val sourceUrl: String,
    val effectiveDate: String,
    val sha256: String,
    val schemaVersion: Int,
    val nodeCount: Int,
    val byteCount: Int,
)

enum class RulesNodeKind { INTRODUCTION, CHAPTER, SECTION, RULE, SUBRULE, GLOSSARY, NOTICE }

data class RulesNode(
    val id: String,
    val kind: RulesNodeKind,
    val title: String,
    val paragraphs: List<String>,
    val parentId: String? = null,
    val references: List<String> = emptyList(),
)

data class RulesEdition(val manifest: RulesManifest, val nodes: List<RulesNode>) {
    val id: String get() = manifest.sha256
}

data class RulesSearchPage(val nodes: List<RulesNode>, val offset: Int, val total: Int)

sealed interface RulesDestination {
    data class Index(val query: String = "") : RulesDestination
    data class Reference(val edition: String?, val referenceId: String) : RulesDestination
    data class References(val edition: String, val referenceIds: List<String>) : RulesDestination
}

enum class RulesFailure { NETWORK, INVALID_SOURCE, STORAGE, BASELINE }

sealed interface RulesUpdateResult {
    data class Installed(val edition: RulesEdition) : RulesUpdateResult
    data object Unchanged : RulesUpdateResult
    data class Failed(val category: RulesFailure) : RulesUpdateResult
}

data class RulesRawSnapshot(val manifest: RulesManifest, val text: String)
