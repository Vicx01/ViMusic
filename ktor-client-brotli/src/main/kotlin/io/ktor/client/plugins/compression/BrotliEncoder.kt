package io.ktor.client.plugins.compression

import io.ktor.util.ContentEncoder
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.brotli.dec.BrotliInputStream

/** Ktor response decoder; request-side Brotli encoding is not supported. */
internal object BrotliEncoder : ContentEncoder {
    override val name: String = "br"

    override fun encode(source: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        error("Brotli request encoding is not available")

    override fun encode(source: ByteWriteChannel, coroutineContext: CoroutineContext): ByteWriteChannel =
        error("Brotli request encoding is not available")

    override fun decode(source: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        BrotliInputStream(source.toInputStream(coroutineContext[Job]))
            .toByteReadChannel(coroutineContext + Dispatchers.IO)
}
