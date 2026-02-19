package com.simbridge.host.data

import com.google.gson.annotations.SerializedName

// ── WebSocket Messages ──

data class WsMessage(
    val type: String,                    // "command", "event", "webrtc"
    val cmd: String? = null,             // for type="command"
    val event: String? = null,           // for type="event"
    val action: String? = null,          // for type="webrtc"
    val sim: Int? = null,
    val to: String? = null,
    val from: String? = null,
    val body: String? = null,
    val status: String? = null,
    val state: String? = null,
    val sims: List<SimInfo>? = null,
    val sdp: String? = null,
    val candidate: String? = null,
    @SerializedName("sdpMid") val sdpMid: String? = null,
    @SerializedName("sdpMLineIndex") val sdpMLineIndex: Int? = null,
    @SerializedName("req_id") val reqId: String? = null,
    @SerializedName("from_device_id") val fromDeviceId: Int? = null,
)

data class SimInfo(
    val slot: Int,
    val carrier: String,
    val number: String?,
)

// ── REST API ──

data class LoginRequest(
    val username: String,
    val password: String,
)

data class LoginResponse(
    val token: String,
)

data class GoogleAuthRequest(
    @SerializedName("id_token") val idToken: String,
)

data class DeviceRegisterRequest(
    val name: String,
    val type: String = "host",
)

data class DeviceResponse(
    val id: Int,
    val name: String,
    val type: String,
    @SerializedName("paired_with") val pairedWith: Int? = null,
)

data class PairRequest(
    val code: String,
)

data class PairResponse(
    val status: String,
    @SerializedName("paired_device_id") val pairedDeviceId: Int? = null,
)

// ── UI State ──

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val direction: String, // "IN" or "OUT"
    val summary: String,
)
