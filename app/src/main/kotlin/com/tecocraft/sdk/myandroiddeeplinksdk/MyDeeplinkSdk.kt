package com.tecocraft.sdk.myandroiddeeplinksdk

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

private const val TAG                         = "MyDeeplinkSdk"
private const val HAS_FIRST_INSTALL_PREF_KEY  = "@storage.hasFirstInstall"
private const val PREFS_NAME                  = "my_deeplink_sdk_prefs"

private const val UNKNOWN_MANUFACTURER = "Unknown Manufacturer"
private const val UNKNOWN_DEVICE_MODEL = "Unknown Device"
private const val UNKNOWN_OS_VERSION   = "UNKNOWN"
private const val UNKNOWN_APP_NAME     = "MyApp.kt"
private const val UNKNOWN_APP_VERSION  = "1.0.0"

/**
 * Android Kotlin equivalent of the Flutter `MyDeeplinkSdk` class.
 *
 * **Usage — initialize once in Application.onCreate():**
 * ```kotlin
 * MyDeeplinkSdk.init(
 *     context = this,
 *     config  = mapOf(
 *         "app_id"   to "com.example.myapp",
 *         "base_url" to "https://backend-dynamiclink.tecocraft.us",
 *     )
 * )
 * ```
 */
object MyDeeplinkSdk {

    // ── internal state ────────────────────────────────────────────────────────

    private var _config: MyDeeplinkSdkConfig?        = null
    private var _pathShortCodeHosts: Set<String>?    = null
    private var _smartLinkJob: Job?                  = null
    private var _applicationContext: Context?        = null

    private val _backend: DynamicLinkBackendClient
        get() = DynamicLinkBackendClient(_config)

    // ── init ──────────────────────────────────────────────────────────────────

    /**
     * Initializes the SDK. Call once from `Application.onCreate()`.
     *
     * **Config keys** (snake_case or camelCase both accepted):
     * - `app_id` / `appId` — required for [postPendingRedirect]
     * - `base_url` / `baseUrl` — override API origin
     * - `app_display_name` / `appDisplayName`
     * - `app_version` / `appVersion`
     * - `device_manufacturer` / `deviceManufacturer`
     * - `device_model` / `deviceModel`
     * - `os_version` / `osVersion`
     * - `path_short_code_hosts` / `pathShortCodeHosts` — List<String>
     *
     * Mirrors Flutter's [MyDeeplinkSdk.init].
     */
    fun init(context: Context, config: Map<String, Any?> = emptyMap()) {
        _applicationContext = context.applicationContext

        val appIdentity = readAppIdentity(context)
        val deviceIdentity = readDeviceIdentity()

        // ── app_id ────────────────────────────────────────────────────────────
        val configuredAppId = config.str("app_id") ?: config.str("appId")
        val appId = firstNonEmpty(configuredAppId, appIdentity.appId) ?: ""

        // ── base_url ──────────────────────────────────────────────────────────
        val baseRaw = config.str("base_url")
            ?: config.str("baseUrl")
            ?: kDefaultDynamicLinkBaseUrl
        val base = baseRaw.trim().ifEmpty { kDefaultDynamicLinkBaseUrl }.trimEnd('/')

        // ── display name & version ────────────────────────────────────────────
        val configuredDisplay  = config.str("app_display_name") ?: config.str("appDisplayName")
        val configuredVersion  = config.str("app_version")      ?: config.str("appVersion")
        val display = firstNonEmpty(configuredDisplay, appIdentity.appName) ?: UNKNOWN_APP_NAME
        val version = firstNonEmpty(configuredVersion, appIdentity.appVersion) ?: UNKNOWN_APP_VERSION

        // ── device info ───────────────────────────────────────────────────────
        val configuredManufacturer = config.str("device_manufacturer") ?: config.str("deviceManufacturer")
        val configuredModel        = config.str("device_model")        ?: config.str("deviceModel")
        val configuredOsVersion    = config.str("os_version")          ?: config.str("osVersion")
        val deviceManufacturer = firstNonEmpty(configuredManufacturer, deviceIdentity.manufacturer) ?: UNKNOWN_MANUFACTURER
        val deviceModel        = firstNonEmpty(configuredModel,        deviceIdentity.model)        ?: UNKNOWN_DEVICE_MODEL
        val osVersion          = firstNonEmpty(configuredOsVersion,    deviceIdentity.osVersion)    ?: UNKNOWN_OS_VERSION

        _config = MyDeeplinkSdkConfig(
            appId              = appId,
            baseUrl            = base,
            appDisplayName     = display,
            appVersion         = version,
            deviceManufacturer = deviceManufacturer,
            deviceModel        = deviceModel,
            osVersion          = osVersion,
        )

        // ── path_short_code_hosts ─────────────────────────────────────────────
        @Suppress("UNCHECKED_CAST")
        val hostList = (config["path_short_code_hosts"] ?: config["pathShortCodeHosts"]) as? List<*>
        _pathShortCodeHosts = hostList
            ?.mapNotNull { it?.toString()?.trim()?.ifEmpty { null } }
            ?.toSet()
            ?.ifEmpty { null }

        if (appId.isEmpty()) {
            Log.w(TAG, "MyDeeplinkSdk.init: app_id is empty — getLinkByCode will still work; " +
                    "postPendingRedirect() will throw until app_id is set.")
        }
    }

    // ── GET link by code ──────────────────────────────────────────────────────

    /**
     * GET `/api/links/code/{shortCode}` → returns payload map.
     * Mirrors Flutter's [MyDeeplinkSdk.getLinkByCode].
     *
     * Run on a background thread / coroutine — this is a blocking network call.
     */
    @Throws(MyDeeplinkSdkHttpException::class)
    fun getLinkByCode(shortCode: String): Map<String, Any?> =
        _backend.getLinkByCode(shortCode)

    /**
     * Typed version. Mirrors Flutter's [MyDeeplinkSdk.getLinkByCodeTyped].
     */
    @Throws(MyDeeplinkSdkHttpException::class, IllegalArgumentException::class)
    fun getLinkByCodeTyped(shortCode: String): DynamicLinkData {
        val envelope = _backend.getLinkByCodeEnvelope(shortCode)
        val parsed   = DynamicLinkEnvelope.fromJson(envelope)
        return parsed.data
            ?: throw IllegalArgumentException("Expected non-null data for code lookup response")
    }

    /** RN parity alias for [getLinkByCode]. */
    @Throws(MyDeeplinkSdkHttpException::class)
    fun fetchDynamicLink(shortCode: String): Map<String, Any?> = getLinkByCode(shortCode)

    /** RN parity alias (typed). */
    @Throws(MyDeeplinkSdkHttpException::class)
    fun fetchDynamicLinkTyped(shortCode: String): DynamicLinkData = getLinkByCodeTyped(shortCode)

    // ── POST pending-redirect ─────────────────────────────────────────────────

    /**
     * POST `/api/links/pending-redirect`.
     * Mirrors Flutter's [MyDeeplinkSdk.postPendingRedirect].
     */
    @Throws(MyDeeplinkSdkHttpException::class, IllegalStateException::class)
    fun postPendingRedirect(): Map<String, Any?> = _backend.postPendingRedirect()

    /**
     * Typed version. Mirrors Flutter's [MyDeeplinkSdk.postPendingRedirectTyped].
     */
    @Throws(MyDeeplinkSdkHttpException::class, IllegalStateException::class)
    fun postPendingRedirectTyped(): DynamicLinkData {
        val envelope = _backend.postPendingRedirectEnvelope()
        val parsed   = DynamicLinkEnvelope.fromJson(envelope)
        return parsed.data
            ?: throw IllegalArgumentException("Expected non-null data for pending redirect response")
    }

    /** RN parity alias for [postPendingRedirect]. */
    fun trackPendingRedirect(): Map<String, Any?> = postPendingRedirect()

    /** RN parity alias (typed). */
    fun trackPendingRedirectTyped(): DynamicLinkData = postPendingRedirectTyped()


    // ── POST generate public link ─────────────────────────────────────────────
    /**
     * POST `/api/links/public-link` → returns payload map.
     * Mirrors Flutter's [MyDeeplinkSdk.generatePublicLink].
     *
     * Run on a background thread / coroutine — this is a blocking network call.
     */
    @Throws(MyDeeplinkSdkHttpException::class)
    fun generatePublicLink(clientId: String, body: Map<String, Any?>): Map<String, Any?> =
        _backend.generatePublicLink(clientId, body)


    // ── initial / incoming link ───────────────────────────────────────────────

    /**
     * Returns the deep-link URI that launched the app (if any).
     * Call from your launcher Activity.
     * Mirrors Flutter's [MyDeeplinkSdk.getInitialLink].
     */
    fun getInitialLink(intent: Intent?): String? = intent?.data?.toString()

    /** RN parity alias. */
    fun getInitialURL(intent: Intent?): String? = getInitialLink(intent)

    // ── short code extraction ─────────────────────────────────────────────────

    /**
     * Reads `short_code` from query string (URL-decoded).
     * Mirrors Flutter's [MyDeeplinkSdk.extractShortCode].
     */
    fun extractShortCode(url: String): String? {
        val uri = Uri.parse(url) ?: return null
        val v   = uri.getQueryParameter("short_code")
        return v?.takeIf { it.isNotEmpty() }
    }

    /**
     * Query `short_code` first; if absent and [allowedHosts] contains the URL host,
     * uses the last non-empty path segment as short code.
     * Mirrors Flutter's [MyDeeplinkSdk.extractShortCodeWithPathFallback].
     */
    fun extractShortCodeWithPathFallback(
        url: String,
        allowedHosts: Set<String>? = null,
    ): String? {
        val fromQuery = extractShortCode(url)
        if (fromQuery != null) return fromQuery

        val uri = Uri.parse(url) ?: return null
        if (allowedHosts.isNullOrEmpty()) return null
        if (!allowedHosts.contains(uri.host)) return null

        val segments = uri.pathSegments.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null

        val last = segments.last()
        if (Regex("^[A-Za-z0-9_-]+\$").matches(last) && last.length >= 4) {
            return last
        }
        return null
    }

    // ── first-install tracking ────────────────────────────────────────────────

    /**
     * Returns true when first-install handling was already completed.
     * Mirrors Flutter's [MyDeeplinkSdk.hasHandledFirstInstall].
     */
    fun hasHandledFirstInstall(): Boolean {
        return prefs()?.getBoolean(HAS_FIRST_INSTALL_PREF_KEY, false) ?: false
    }

    /**
     * Clears first-install flag for QA/re-test flows.
     * Mirrors Flutter's [MyDeeplinkSdk.resetFirstInstallFlag].
     */
    fun resetFirstInstallFlag() {
        prefs()?.edit()?.remove(HAS_FIRST_INSTALL_PREF_KEY)?.apply()
    }

    // ── smart linking ─────────────────────────────────────────────────────────

    /**
     * Starts the smart-linking flow:
     * 1. On first install → POST pending-redirect
     * 2. Process [launchIntent] if present
     * 3. Process any subsequent deep-link intents via [onNewIntent]
     *
     * Mirrors Flutter's [MyDeeplinkSdk.startSmartLinking].
     *
     * @param scope        CoroutineScope to run async work in (e.g. `lifecycleScope`)
     * @param launchIntent The Intent that started the Activity (may be null)
     * @param options      Callbacks for success / error / raw URL
     */
    fun startSmartLinking(
        scope: CoroutineScope,
        launchIntent: Intent?,
        options: SmartLinkingOptions = SmartLinkingOptions(),
    ) {
        stopSmartLinking()
        _smartLinkJob = scope.launch(Dispatchers.IO) {
            handlePendingRedirectOnFirstInstall(options)

            launchIntent?.data?.toString()?.takeIf { it.isNotEmpty() }?.let { url ->
                handleIncomingUrl(url, options)
            }
        }
    }

    /**
     * Call from `Activity.onNewIntent(intent)` to process runtime deep links.
     * Mirrors the [onLinkStream] listener in Flutter.
     */
    fun onNewIntent(
        scope: CoroutineScope,
        intent: Intent?,
        options: SmartLinkingOptions = SmartLinkingOptions(),
    ) {
        val url = intent?.data?.toString() ?: return
        scope.launch(Dispatchers.IO) {
            handleIncomingUrl(url, options)
        }
    }

    /**
     * Cancels the current smart-linking coroutine.
     * Mirrors Flutter's [MyDeeplinkSdk.stopSmartLinking].
     */
    fun stopSmartLinking() {
        _smartLinkJob?.cancel()
        _smartLinkJob = null
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private suspend fun handlePendingRedirectOnFirstInstall(options: SmartLinkingOptions) {
        if (hasHandledFirstInstall()) {
            Log.d(TAG, "MyDeeplinkSdk.pendingRedirect: skipped (already handled).")
            return
        }
        try {
            Log.d(TAG, "MyDeeplinkSdk.pendingRedirect: calling POST /api/links/pending-redirect")
            val data = trackPendingRedirectTyped()
            Log.d(TAG, "MyDeeplinkSdk.pendingRedirect: response dataShortCode=${data.shortCode} customData=${data.customData}")
            withContext(Dispatchers.Main) { options.onSuccess?.invoke(data) }
            prefs()?.edit()?.putBoolean(HAS_FIRST_INSTALL_PREF_KEY, true)?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "MyDeeplinkSdk.pendingRedirect: error $e")
            withContext(Dispatchers.Main) { options.onError?.invoke(e) }
        }
    }

    private suspend fun handleIncomingUrl(url: String, options: SmartLinkingOptions) {
        withContext(Dispatchers.Main) { options.onUrl?.invoke(url) }
        val shortCode = extractShortCodeWithPathFallback(url, _pathShortCodeHosts) ?: return
        try {
            val data = fetchDynamicLinkTyped(shortCode)
            withContext(Dispatchers.Main) { options.onSuccess?.invoke(data) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { options.onError?.invoke(e) }
        }
    }

    private fun prefs(): SharedPreferences? =
        _applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── device / app identity readers ─────────────────────────────────────────

    private data class DeviceIdentity(
        val manufacturer: String? = null,
        val model: String?        = null,
        val osVersion: String?    = null,
    )

    private data class AppIdentity(
        val appId: String?      = null,
        val appName: String?    = null,
        val appVersion: String? = null,
    )

    /**
     * Reads device info from Android [Build].
     * Replaces the Flutter `device_info_plus` plugin dependency.
     */
    private fun readDeviceIdentity(): DeviceIdentity = DeviceIdentity(
        manufacturer = Build.MANUFACTURER,
        model        = Build.MODEL,
        osVersion    = Build.VERSION.RELEASE,
    )

    /**
     * Reads app info from [PackageManager].
     * Replaces the Flutter `package_info_plus` plugin dependency.
     */
    private fun readAppIdentity(context: Context): AppIdentity {
        return try {
            val pm  = context.packageManager
            val pkg = context.packageName
            @Suppress("DEPRECATION")
            val pi  = pm.getPackageInfo(pkg, 0)
            AppIdentity(
                appId      = pkg,
                appName    = pm.getApplicationLabel(pi.applicationInfo!!).toString(),
                appVersion = pi.versionName,
            )
        } catch (e: Exception) {
            Log.w(TAG, "MyDeeplinkSdk.init: app info unavailable: $e")
            AppIdentity()
        }
    }

    private fun firstNonEmpty(a: String?, b: String?): String? {
        val av = a?.trim()
        if (!av.isNullOrEmpty()) return av
        val bv = b?.trim()
        if (!bv.isNullOrEmpty()) return bv
        return null
    }

    private fun Map<String, Any?>.str(key: String): String? =
        this[key]?.toString()?.takeIf { it.isNotEmpty() }
}