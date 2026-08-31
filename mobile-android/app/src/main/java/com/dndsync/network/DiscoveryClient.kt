package com.dndsync.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DiscoveryClient(private val context: Context) {

    private val gson = Gson()
    private var multicastLock: WifiManager.MulticastLock? = null

    data class DiscoveredDesktop(
        val deviceId: String,
        val deviceName: String,
        val deviceType: String,
        val ipAddress: String,
        val wsPort: Int
    )

    suspend fun listenForDesktop(timeoutMs: Int = 10000): DiscoveredDesktop? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("dnd_sync_discovery_lock").apply {
                setReferenceCounted(true)
                acquire()
            }

            socket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = timeoutMs
                bind(java.net.InetSocketAddress(47891))
            }

            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)

            Log.d(TAG, "Listening for Desktop UDP beacons on port 47891...")
            socket.receive(packet)

            val jsonString = String(packet.data, 0, packet.length)
            Log.d(TAG, "Received UDP beacon: $jsonString")

            val json = gson.fromJson(jsonString, JsonObject::class.java)
            if (json.has("magic") && json.get("magic").asString == "DND_SYNC_BEACON") {
                val hostFromBeacon = if (json.has("host") && !json.get("host").isJsonNull) json.get("host").asString else null
                val ip = hostFromBeacon ?: packet.address.hostAddress ?: "127.0.0.1"
                return@withContext DiscoveredDesktop(
                    deviceId = json.get("deviceId").asString,
                    deviceName = json.get("deviceName").asString,
                    deviceType = json.get("deviceType").asString,
                    ipAddress = ip,
                    wsPort = json.get("wsPort").asInt
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Discovery listen timed out or error: ${e.message}")
        } finally {
            socket?.close()
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock?.release()
                }
            } catch (_: Exception) {}
        }
        return@withContext null
    }

    companion object {
        private const val TAG = "DiscoveryClient"
    }
}
