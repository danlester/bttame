package com.bttame

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TameDevice(
    val mac: String,
    val name: String,
    val autoForget: Boolean = false
)

class DeviceStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("bttame", Context.MODE_PRIVATE)

    init { migrateLegacyIfNeeded() }

    fun list(): List<TameDevice> {
        val s = prefs.getString(KEY_DEVICES, "[]") ?: "[]"
        val arr = JSONArray(s)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            TameDevice(
                mac = o.getString("mac"),
                name = o.optString("name", o.getString("mac")),
                autoForget = o.optBoolean("autoForget", false)
            )
        }
    }

    fun add(d: TameDevice) {
        val current = list().toMutableList()
        val idx = current.indexOfFirst { it.mac.equals(d.mac, ignoreCase = true) }
        if (idx >= 0) {
            current[idx] = d.copy(autoForget = current[idx].autoForget)
        } else {
            current.add(d)
        }
        save(current)
        if (activeMac() == null) setActive(d.mac)
    }

    fun remove(mac: String) {
        save(list().filterNot { it.mac.equals(mac, ignoreCase = true) })
        if (activeMac().equals(mac, ignoreCase = true)) {
            setActive(list().firstOrNull()?.mac)
        }
    }

    fun setAutoForget(mac: String, enabled: Boolean) {
        val updated = list().map {
            if (it.mac.equals(mac, ignoreCase = true)) it.copy(autoForget = enabled) else it
        }
        save(updated)
    }

    fun anyAutoForget(): Boolean = list().any { it.autoForget }

    fun activeMac(): String? = prefs.getString(KEY_ACTIVE, null)

    fun setActive(mac: String?) {
        prefs.edit().apply {
            if (mac == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, mac)
        }.apply()
    }

    private fun save(list: List<TameDevice>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("mac", it.mac)
                    .put("name", it.name)
                    .put("autoForget", it.autoForget)
            )
        }
        prefs.edit().putString(KEY_DEVICES, arr.toString()).apply()
    }

    private fun migrateLegacyIfNeeded() {
        if (prefs.contains(KEY_DEVICES)) return
        val legacyMac = prefs.getString("mac", null) ?: return
        val legacyName = prefs.getString("name", legacyMac) ?: legacyMac
        save(listOf(TameDevice(legacyMac, legacyName)))
        prefs.edit().putString(KEY_ACTIVE, legacyMac).remove("mac").remove("name").apply()
    }

    companion object {
        private const val KEY_DEVICES = "devices"
        private const val KEY_ACTIVE = "activeMac"
    }
}
