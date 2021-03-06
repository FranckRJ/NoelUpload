package com.franckrj.noelupload.upload

import okhttp3.MediaType
import okio.BufferedSink
import okhttp3.RequestBody
import okhttp3.internal.closeQuietly
import okio.Source
import okio.source
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Un [RequestBody] qui prend en paramètre un [listenerForProgress] qui sera notifié de la progression
 * de l'écriture de la requête.
 */
class ProgressRequestBody(
    private val contentType: MediaType?,
    private val content: ByteArray,
    private val linkedUploadInfos: UploadInfos,
    private val listenerForProgress: (bytesSended: Long, totalBytesToSend: Long, linkedUploadInfos: UploadInfos) -> Any?
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

            source = ByteArrayInputStream(content).source()

            read = source.read(sink.buffer, SEGMENT_SIZE)
            while (read != -1L) {
                total += read
                sink.flush()
                listenerForProgress(total, contentLength(), linkedUploadInfos)
                read = source.read(sink.buffer, SEGMENT_SIZE)
            }
        } finally {
            source?.closeQuietly()
        }
    }
}
