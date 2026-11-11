package com.seolha.rechzzk

import android.webkit.CookieManager
import com.seolha.rechzzk.net.Api
import com.seolha.rechzzk.net.Http
import com.seolha.rechzzk.net.UserStatus
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object Auth {
    private val scope = MainScope()
    private val _user = MutableStateFlow<UserStatus?>(null)
    val user: StateFlow<UserStatus?> = _user.asStateFlow()

    val loggedIn: Boolean get() = _user.value?.loggedIn == true

    fun refresh() {
        if (!Http.isLoggedInCookie()) { _user.value = null; return }
        scope.launch {
            _user.value = runCatching { Api.userStatus() }.getOrNull()?.takeIf { it.loggedIn }
        }
    }

    fun logout() {
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        _user.value = null
    }
}
