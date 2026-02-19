package com.simbridge.host.data

import com.google.gson.Gson
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

    /**
     * POST /auth/login — returns JWT token.
     */
    fun login(serverUrl: String, username: String, password: String): Result<LoginResponse> {
        val body = gson.toJson(LoginRequest(username, password))
            .toRequestBody(jsonType)
        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/auth/login")
            .post(body)
            .build()
        return execute(request, LoginResponse::class.java)
    }

    /**
     * POST /devices — register this device as a host.
     */
    fun registerDevice(name: String): Result<DeviceResponse> {
        val body = gson.toJson(DeviceRegisterRequest(name, "host"))
            .toRequestBody(jsonType)
        val request = Request.Builder()
            .url("${prefs.serverUrl}/devices")
            .header("Authorization", "Bearer ${prefs.token}")
            .post(body)
            .build()
        return execute(request, DeviceResponse::class.java)
    }

    /**
     * POST /pair — pair with a client device using a pairing code.
     */
    fun pair(code: String): Result<PairResponse> {
        val body = gson.toJson(PairRequest(code))
            .toRequestBody(jsonType)
        val request = Request.Builder()
            .url("${prefs.serverUrl}/pair")
            .header("Authorization", "Bearer ${prefs.token}")
            .post(body)
            .build()
        return execute(request, PairResponse::class.java)
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
}
