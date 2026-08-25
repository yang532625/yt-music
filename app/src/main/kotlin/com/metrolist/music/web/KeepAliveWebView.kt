package com.metrolist.music.web

import android.content.Context
import android.webkit.WebView

/**
 * Chromium pauses HTML5 media when the WebView window is reported hidden.
 * Keep reporting VISIBLE so audio continues after Home, like official YTM
 * and the Windows desktop client.
 */
class KeepAliveWebView(context: Context) : WebView(context) {
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(VISIBLE)
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        super.dispatchWindowVisibilityChanged(VISIBLE)
    }
}
