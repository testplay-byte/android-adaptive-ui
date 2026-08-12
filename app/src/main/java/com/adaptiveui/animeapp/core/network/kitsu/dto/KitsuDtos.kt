package com.adaptiveui.animeapp.core.network.kitsu.dto

import kotlinx.serialization.Serializable

/**
 * Kitsu JSON:API response for episodes. The `data` array holds episode resources; `meta.count`
 * gives the total episode count for pagination.
 */
@Serializable
data class KitsuEpisodesResponse(
    val data: List<KitsuResource> = emptyList(),
    val meta: KitsuMeta? = null,
    val links: KitsuLinks? = null
)

@Serializable
data class KitsuMeta(val count: Int? = null)

@Serializable
data class KitsuLinks(
    val first: String? = null,
    val next: String? = null,
    val last: String? = null
)

@Serializable
data class KitsuResource(
    val id: String? = null,
    val type: String? = null,
    val attributes: KitsuEpisodeAttributes? = null
)

@Serializable
data class KitsuEpisodeAttributes(
    val number: Int? = null,
    val seasonNumber: Int? = null,
    val relativeNumber: Int? = null,
    val canonicalTitle: String? = null,
    val titles: Map<String, String>? = null,
    val synopsis: String? = null,
    val description: String? = null,
    val airdate: String? = null,
    val length: Int? = null,
    val thumbnail: KitsuThumbnail? = null
) {
    /** Kitsu sometimes fills `synopsis` but not `description` (or vice-versa). */
    val effectiveDescription: String? get() = synopsis ?: description
    val effectiveTitle: String? get() = canonicalTitle ?: titles?.get("en") ?: titles?.get("en_jp") ?: titles?.values?.firstOrNull()
}

@Serializable
data class KitsuThumbnail(
    val original: String? = null
)
