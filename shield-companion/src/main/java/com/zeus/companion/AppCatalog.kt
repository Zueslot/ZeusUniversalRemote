package com.zeus.companion

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.zeus.shared.ShieldApp
import com.zeus.shared.validPackageName
import org.json.JSONArray
import org.json.JSONObject

internal class AppCatalog(private val context: Context) {
    @Suppress("DEPRECATION")
    fun apps(): List<ShieldApp> = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        .flatMap { category -> context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
        .map { ShieldApp(it.loadLabel(context.packageManager).toString().take(120), it.activityInfo.packageName) }
        .filter { it.packageName != context.packageName && validPackageName(it.packageName) }
        .distinctBy { it.packageName }.sortedBy { it.name.lowercase() }

    fun json(): JSONObject = JSONObject().put("apps", JSONArray().apply {
        apps().forEach { put(JSONObject().put("name", it.name).put("package", it.packageName)) }
    }).put("launchAllowed", Settings.canDrawOverlays(context))

    fun launch(packageName: String) {
        require(Settings.canDrawOverlays(context)) { "Enable app launching on the Shield companion first." }
        require(validPackageName(packageName) && apps().any { it.packageName == packageName }) { "App is not installed or has no launcher activity." }
        val intent = context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("App has no launch activity.")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
