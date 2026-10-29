package io.github.reichimd.zapio.data

import android.content.Context
import io.github.reichimd.zapio.adb.TvAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** A TV the phone has connected to before. */
data class SavedTv(val address: TvAddress, val name: String?, val lastUsedMs: Long) {
    val key: String get() = address.toString()
    val title: String get() = name ?: address.host
}

/** Saved TVs, newest first. Kept in the app's private preferences. */
class DeviceStore(context: Context) {

    private val prefs = context.getSharedPreferences("devices", Context.MODE_PRIVATE)
    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<SavedTv>> = _devices.asStateFlow()

    fun remember(address: TvAddress, name: String?): SavedTv {
        val tv = SavedTv(address, name, System.currentTimeMillis())
        _devices.update { list -> listOf(tv) + list.filter { it.key != tv.key } }
        save()
        return tv
    }

    fun forget(tv: SavedTv) {
        _devices.update { list -> list.filter { it.key != tv.key } }
        save()
    }

    private fun load(): List<SavedTv> {
        val json = JSONArray(prefs.getString(KEY, null) ?: return emptyList())
        return (0 until json.length()).mapNotNull { index ->
            val item = json.getJSONObject(index)
            val address = runCatching { TvAddress(item.getString("host"), item.getInt("port")) }.getOrNull()
            address?.let { SavedTv(it, item.optString("name").ifEmpty { null }, item.optLong("lastUsed")) }
        }
    }

    private fun save() {
        val json = JSONArray()
        _devices.value.forEach { tv ->
            json.put(
                JSONObject()
                    .put("host", tv.address.host)
                    .put("port", tv.address.port)
                    .put("name", tv.name ?: "")
                    .put("lastUsed", tv.lastUsedMs),
            )
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private companion object {
        const val KEY = "saved_tvs"
    }
}
