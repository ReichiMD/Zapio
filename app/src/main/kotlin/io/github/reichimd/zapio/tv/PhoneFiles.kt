package io.github.reichimd.zapio.tv

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Files on the phone, reached through the system's file dialogs; no storage permission needed. */
internal class PhoneFiles(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun displayName(uri: Uri): String? = query(uri, OpenableColumns.DISPLAY_NAME) { getString(it) }

    /** Size from the file provider, or from the file itself if the provider does not know it. */
    fun size(uri: Uri): Long? {
        val known = query(uri, OpenableColumns.SIZE) { if (isNull(it)) null else getLong(it) }
        if (known != null && known > 0) return known
        return try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it > 0 }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Opens a phone file to read; its read errors come out as [PhoneFileException]. */
    fun openRead(uri: Uri): InputStream =
        PhoneFileInputStream(resolver.openInputStream(uri) ?: throw PhoneFileException(IOException("no stream")))

    /**
     * Makes a new file [name] in the chosen [folder]. If the name is taken, the system adds a number.
     * Returns the new file, or null if the folder cannot be written (any more).
     */
    fun create(folder: Uri, name: String): Uri? = try {
        val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        DocumentsContract.createDocument(resolver, parent, mimeType(name), name)
    } catch (e: Exception) {
        // The provider throws whatever it likes when the folder is gone or the permission was taken back.
        null
    }

    /** Opens a new phone file to write; its write errors come out as [PhoneFileException]. */
    fun openWrite(uri: Uri): OutputStream =
        PhoneFileOutputStream(resolver.openOutputStream(uri, "w") ?: throw PhoneFileException(IOException("no stream")))

    /** Removes a half-written file after a failed or stopped copy. */
    fun delete(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
    }

    private fun <T> query(uri: Uri, column: String, read: Cursor.(Int) -> T?): T? = try {
        resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && cursor.moveToFirst()) cursor.read(index) else null
        }
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        fun mimeType(name: String): String {
            val extension = name.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }
    }
}

/** A read or write error of the phone's file, kept apart from errors of the TV connection. */
internal class PhoneFileException(cause: IOException) : RuntimeException(cause)

private class PhoneFileInputStream(input: InputStream) : FilterInputStream(input) {
    override fun read(): Int = phoneFile { super.read() }
    override fun read(b: ByteArray, off: Int, len: Int): Int = phoneFile { super.read(b, off, len) }
}

private class PhoneFileOutputStream(output: OutputStream) : FilterOutputStream(output) {
    override fun write(b: Int) = phoneFile { out.write(b) }
    override fun write(b: ByteArray, off: Int, len: Int) = phoneFile { out.write(b, off, len) }
    override fun flush() = phoneFile { out.flush() }
    override fun close() = phoneFile { out.close() }
}

private inline fun <T> phoneFile(block: () -> T): T = try {
    block()
} catch (e: IOException) {
    throw PhoneFileException(e)
}
