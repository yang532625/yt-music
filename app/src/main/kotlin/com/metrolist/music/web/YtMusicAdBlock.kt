package com.metrolist.music.web

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object YtMusicAdBlock {
    private val blockedHostParts = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "adservice.google",
        "pagead2.",
        "pagead.",
        "ad.youtube",
        "ads.youtube",
        "partnerad.l.google",
        "adsafeprotected.com",
        "scorecardresearch.com",
        "adsystem.com",
        "2mdn.net",
        "adm.youtube.com",
    )

    private val blockedPathParts = listOf(
        "/pagead/",
        "/ptracking",
        "/api/stats/ads",
        "/api/stats/atr",
        "/get_midroll",
        "/ad_break",
        "/pcs/activeview",
        "/pagead/adview",
        "/youtubei/v1/log_event",
    )

    fun shouldBlock(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("googlevideo.com") || lower.contains("/youtubei/v1/player")) return false
        if (blockedHostParts.any { lower.contains(it) }) return true
        if (blockedPathParts.any { lower.contains(it) }) return true
        return false
    }

    fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0)),
        )
    }
}
