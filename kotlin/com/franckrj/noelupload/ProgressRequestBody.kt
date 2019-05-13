package com.franckrj.noelupload

import okhttp3.MediaType
import okio.Okio
import okio.BufferedSink
import okhttp3.RequestBody
import okhttp3.internal.Util
import okio.Source
import java.io.ByteArrayInputStream
import java.io.IOException

class ProgressRequestBody(
    private val contentType: MediaType?,
    private val content: ByteArray,
    private val listener: (bytesSended: Long, totalBytesToSend: Long) -> Unit
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

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        var source: Source? = null
        try {
            source = Okio.source(ByteArrayInputStream(content))
            var total: Long = 0
            var read: Long = 0

            while ({read = source.read(sink.buffer(), SEGMENT_SIZE); read }() != -1L) {
                total += read
                sink.flush()
                listener(total, contentLength())
            }
        } finally {
            Util.closeQuietly(source)
        }
    }
}
