package com.simbridge.client.data

import com.google.gson.annotations.SerializedName

// ── WebSocket Messages ──

data class WsMessage(
    val type: String,                    // "command", "event", "webrtc", "ping", "pong"
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
    @SerializedName("to_device_id") val toDeviceId: Int? = null,
    val error: String? = null,
    @SerializedName("target_device_id") val targetDeviceId: Int? = null,
)

data class SimInfo(
    val slot: Int,
    val carrier: String,
    val number: String?,
)

// ── REST API ──

data class LoginRequest(val username: String, val password: String)

data class RegisterRequest(val username: String, val password: String)

data class AuthResponse(val token: String, @SerializedName("user_id") val userId: Int? = null)

data class GoogleAuthRequest(
    @SerializedName("id_token") val idToken: String,
)

data class DeviceRegisterRequest(val name: String, val type: String = "client")

data class DeviceInfo(
    val id: Int,
    val name: String,
    val type: String,
    @SerializedName("is_online") val isOnline: Boolean = false,
    @SerializedName("last_seen") val lastSeen: String? = null,
)

data class PairConfirmRequest(
    val code: String,
    @SerializedName("client_device_id") val clientDeviceId: Int,
)

data class PairConfirmResponse(
    val status: String,
    @SerializedName("pairing_id") val pairingId: Int? = null,
    @SerializedName("host_device_id") val hostDeviceId: Int? = null,
)

data class SmsRequest(
    @SerializedName("to_device_id") val toDeviceId: Int,
    val sim: Int,
    val to: String,
    val body: String,
)

data class CallRequest(
    @SerializedName("to_device_id") val toDeviceId: Int,
    val sim: Int,
    val to: String,
)

data class CommandResponse(
    val status: String,
    @SerializedName("req_id") val reqId: String? = null,
)

data class HistoryEntry(
    val id: Int,
    @SerializedName("from_device_id") val fromDeviceId: Int,
    @SerializedName("to_device_id") val toDeviceId: Int,
    @SerializedName("msg_type") val msgType: String,
    val payload: String,
    @SerializedName("created_at") val createdAt: String,
)

// ── UI State ──

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

enum class CallState { IDLE, DIALING, RINGING, ACTIVE }

data class SmsConversation(
    val address: String,
    val messages: List<SmsEntry>,
)

data class SmsEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val direction: String,   // "sent" or "received"
    val sim: Int?,
    val address: String,     // phone number
    val body: String,
    val status: String? = null,
)

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val direction: String,   // "OUT" or "IN"
    val summary: String,
)
