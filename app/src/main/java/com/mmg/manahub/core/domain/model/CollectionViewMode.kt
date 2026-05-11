package com.mmg.manahub.core.domain.model

enum class CollectionViewMode {
    GRID, LIST;

    companion object {
        fun fromName(name: String?) = entries.find { it.name == name } ?: GRID
    }
}
