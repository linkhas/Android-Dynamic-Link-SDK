package com.tecocraft.sdk.myandroiddeeplinksdk

import org.json.JSONObject

data class DynamicLinkProject(
    val id: String,
    val name: String,
    val description: String,
    val onPlaystore: Boolean,
    val onAppstore: Boolean,
    val androidPackageName: String,
    val iosBundleId: String?,
    val defaultUrl: String,
    val androidFallbackUrl: String,
    val iosFallbackUrl: String,
    val androidHost: String?,
    val iosHost: String?,
) {
    companion object {
        fun fromJson(json: JSONObject): DynamicLinkProject = DynamicLinkProject(
            id                 = json.optString("id",                   ""),
            name               = json.optString("name",                 ""),
            description        = json.optString("description",          ""),
            onPlaystore        = json.optBoolean("on_playstore",        false),
            onAppstore         = json.optBoolean("on_appstore",         false),
            androidPackageName = json.optString("android_package_name", ""),
            iosBundleId        = json.optString("ios_bundle_id").takeIf { it.isNotEmpty() },
            defaultUrl         = json.optString("default_url",          ""),
            androidFallbackUrl = json.optString("android_fallback_url", ""),
            iosFallbackUrl     = json.optString("ios_fallback_url",     ""),
            androidHost        = json.optString("android_host").takeIf { it.isNotEmpty() },
            iosHost            = json.optString("ios_host").takeIf { it.isNotEmpty() },
        )
    }
}

data class DynamicLinkData(
    val linkId: String,
    val name: String,
    val description: String,
    val shortCode: String,
    val customDomain: String?,
    val projectId: String,
    val androidScheme: String,
    val iosScheme: String,
    val desktopLink: String?,
    val customData: Map<String, Any?>,
    val project: DynamicLinkProject,
) {
    /** RN compatibility alias — same as Flutter's `params` getter */
    val params: Map<String, Any?> get() = customData

    companion object {
        fun fromJson(json: JSONObject): DynamicLinkData {
            val projectJson = json.optJSONObject("project") ?: JSONObject()
            val dataJson    = json.optJSONObject("data")   ?: JSONObject()
            return DynamicLinkData(
                linkId       = json.optString("link_id",       ""),
                name         = json.optString("name",          ""),
                description  = json.optString("description",   ""),
                shortCode    = json.optString("short_code",    ""),
                customDomain = json.optString("custom_domain").takeIf { it.isNotEmpty() },
                projectId    = json.optString("projectId",     ""),
                androidScheme= json.optString("android_scheme",""),
                iosScheme    = json.optString("ios_scheme",    ""),
                desktopLink  = json.optString("desktop_link").takeIf { it.isNotEmpty() },
                customData   = dataJson.toMap(),
                project      = DynamicLinkProject.fromJson(projectJson),
            )
        }
    }
}

data class DynamicLinkEnvelope(
    val statusCode: Int,
    val data: DynamicLinkData?,
    val message: String,
    val success: Boolean,
) {
    companion object {
        fun fromJson(json: JSONObject): DynamicLinkEnvelope {
            val dataJson = json.optJSONObject("data")
            return DynamicLinkEnvelope(
                statusCode = json.optInt("statusCode", 0),
                data       = dataJson?.let { DynamicLinkData.fromJson(it) },
                message    = json.optString("message", ""),
                success    = json.optBoolean("success", false),
            )
        }
    }
}

internal fun JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    keys().forEach { key -> map[key] = opt(key) }
    return map
}