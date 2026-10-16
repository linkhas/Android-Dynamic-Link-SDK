package com.tecocraft.sdk.myandroiddeeplinksdk

/**
 * Thrown when a backend request completes with a non-success HTTP status.
 * Mirrors Flutter's [MyDeeplinkSdkHttpException].
 */
class MyDeeplinkSdkHttpException(
    val statusCode: Int,
    val body: String,
) : Exception("MyDeeplinkSdkHttpException($statusCode): ${body.ifEmpty { "(empty body)" }}")