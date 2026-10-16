package com.tecocraft.sdk.myandroiddeeplinksdk

/**
 * Resolved at [MyDeeplinkSdk.init] from the host app.
 * Mirrors Flutter's [MyDeeplinkSdkConfig].
 */
data class MyDeeplinkSdkConfig(
    /** Android package name — sent as `app_id` to pending-redirect. */
    val appId: String,

    /** API origin, no trailing slash. */
    val baseUrl: String,

    val appDisplayName: String,
    val appVersion: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val osVersion: String,
) {
    /**
     * format: `(App/Version) (Manufacturer Model; ANDROID 14)`.
     */
    val userAgent: String
        get() = "($appDisplayName/$appVersion) ($deviceManufacturer $deviceModel; ANDROID $osVersion)"

    /**
     * Always "ANDROID" for this SDK.
     */
    val deviceType: String get() = "ANDROID"
}