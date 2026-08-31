package com.dndsync.model

data class PairedDesktop(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String = "macos",
    val host: String,
    val port: Int = 47890,
    val sessionToken: String? = null,
    val isOnline: Boolean = false,
    val lastSeenAt: Long = System.currentTimeMillis()
)
