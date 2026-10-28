package com.zeus.remote.data

import android.content.Context

/** Keep the existing preference file and keys so installed device settings survive the refactor. */
class DeviceRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("devices", Context.MODE_PRIVATE)

    var samsungAddress: String
        get() = preferences.getString("samsung", "").orEmpty()
        set(value) { preferences.edit().putString("samsung", value).apply() }
    var shieldAddress: String
        get() = preferences.getString("shield", "").orEmpty()
        set(value) { preferences.edit().putString("shield", value).apply() }
    var samsungToken: String
        get() = preferences.getString("samsung_token", "").orEmpty()
        set(value) { preferences.edit().putString("samsung_token", value).apply() }
    var trustSamsungCertificate: Boolean
        get() = preferences.getBoolean("tv_self_signed", false)
        set(value) { preferences.edit().putBoolean("tv_self_signed", value).apply() }
    var fastHdmiInput: Boolean
        get() = preferences.getBoolean("fast_hdmi_input", true)
        set(value) { preferences.edit().putBoolean("fast_hdmi_input", value).apply() }

    fun forgetSamsungToken() { preferences.edit().remove("samsung_token").apply() }
}
