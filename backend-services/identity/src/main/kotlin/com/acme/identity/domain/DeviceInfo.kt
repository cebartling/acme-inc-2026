package com.acme.identity.domain

/**
 * Represents device information for authentication tracking.
 *
 * @property deviceId The device identifier (fingerprint or UUID).
 * @property ipAddress The client's IP address.
 * @property userAgent The client's User-Agent header.
 * @property fingerprint Optional device fingerprint for tracking.
 */
data class DeviceInfo(
    val deviceId: String,
    val ipAddress: String,
    val userAgent: String,
    val fingerprint: String? = null
) {
    companion object {
        /**
         * Creates device info from request metadata.
         *
         * If no explicit fingerprint is provided, generates a device ID
         * from the IP and User-Agent hash.
         *
         * @param ipAddress The client's IP address.
         * @param userAgent The client's User-Agent.
         * @param fingerprint Optional explicit device fingerprint.
         * @return A [DeviceInfo] instance.
         */
        fun fromRequest(
            ipAddress: String,
            userAgent: String,
            fingerprint: String? = null
        ): DeviceInfo {
            val deviceId = fingerprint ?: generateDeviceId(ipAddress, userAgent)
            return DeviceInfo(
                deviceId = deviceId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                fingerprint = fingerprint
            )
        }

        /**
         * Generates a device ID from IP and User-Agent.
         *
         * This is a fallback when no explicit fingerprint is provided.
         * Uses a simple hash of IP + User-Agent.
         *
         * @param ipAddress The client's IP.
         * @param userAgent The client's User-Agent.
         * @return A device ID string.
         */
        private fun generateDeviceId(ipAddress: String, userAgent: String): String {
            val combined = "$ipAddress|$userAgent"
            val hash = combined.hashCode().toString(16)
            return "dev_$hash"
        }
    }
}
