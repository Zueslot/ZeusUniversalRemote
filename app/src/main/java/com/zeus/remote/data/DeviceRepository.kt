package com.zeus.remote.data

import android.content.Context

/** Keep the existing preference file and keys so installed device settings survive the refactor. */
class DeviceRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("devices", Context.MODE_PRIVATE)
    init {
        listOf("samsung", "shield").forEach { key ->
            val saved = preferences.getString(key, "").orEmpty()
            val clean = singleLineDeviceAddress(saved)
            if (saved != clean) preferences.edit().putString(key, clean).apply()
        }
    }

    var samsungAddress: String
        get() = preferences.getString("samsung", "").orEmpty()
        set(value) { preferences.edit().putString("samsung", singleLineDeviceAddress(value)).apply() }
    var shieldAddress: String
        get() = preferences.getString("shield", "").orEmpty()
        set(value) { preferences.edit().putString("shield", singleLineDeviceAddress(value)).apply() }
    var samsungToken: String
        get() = preferences.getString("samsung_token", "").orEmpty()
        set(value) { preferences.edit().putString("samsung_token", value).apply() }
    var trustSamsungCertificate: Boolean
        get() = preferences.getBoolean("tv_self_signed", false)
        set(value) { preferences.edit().putBoolean("tv_self_signed", value).apply() }
    var fastHdmiInput: Boolean
        get() = preferences.getBoolean("fast_hdmi_input", true)
        set(value) { preferences.edit().putBoolean("fast_hdmi_input", value).apply() }
    var darkTheme: Boolean
        get() = preferences.getBoolean("dark_theme", true)
        set(value) { preferences.edit().putBoolean("dark_theme", value).apply() }
    var hapticFeedback: Boolean
        get() = preferences.getBoolean("haptic_feedback", true)
        set(value) { preferences.edit().putBoolean("haptic_feedback", value).apply() }
    var remoteLayout: String
        get() = preferences.getString("remote_layout_v1", "").orEmpty()
        set(value) { preferences.edit().putString("remote_layout_v1", value).apply() }
    var samsungMac: String
        get() = if (preferences.getString("samsung_wake_mac_host", "") == samsungAddress) preferences.getString("samsung_wake_mac", "").orEmpty() else ""
        set(value) { preferences.edit().putString("samsung_wake_mac", value.trim()).putString("samsung_wake_mac_host", samsungAddress).apply() }
    var powerHdmiFallback: Boolean
        get() = preferences.getBoolean("power_hdmi_fallback", false)
        set(value) { preferences.edit().putBoolean("power_hdmi_fallback", value).apply() }

    fun forgetSamsungToken() { preferences.edit().remove("samsung_token").apply() }
}
