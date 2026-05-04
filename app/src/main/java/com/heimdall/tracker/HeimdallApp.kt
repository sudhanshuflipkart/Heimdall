package com.heimdall.tracker

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class HeimdallApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Initialize osmdroid configuration
            val tileCache = File(cacheDir, "osmdroid")
            if (!tileCache.exists()) {
                tileCache.mkdirs()
            }
            Configuration.getInstance().apply {
                userAgentValue = packageName
                osmdroidTileCache = tileCache
                osmdroidBasePath = tileCache
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
