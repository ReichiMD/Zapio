package io.github.reichimd.zapio.adb

import java.io.FilterInputStream
import java.io.InputStream

/** Reports the total number of bytes read so far after every read. */
internal class CountingInputStream(input: InputStream, private val onCount: (Long) -> Unit) :
    FilterInputStream(input) {

    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) add(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        super.read(b, off, len).also { if (it > 0) add(it.toLong()) }

    override fun skip(n: Long): Long = super.skip(n).also { if (it > 0) add(it) }

    override fun markSupported() = false

    private fun add(n: Long) {
        count += n
        onCount(count)
    }
}
