package com.tecocraft.sdk.myandroiddeeplinksdk

/**
 * Callbacks are nullable — set only the ones you need.
 */
data class SmartLinkingOptions(
    /** Called when a dynamic link is successfully resolved. */
    val onSuccess: ((DynamicLinkData) -> Unit)? = null,

    /** Called on any error during smart linking. */
    val onError: ((Throwable) -> Unit)? = null,

    /** Called whenever an incoming deep-link URL is detected (before resolution). */
    val onUrl: ((String) -> Unit)? = null,
)