package com.tecocraft.sdk.myandroiddeeplinksdk

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

const val kDefaultDynamicLinkBaseUrl = "https://backend-dynamiclink.tecocraft.us"
private const val TAG = "MyDeeplinkSdk"
private const val REQUEST_TIMEOUT_SECONDS = 15L

/**
 * Calls Tecocraft dynamic-link HTTP APIs.
 */
class DynamicLinkBackendClient(private val config: MyDeeplinkSdkConfig?) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    // ── GET /api/links/code/{shortCode} ──────────────────────────────────────

    /** Returns extracted `data` map. */
    @Throws(MyDeeplinkSdkHttpException::class)
    fun getLinkByCode(shortCode: String): Map<String, Any?> {
        return extractData(getLinkByCodeEnvelope(shortCode))
    }

    /** Returns full API envelope  */
    @Throws(MyDeeplinkSdkHttpException::class)
    fun getLinkByCodeEnvelope(shortCode: String): JSONObject {
        val code = shortCode.trim()
        require(code.isNotEmpty()) { "shortCode must not be empty" }

        val base  = normalizeBase(config?.baseUrl ?: kDefaultDynamicLinkBaseUrl)
        val url   = "$base/api/links/code/${encode(code)}"
        val headers = buildHeaders(includeJsonAccept = true)

        logRequest("GET", url, headers, mapOf("short_code" to code))

        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .get()
            .build()

        return executeAndDecode("GET", url, request)
    }

    // ── POST /api/links/pending-redirect ─────────────────────────────────────

    /** Returns extracted `data` map.  */
    @Throws(MyDeeplinkSdkHttpException::class, IllegalStateException::class)
    fun postPendingRedirect(): Map<String, Any?> {
        return extractData(postPendingRedirectEnvelope())
    }

    /** Returns full API envelope.  */
    @Throws(MyDeeplinkSdkHttpException::class, IllegalStateException::class)
    fun postPendingRedirectEnvelope(): JSONObject {
        val cfg = config
            ?: error("MyDeeplinkSdk.init() must be called with app_id before postPendingRedirect().")
        check(cfg.appId.isNotEmpty()) {
            "MyDeeplinkSdk.init() must include a non-empty app_id (or appId)."
        }

        val base    = normalizeBase(cfg.baseUrl)
        val url     = "$base/api/links/pending-redirect"
        val bodyJson = JSONObject().apply {
            put("app_id",      cfg.appId)
            put("device_type", cfg.deviceType)
        }
        val mediaType   = "application/json".toMediaType()
        val requestBody = bodyJson.toString().toRequestBody(mediaType)
        val headers     = buildHeaders(includeJsonAccept = false, contentTypeJson = true)

        logRequest("POST", url, headers, bodyJson.toMap())

        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .post(requestBody)
            .build()

        val decodedResponse = executeAndDecode("POST", url, request)

        // Same two-step : POST → get short_code → GET by code
        val shortCode = decodedResponse
            .optJSONObject("data")
            ?.optString("short_code") ?: ""
        Log.d(TAG, "this is short code after getting response $shortCode")

        return getLinkByCodeEnvelope(shortCode)
    }



    // ── POST /api/links/public-link ──────────────────────────────────────────

    /**
     * POST `/api/links/public-link` → returns payload map.
     * Mirrors Flutter's [generatePublicLink].
     */
    @Throws(MyDeeplinkSdkHttpException::class)
    fun generatePublicLink(clientId: String, body: Map<String, Any?>): Map<String, Any?> {
        return extractData(generatePublicLinkEnvelope(clientId, body))
    }

    /**
     * Returns full API envelope.
     */
    @Throws(MyDeeplinkSdkHttpException::class)
    fun generatePublicLinkEnvelope(clientId: String, body: Map<String, Any?>): JSONObject {
        require(clientId.trim().isNotEmpty()) { "clientId must not be empty" }

        val base = normalizeBase(config?.baseUrl ?: kDefaultDynamicLinkBaseUrl)
        val url = "$base/api/links/public-link"
        val bodyJson = JSONObject(body)
        val mediaType = "application/json".toMediaType()
        val requestBody = bodyJson.toString().toRequestBody(mediaType)

        val headers = buildHeaders(includeJsonAccept = true, contentTypeJson = true).toMutableMap().apply {
            put("clientId", clientId)
        }

        logRequest("POST", url, headers, body)

        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .post(requestBody)
            .build()

        return executeAndDecode("POST", url, request)
    }



    // ── private helpers ───────────────────────────────────────────────────────

    private fun executeAndDecode(method: String, url: String, request: Request): JSONObject {
        val response = httpClient.newCall(request).execute()
        val statusCode = response.code
        val bodyStr    = response.body?.string() ?: ""

        if (statusCode < 200 || statusCode >= 300) {
            throw MyDeeplinkSdkHttpException(statusCode, bodyStr)
        }

        val decoded = if (bodyStr.isEmpty()) JSONObject() else JSONObject(bodyStr)
        logResponse(method, url, statusCode, decoded)
        return decoded
    }

    private fun buildHeaders(
        includeJsonAccept: Boolean,
        contentTypeJson: Boolean = false,
    ): Map<String, String> {
        val ua = config?.userAgent ?: "(MyApp.kt/1.0.0) (Unknown Device; ANDROID UNKNOWN)"
        return buildMap {
            put("User-Agent", ua)
            if (includeJsonAccept)  put("Accept",       "application/json")
            if (contentTypeJson)    put("Content-Type", "application/json")
        }
    }

    private fun logRequest(method: String, url: String, headers: Map<String, String>, params: Map<String, Any?>) {
        Log.d(TAG, "MyDeeplinkSdk.http request\n  method: $method\n  url: $url\n  headers: $headers\n  params: $params")
    }

    private fun logResponse(method: String, url: String, statusCode: Int, body: JSONObject) {
        Log.d(TAG, "MyDeeplinkSdk.http response\n  method: $method\n  url: $url\n  statusCode: $statusCode\n  body: $body")
    }

    companion object {
        private fun normalizeBase(base: String) = base.trimEnd('/')
        private fun encode(value: String)       = java.net.URLEncoder.encode(value, "UTF-8")

        fun extractData(envelope: JSONObject): Map<String, Any?> {
            val data = envelope.optJSONObject("data")
            return data?.toMap() ?: envelope.toMap()
        }
    }
}