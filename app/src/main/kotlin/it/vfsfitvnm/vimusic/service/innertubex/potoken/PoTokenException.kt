/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package it.vfsfitvnm.vimusic.service.innertubex.potoken

class PoTokenException(message: String) : Exception(message)

// to be thrown if the WebView provided by the system is broken
class BadWebViewException(message: String) : Exception(message)

fun buildExceptionForJsError(error: String): Exception {
    return if (error.contains("SyntaxError"))
        BadWebViewException("BotGuard JavaScript syntax failure")
    else
        PoTokenException("BotGuard JavaScript initialization failure")
}
