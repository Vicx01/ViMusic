package it.vfsfitvnm.innertube.utils

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * Small NewPipe downloader backed by HttpURLConnection, so the app does not need a second HTTP
 * client just to resolve a stream. A new connection is created for every request.
 */
internal object NewPipeDownloader : Downloader() {
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val connection = URL(request.url()).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = request.httpMethod()
            connection.connectTimeout = 16_000
            connection.readTimeout = 16_000
            connection.instanceFollowRedirects = true

            request.headers().forEach { (name, values) ->
                values.forEach { value -> connection.addRequestProperty(name, value) }
            }

            request.dataToSend()?.let { data ->
                connection.doOutput = true
                connection.outputStream.use { it.write(data) }
            }

            val responseCode = connection.responseCode
            if (responseCode == 429) {
                throw ReCaptchaException("YouTube requested a reCaptcha challenge", request.url())
            }

            val stream = if (responseCode >= 400) connection.errorStream else connection.inputStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key!! }

            return Response(
                responseCode,
                connection.responseMessage.orEmpty(),
                headers,
                responseBody,
                connection.url.toString()
            )
        } finally {
            connection.disconnect()
        }
    }
}
