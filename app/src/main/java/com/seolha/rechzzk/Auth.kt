package com.seolha.rechzzk

import android.webkit.CookieManager
import com.seolha.rechzzk.net.Api
import com.seolha.rechzzk.net.ApiException
import com.seolha.rechzzk.net.Http
import com.seolha.rechzzk.net.UserStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 네이버 로그인 상태.
 * 로그인이 저절로 풀려 보이던 원인과 대책:
 *  1) "로그인 상태 유지"를 안 켜면 쿠키가 세션 쿠키라 앱 프로세스가 다시 시작되면 사라짐
 *     → 로그인 화면에서 자동으로 체크(AccountScreens), 그리고 쿠키를 따로 백업해 두었다가 사라지면 복원
 *  2) 앱 시작 직후 네트워크가 잠깐 안 돼 상태 확인이 실패하면 로그아웃으로 처리했음
 *     → 마지막 상태를 유지하고 재시도, 서버가 '로그인 안 됨'이라고 확실히 답할 때만 로그아웃
 *  3) 서버가 갱신해 준 쿠키(Set-Cookie)를 버리고 있었음 → Http에서 WebView 쿠키 저장소에 반영
 */
object Auth {
    private val scope = MainScope()
    private val _user = MutableStateFlow<UserStatus?>(null)
    val user: StateFlow<UserStatus?> = _user.asStateFlow()

    val loggedIn: Boolean get() = _user.value?.loggedIn == true

    private const val KEY_USER = "authUser"
    private const val KEY_COOKIES = "authCookies"
    private val NAMES = listOf("NID_AUT", "NID_SES", "NID_JKL")
    private var job: Job? = null

    /** 앱 시작 시: 마지막 로그인 정보를 바로 보여주고(깜빡임 방지) 백그라운드에서 확인 */
    fun init() {
        restoreCookiesIfMissing()
        if (Http.isLoggedInCookie()) {
            _user.value = runCatching {
                Http.json.decodeFromString(UserStatus.serializer(), Prefs.raw.getString(KEY_USER, null)!!)
            }.getOrNull()
        }
        refresh()
    }

    fun refresh() {
        restoreCookiesIfMissing()
        if (!Http.isLoggedInCookie()) { setUser(null); return }
        job?.cancel()
        job = scope.launch {
            // 네트워크 실패는 상태를 바꾸지 않고 몇 번 재시도
            for (wait in listOf(0L, 2_000L, 5_000L, 15_000L)) {
                delay(wait)
                try {
                    val s = Api.userStatus()
                    if (s.loggedIn) { setUser(s); backupCookies() }
                    else { setUser(null); Prefs.raw.edit().remove(KEY_COOKIES).apply() }  // 서버에서 끊긴 세션은 백업도 폐기
                    return@launch
                } catch (e: ApiException) {
                    // 서버가 인증 실패(401 등)를 확실히 알려준 경우만 로그아웃
                    if (e.code == 401 || e.code == 403) {
                        setUser(null); Prefs.raw.edit().remove(KEY_COOKIES).apply(); return@launch
                    }
                } catch (_: Exception) { /* 네트워크 문제: 다음 시도 */ }
            }
        }
    }

    private fun setUser(s: UserStatus?) {
        _user.value = s
        val e = Prefs.raw.edit()
        if (s == null) e.remove(KEY_USER) else e.putString(KEY_USER, Http.json.encodeToString(UserStatus.serializer(), s))
        e.apply()
    }

    private fun currentCookies(): Map<String, String> {
        val raw = runCatching { CookieManager.getInstance().getCookie("https://chzzk.naver.com") }.getOrNull() ?: return emptyMap()
        return raw.split(";").mapNotNull {
            val kv = it.trim().split("=", limit = 2)
            if (kv.size == 2 && kv[0] in NAMES) kv[0] to kv[1] else null
        }.toMap()
    }

    /** 로그인 쿠키를 앱 저장소에 백업 (세션 쿠키로 저장돼 사라지는 경우 대비) */
    fun backupCookies() {
        val c = currentCookies()
        if (c.containsKey("NID_AUT") && c.containsKey("NID_SES")) {
            Prefs.raw.edit().putString(KEY_COOKIES, c.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
        }
    }

    /** WebView 쿠키에서 로그인 쿠키가 사라졌으면 백업으로 복원(1년 유지 쿠키로 다시 저장) */
    private fun restoreCookiesIfMissing() {
        if (Http.isLoggedInCookie()) return
        val saved = Prefs.raw.getString(KEY_COOKIES, null) ?: return
        val cm = CookieManager.getInstance()
        saved.lines().filter { it.contains("=") }.forEach { kv ->
            cm.setCookie("https://naver.com", "$kv; Domain=.naver.com; Path=/; Max-Age=31536000; Secure; HttpOnly")
        }
        cm.flush()
    }

    fun logout() {
        Prefs.raw.edit().remove(KEY_COOKIES).remove(KEY_USER).apply()
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        job?.cancel()
        _user.value = null
    }
}
