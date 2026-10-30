package io.github.reichimd.zapio.adb

import java.io.FilterOutputStream
import java.io.OutputStream

/** Reports the total number of bytes written so far after every write. */
internal class CountingOutputStream(output: OutputStream, private val onCount: (Long) -> Unit) :
    FilterOutputStream(output) {

    private var count = 0L

    override fun write(b: Int) {
        out.write(b)
        add(1)
    }

    // FilterOutputStream would write byte by byte.
    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        if (len > 0) add(len.toLong())
    }

    private fun add(n: Long) {
        count += n
        onCount(count)
    }
}
