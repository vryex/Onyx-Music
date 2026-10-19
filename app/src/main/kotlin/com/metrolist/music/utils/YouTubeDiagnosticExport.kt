package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.metrolist.innertube.utils.YouTubeNetworkLog
import com.metrolist.innertube.utils.MusicTransportProbe
import com.metrolist.music.BuildConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

fun buildYouTubeDiagnosticReport(context: Context): String {
    val capabilities = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        manager.getNetworkCapabilities(manager.activeNetwork)
    }.getOrNull()
    val network = when {
        capabilities == null -> "unknown"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        else -> "other"
    }
    val report = buildJsonObject {
        put("build", BuildConfig.VERSION_NAME)
        put("androidSdk", Build.VERSION.SDK_INT)
        put("createdAtMs", System.currentTimeMillis())
        put("network", network)
        put("internetValidated", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        put("events", Json.encodeToJsonElement(YouTubeNetworkLog.snapshot()))
        put("connectionProbe", Json.encodeToJsonElement(MusicTransportProbe.results.value))
    }
    return "Sonet network diagnostic\n" + Json { prettyPrint = true }.encodeToString(report)
}
