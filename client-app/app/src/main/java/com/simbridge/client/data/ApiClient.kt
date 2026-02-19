package com.simbridge.client.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(private val prefs: Prefs) {

    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // ── Auth ──

    fun login(serverUrl: String, username: String, password: String): Result<AuthResponse> {
        val body = gson.toJson(LoginRequest(username, password)).toRequestBody(jsonType)
        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/auth/login")
            .post(body)
            .build()
        return execute(request, AuthResponse::class.java)
    }

    fun register(serverUrl: String, username: String, password: String): Result<AuthResponse> {
        val body = gson.toJson(RegisterRequest(username, password)).toRequestBody(jsonType)
        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/auth/register")
            .post(body)
            .build()
        return execute(request, AuthResponse::class.java)
    }

    // ── Devices ──

    fun registerDevice(name: String): Result<DeviceInfo> {
        val body = gson.toJson(DeviceRegisterRequest(name, "client")).toRequestBody(jsonType)
        val request = authedRequest("/devices").post(body).build()
        return execute(request, DeviceInfo::class.java)
    }

    fun listDevices(): Result<List<DeviceInfo>> {
        val request = authedRequest("/devices").get().build()
        return executeList(request)
    }

    // ── Pairing ──

    fun confirmPairing(code: String): Result<PairConfirmResponse> {
        val body = gson.toJson(PairConfirmRequest(code, prefs.deviceId)).toRequestBody(jsonType)
        val request = authedRequest("/pair/confirm").post(body).build()
        return execute(request, PairConfirmResponse::class.java)
    }

    // ── Commands (REST fallback) ──

    fun sendSms(sim: Int, to: String, body: String): Result<CommandResponse> {
        val payload = gson.toJson(SmsRequest(prefs.pairedHostId, sim, to, body))
            .toRequestBody(jsonType)
        val request = authedRequest("/sms").post(payload).build()
        return execute(request, CommandResponse::class.java)
    }

    fun makeCall(sim: Int, to: String): Result<CommandResponse> {
        val payload = gson.toJson(CallRequest(prefs.pairedHostId, sim, to))
            .toRequestBody(jsonType)
        val request = authedRequest("/call").post(payload).build()
        return execute(request, CommandResponse::class.java)
    }

    fun getSims(): Result<CommandResponse> {
        val request = authedRequest("/sims?host_device_id=${prefs.pairedHostId}")
            .get().build()
        return execute(request, CommandResponse::class.java)
    }

    // ── History ──

    fun getHistory(limit: Int = 50): Result<List<HistoryEntry>> {
        val request = authedRequest("/history?limit=$limit").get().build()
        return executeList(request)
    }

    // ── Helpers ──

    private fun authedRequest(path: String): Request.Builder {
        return Request.Builder()
            .url("${prefs.serverUrl}$path")
            .header("Authorization", "Bearer ${prefs.token}")
    }

    private fun <T> execute(request: Request, clazz: Class<T>): Result<T> {
        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                Result.success(gson.fromJson(responseBody, clazz))
            } else {
                Result.failure(IOException("HTTP ${response.code}: ${responseBody ?: "no body"}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private inline fun <reified T> executeList(request: Request): Result<List<T>> {
        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                val type = TypeToken.getParameterized(List::class.java, T::class.java).type
                Result.success(gson.fromJson(responseBody, type))
            } else {
                Result.failure(IOException("HTTP ${response.code}: ${responseBody ?: "no body"}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
