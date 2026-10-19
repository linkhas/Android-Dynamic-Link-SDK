package com.tecocraft.sdk.myandroiddeeplinksdk

import android.app.Application

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MyDeeplinkSdk.init(
            context = this,
            config  = mapOf(
                "app_id"                to packageName,
                "base_url"              to "https://backend.linkhas.com",
                "path_short_code_hosts" to listOf("assetlinkslug.linkhas.com"), // name of your Asset Links Slug with .linkhas.com ex. abc.linkhas.com
            )
        )
    }
}