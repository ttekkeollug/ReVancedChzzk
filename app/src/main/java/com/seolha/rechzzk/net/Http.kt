package com.seolha.rechzzk.net

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    /** 네이버 도메인 요청에 WebView 로그인 쿠키(NID_AUT, NID_SES)를 붙여줌 */
    private val cookieInterceptor = Interceptor { chain ->
        val req = chain.request()
        val host = req.url.host
        val builder = req.newBuilder().header("User-Agent", UA)
        if (host.endsWith("naver.com")) {
            val cookie = runCatching { CookieManager.getInstance().getCookie("https://chzzk.naver.com") }.getOrNull()
            if (!cookie.isNullOrBlank()) builder.header("Cookie", cookie)
            builder.header("Origin", "https://chzzk.naver.com")
            builder.header("Referer", "https://chzzk.naver.com/")
        }
        val res = chain.proceed(builder.build())
        // 네이버가 응답으로 갱신해 주는 로그인 쿠키를 WebView 쿠키 저장소에 반영 (버리면 나중에 만료돼 로그아웃됨)
        if (host.endsWith("naver.com")) {
            val setCookies = res.headers("Set-Cookie")
            if (setCookies.isNotEmpty()) runCatching {
                val cm = CookieManager.getInstance()
                setCookies.forEach { cm.setCookie(req.url.toString(), it) }
                cm.flush()
            }
        }
        res
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(cookieInterceptor)
        .build()

    /** 영상 세그먼트용: 쿠키 없이, 짧은 타임아웃으로 빠르게 재시도 */
    val mediaClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val wsClient: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    suspend fun getText(url: String, accept: String? = null): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply { if (accept != null) header("Accept", accept) }.build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful && body.isBlank()) throw IOException("HTTP ${res.code}")
            body
        }
    }

    /** PUT/POST 등 본문이 있는 요청. (HTTP 코드, 응답 본문) 반환 */
    suspend fun send(method: String, url: String, json: String?): Pair<Int, String> = withContext(Dispatchers.IO) {
        val body = (json ?: "").toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(url).method(method, if (json == null && method == "GET") null else body).build()
        client.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }
    }

    fun isLoggedInCookie(): Boolean =
        runCatching { CookieManager.getInstance().getCookie("https://chzzk.naver.com") }
            .getOrNull()?.contains("NID_AUT") == true
}
