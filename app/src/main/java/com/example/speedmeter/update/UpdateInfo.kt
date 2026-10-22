package com.example.speedmeter.update

import com.example.speedmeter.BuildConfig
import org.json.JSONObject

/** Contents of `version.json`, published by CI next to the APK in the debug-latest release. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val commit: String,
    val apkUrl: String,
    val sha256: String,
) {
    val isNewer: Boolean get() = versionCode > BuildConfig.VERSION_CODE

    companion object {
        fun parse(json: String): UpdateInfo {
            val o = JSONObject(json)
            return UpdateInfo(
                versionCode = o.getInt("versionCode"),
                versionName = o.getString("versionName"),
                commit = o.optString("commit"),
                apkUrl = o.getString("apkUrl"),
                sha256 = o.getString("sha256").lowercase(),
            )
        }
    }
}
