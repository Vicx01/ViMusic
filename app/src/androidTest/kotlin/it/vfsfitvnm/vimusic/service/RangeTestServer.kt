package it.vfsfitvnm.vimusic.service

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** Minimal local HTTP fixture: full GET, single byte range, and 416. */
internal class RangeTestServer(private val data: ByteArray) : Closeable {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    val url = "http://127.0.0.1:${server.localPort}/audio"
    val ranges = CopyOnWriteArrayList<String>()
    val statuses = CopyOnWriteArrayList<Int>()
    val regressionHeaders = CopyOnWriteArrayList<String>()
    private val worker = thread(name = "range-test-server", isDaemon = true) {
        while (!server.isClosed) {
            val socket = try { server.accept() } catch (_: java.io.IOException) { break }
            socket.use {
                val reader = it.getInputStream().bufferedReader(Charsets.US_ASCII)
                val request = reader.readLine() ?: return@use
                if (!request.startsWith("GET ")) return@use
                var range: String? = null
                var regressionHeader: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
                    if (line.startsWith("X-Regression-Header:", ignoreCase = true)) regressionHeader = line.substringAfter(':').trim()
                }
                ranges.add(range ?: "none")
                regressionHeaders.add(regressionHeader ?: "none")
                val match = range?.let { value -> Regex("bytes=(\\d+)-(\\d*)").matchEntire(value) }
                val start = match?.groupValues?.get(1)?.toLong() ?: 0L
                val end = match?.groupValues?.get(2)?.takeIf(String::isNotEmpty)?.toLong()
                    ?: (data.size - 1).toLong()
                val out = it.getOutputStream()
                if (start >= data.size) {
                    statuses.add(416)
                    out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */${data.size}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                } else {
                    val last = minOf(end, (data.size - 1).toLong())
                    val size = (last - start + 1).toInt()
                    val status = if (match == null) "200 OK" else "206 Partial Content"
                    statuses.add(if (match == null) 200 else 206)
                    val contentRange = if (match == null) "" else "Content-Range: bytes $start-$last/${data.size}\r\n"
                    out.write(("HTTP/1.1 $status\r\nContent-Type: audio/mp4\r\n" +
                        "Accept-Ranges: bytes\r\n$contentRange" +
                        "Content-Length: $size\r\nConnection: close\r\n\r\n").toByteArray())
                    try { out.write(data, start.toInt(), size) } catch (_: java.io.IOException) { /* client closed */ }
                }
            }
        }
    }

    override fun close() {
        server.close()
        worker.join(2_000)
    }
}
