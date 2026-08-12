package com.adaptiveui.animeapp.domain.model

import kotlinx.serialization.Serializable

/**
 * A user-created category for organizing the library. The "Default" category is special:
 * it cannot be deleted or renamed, and new saves land here by default.
 */
@Serializable
data class Category(
    val id: Long,
    val name: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val order: Int
) {
    companion object {
        const val DEFAULT_ID = 1L
        const val DEFAULT_NAME = "Default"
    }
}

/**
 * A library entry — a saved anime optionally linked to one or more categories.
 * One anime can be in multiple categories (multi-save).
 */
@Serializable
data class LibraryEntry(
    val animeId: Int,
    val title: String,
    val coverUrl: String?,
    val bannerUrl: String?,
    val score: Int?,
    val episodes: Int?,
    val format: String?,
    val year: Int?,
    val savedAt: Long,
    val categoryIds: List<Long>
)

/**
 * Category with its entries, used by the library screen.
 */
data class CategoryWithEntries(
    val category: Category,
    val entries: List<LibraryEntry>
)
