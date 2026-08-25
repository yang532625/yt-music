package com.metrolist.innertube.strategy

import com.metrolist.innertube.models.YouTubeClient

data class ContentHints(
    val isExplicit: Boolean? = null,
    val isKidsContent: Boolean? = null,
    val isLive: Boolean? = null,
    val isUploaded: Boolean? = null,
)

class ContentAwareFallbackStrategy {
    fun resolveClients(hints: ContentHints): List<YouTubeClient> {
        val base = when {
            hints.isUploaded == true -> uploadedClients()
            hints.isLive == true -> liveClients()
            hints.isKidsContent == true -> kidsClients()
            hints.isExplicit == true -> explicitClients()
            else -> defaultClients()
        }
        val preferred = YouTubeClient.preferredOrder
        if (preferred.isEmpty()) return base
        val byName = YouTubeClient.allClients().associateBy { it.clientName }
        val head = preferred.mapNotNull { byName[it] }
        return (head + base).distinct()
    }

    private fun uploadedClients() = listOf(
        YouTubeClient.TVHTML5,
        YouTubeClient.WEB_REMIX,
        YouTubeClient.WEB_CREATOR,
    )

    private fun defaultClients() = listOf(
        YouTubeClient.VISIONOS,
        YouTubeClient.ANDROID_VR_1_65_10,
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.WEB_REMIX,
        YouTubeClient.TVHTML5,
        YouTubeClient.TVHTML5_SIMPLY,
    )

    private fun explicitClients() = listOf(
        YouTubeClient.VISIONOS,
        YouTubeClient.TVHTML5,
        YouTubeClient.WEB_REMIX,
    )

    private fun kidsClients() = listOf(
        YouTubeClient.TVHTML5,
        YouTubeClient.WEB_REMIX,
        YouTubeClient.TVHTML5_SIMPLY,
        YouTubeClient.WEB_CREATOR,
    )

    private fun liveClients() = listOf(
        YouTubeClient.TVHTML5,
        YouTubeClient.WEB_REMIX,
        YouTubeClient.WEB_CREATOR,
        YouTubeClient.TVHTML5_SIMPLY,
    )
}
