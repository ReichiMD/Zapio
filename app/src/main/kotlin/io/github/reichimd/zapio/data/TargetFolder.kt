package io.github.reichimd.zapio.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The phone folder where files from the TV, screenshots and videos go.
 * Chosen once in the system's folder dialog; the app keeps the permission to write there.
 */
class TargetFolder(private val context: Context) {

    private val prefs = context.getSharedPreferences("target_folder", Context.MODE_PRIVATE)
    private val _folder = MutableStateFlow(load())

    /** Null until a folder is chosen, or after its permission went away. */
    val folder: StateFlow<Uri?> = _folder.asStateFlow()

    fun set(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            return
        }
        val old = _folder.value
        if (old != null && old != uri) {
            runCatching { context.contentResolver.releasePersistableUriPermission(old, flags) }
        }
        prefs.edit().putString(KEY, uri.toString()).apply()
        _folder.value = uri
    }

    /** The name the system shows for the folder, or null if none is chosen. Asks the file provider; call off the main thread. */
    fun name(): String? {
        val uri = _folder.value ?: return null
        return runCatching {
            val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            context.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }

    /** Checks again that the app may still write there, for example after the folder was removed. */
    fun refresh() {
        _folder.value = load()
    }

    private fun load(): Uri? {
        val uri = prefs.getString(KEY, null)?.let(Uri::parse) ?: return null
        val allowed = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        return uri.takeIf { allowed }
    }

    private companion object {
        const val KEY = "uri"
    }
}
