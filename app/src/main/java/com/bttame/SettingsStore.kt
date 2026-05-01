package com.bttame

import android.content.Context

class SettingsStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("bttame_settings", Context.MODE_PRIVATE)

    fun forgetHour(): Int = prefs.getInt(KEY_HOUR, 3)
    fun forgetMinute(): Int = prefs.getInt(KEY_MINUTE, 0)

    fun setForgetTime(hour: Int, minute: Int) {
        prefs.edit().putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply()
    }

    fun cancelBondFirst(): Boolean = prefs.getBoolean(KEY_CANCEL_BOND, false)
    fun setCancelBondFirst(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CANCEL_BOND, enabled).apply()
    }

    companion object {
        private const val KEY_HOUR = "forgetHour"
        private const val KEY_MINUTE = "forgetMinute"
        private const val KEY_CANCEL_BOND = "cancelBondFirst"
    }
}
