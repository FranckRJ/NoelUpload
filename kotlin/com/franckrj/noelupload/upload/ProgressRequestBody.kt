package com.franckrj.noelupload.upload

import okhttp3.MediaType
import okio.Okio
import okio.BufferedSink
import okhttp3.RequestBody
import okhttp3.internal.Util
import okio.Source
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Un [RequestBody] qui prend en paramètre un [listenerForProgress] qui sera notifié de la progression
 * de l'écriture de la requête.
 */
class ProgressRequestBody(
    private val contentType: MediaType?,
    private val content: ByteArray,
    private val listenerForProgress: (bytesSended: Long, totalBytesToSend: Long) -> Any?
) : RequestBody() {
    companion object {
        private const val SEGMENT_SIZE: Long = 2048 // okio.Segment.SIZE
    }

    override fun contentType(): MediaType? {
        return contentType
    }

    override fun contentLength(): Long {
        return content.size.toLong()
    }

    /**
     * Ecrit la source [content] dans [sink] et notifie [listenerForProgress] de la progression.
     * L'écriture se fait au maximum par blocs de taille [SEGMENT_SIZE].
     */
    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        var source: Source? = null

        try {
            var total: Long = 0
            var read: Long

            source = Okio.source(ByteArrayInputStream(content))

            read = source.read(sink.buffer(), SEGMENT_SIZE)
            while (read != -1L) {
                total += read
                sink.flush()
                listenerForProgress(total, contentLength())
                read = source.read(sink.buffer(), SEGMENT_SIZE)
            }
        } finally {
            Util.closeQuietly(source)
        }
    }
}
