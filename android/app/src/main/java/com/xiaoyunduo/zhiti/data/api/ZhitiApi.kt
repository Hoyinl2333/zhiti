package com.xiaoyunduo.zhiti.data.api

import com.xiaoyunduo.zhiti.data.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ZhitiApi(
    private val baseUrl: String = "https://43.136.39.211",
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun activate(code: String, deviceDigest: String): String = withContext(Dispatchers.IO) {
        val body = json.encodeToString(ActivationRequest.serializer(), ActivationRequest(code.trim(), deviceDigest))
        val request = Request.Builder().url("$baseUrl/v1/activate")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(errorMessage(response.code))
            json.decodeFromString<ActivationResponse>(response.body.string()).accessToken
        }
    }

    suspend fun catalog(token: String, verify: (ByteArray, String) -> Boolean): Catalog = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/v1/catalog")
            .header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(errorMessage(response.code))
            val bytes = response.body.bytes()
            val signature = response.header("X-Zhiti-Signature") ?: throw IOException("目录缺少签名")
            if (!verify(bytes, signature)) throw IOException("题库目录签名无效")
            json.decodeFromString<Catalog>(bytes.decodeToString())
        }
    }

    private fun errorMessage(code: Int) = when (code) {
        401 -> "激活码或授权无效"
        403 -> "激活码已停用"
        409 -> "激活码已绑定其他设备"
        429 -> "操作过于频繁，请稍后重试"
        else -> "服务器暂时不可用（$code）"
    }
}

@Serializable private data class ActivationRequest(val activationCode: String, val deviceDigest: String)
@Serializable private data class ActivationResponse(val accessToken: String)

