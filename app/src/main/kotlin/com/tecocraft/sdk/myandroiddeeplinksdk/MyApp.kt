package com.tecocraft.sdk.myandroiddeeplinksdk

import android.app.Application

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MyDeeplinkSdk.init(
            context = this,
            config  = mapOf(
                "app_id"                to packageName,
                "base_url"              to "https://backend-dynamiclink.tecocraft.us",
                "path_short_code_hosts" to listOf("backend-dynamiclink.tecocraft.us"),
            )
        )
    }
}