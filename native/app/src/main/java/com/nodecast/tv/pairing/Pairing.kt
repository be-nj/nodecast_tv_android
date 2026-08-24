package com.nodecast.tv.pairing

import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

object Pairing {

    const val PORT = 8765

    /**
     * Four-digit pairing code, generated once and kept stable so an already
     * paired phone survives app restarts. Human fallback only — the QR code
     * carries the long token below.
     */
    fun code(context: Context): String {
        val prefs = context.getSharedPreferences("pairing", Context.MODE_PRIVATE)
        prefs.getString("code", null)?.let { return it }
        val code = "%04d".format(SecureRandom().nextInt(10_000))
        prefs.edit().putString("code", code).apply()
        return code
    }

    /** 128-bit random token embedded in the QR code; not brute-forceable. */
    fun token(context: Context): String {
        val prefs = context.getSharedPreferences("pairing", Context.MODE_PRIVATE)
        prefs.getString("token", null)?.let { return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("token", token).apply()
        return token
    }

    /** Best-guess LAN IPv4 address of this device, or null when offline. */
    fun lanAddress(): String? {
        val candidates = runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .map { it.hostAddress }
                .filterNotNull()
                .toList()
        }.getOrDefault(emptyList())
        return candidates.firstOrNull()
    }

    fun remoteUrl(address: String): String = "http://$address:$PORT"

    fun pairingUrl(context: Context, address: String): String =
        "${remoteUrl(address)}/?t=${token(context)}"
}
