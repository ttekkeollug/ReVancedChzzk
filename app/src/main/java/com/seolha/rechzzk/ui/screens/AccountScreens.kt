package com.seolha.rechzzk.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.seolha.rechzzk.Auth
import com.seolha.rechzzk.LatencyMode
import com.seolha.rechzzk.Prefs
import com.seolha.rechzzk.net.Api
import com.seolha.rechzzk.notify.FastAlerts
import com.seolha.rechzzk.notify.LiveAlerts
import com.seolha.rechzzk.net.SearchChannel
import com.seolha.rechzzk.player.Session
import com.seolha.rechzzk.ui.C
import com.seolha.rechzzk.ui.Nav
import com.seolha.rechzzk.ui.components.Avatar
import com.seolha.rechzzk.ui.components.ChannelLine
import com.seolha.rechzzk.ui.components.Loading
import com.seolha.rechzzk.ui.components.Message
import com.seolha.rechzzk.ui.components.fmtShort
import kotlinx.coroutines.delay

@Composable
private fun BackBar(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton({ Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = C.Text) }
        Text(title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** 계정·설정 화면. 로그인 버튼을 누르면 네이버 로그인 WebView로 전환 */
@Composable
fun LoginScreen() {
    val user by Auth.user.collectAsState()
    var showWeb by remember { mutableStateOf(false) }
    LaunchedEffect(user) { if (user?.loggedIn == true) showWeb = false }
    if (showWeb) NaverLoginWebView { showWeb = false } else AccountScreen { showWeb = true }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NaverLoginWebView(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = C.Text) }
            Text("네이버 로그인", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
            CookieManager.getInstance().setAcceptCookie(true)
            WebView(ctx).apply {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        CookieManager.getInstance().flush()
                        // "로그인 상태 유지"를 자동으로 켬 → 쿠키가 앱을 다시 켜도 남는 장기 쿠키로 저장됨
                        if (url.contains("nid.naver.com")) {
                            view.evaluateJavascript(
                                "(function(){var c=document.getElementById('loginStay');" +
                                "if(c&&!c.checked){c.click();if(!c.checked){c.checked=true;c.value='on';}}})();", null)
                        }
                        // 로그인 완료 후 치지직으로 리다이렉트되면 쿠키를 확인하고 닫음
                        // 로그인 폼이 아닌 페이지에서 NID_AUT 쿠키가 생기면 로그인 완료로 판단
                        val done = CookieManager.getInstance().getCookie("https://chzzk.naver.com")?.contains("NID_AUT") == true
                        if (done && !url.contains("nidlogin.login")) {
                            Auth.refresh()
                            Auth.backupCookies()
                            onClose()
                        }
                    }
                }
                loadUrl("https://nid.naver.com/nidlogin.login?mode=form&url=https%3A%2F%2Fchzzk.naver.com%2F")
            }
        }, onRelease = { it.destroy() })
    }
}

@Composable
private fun AccountScreen(onLogin: () -> Unit) {
    val user by Auth.user.collectAsState()
    val latency by Prefs.latency.collectAsState()
    val autoPip by Prefs.autoPip.collectAsState()
    val bg by Prefs.backgroundPlay.collectAsState()
    val cleanBot by Prefs.cleanBot.collectAsState()
    val liveAlerts by Prefs.liveAlerts.collectAsState()
    val alertAll by Prefs.alertAll.collectAsState()
    val autoClaim by Prefs.autoClaim.collectAsState()
    val fastAlerts by Prefs.fastAlerts.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        item { BackBar("내 정보 · 설정") }
        item {
            if (user?.loggedIn == true) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user?.profileImageUrl, 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(user?.nickname ?: "", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Message("로그인하면 팔로잉 목록과 채팅을 쓸 수 있습니다", "네이버 로그인", onLogin)
            }
        }
        item { SettingHeader("기본 지연 모드") }
        items(LatencyMode.entries) { m ->
            Row(Modifier.fillMaxWidth().clickable { Prefs.setLatency(m) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.label, color = if (m == latency) C.Accent else C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(m.desc, color = C.Sub, fontSize = 13.sp)
                }
                if (m == latency) Text("✓", color = C.Accent, fontSize = 18.sp)
            }
        }
        item { SettingHeader("재생") }
        item { ToggleRow("홈으로 나가면 자동 PIP", autoPip, Prefs::setAutoPip) }
        item { ToggleRow("라디오 모드일 때 백그라운드 재생", bg, Prefs::setBackgroundPlay) }
        item { ToggleRow("클린봇 (가려진 채팅 숨김)", cleanBot, Prefs::setCleanBot) }
        item { SettingHeader("알림 · 통나무") }
        item { ToggleRow("방송 시작 알림", liveAlerts) { Prefs.setLiveAlerts(it); LiveAlerts.setup(ctx) } }
        if (liveAlerts) {
            item { ToggleRow("빠른 알림 (약 1분 간격)", fastAlerts) { Prefs.setFastAlerts(it); FastAlerts.sync(ctx) } }
            item {
                Text(if (fastAlerts) "상시 알림 없이 1분마다 잠깐 깨어나 확인합니다. 휴대폰을 오래 놔둬 깊은 절전 상태가 되면 10분 정도까지 늦어질 수 있습니다."
                     else "약 15분마다 확인합니다(안드로이드 제한).",
                    color = C.Dim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            }
            item { ToggleRow("알림(종) 끈 채널도 알림", alertAll, Prefs::setAlertAll) }
            item {
                Text(if (alertAll) "팔로우한 모든 채널의 방송 시작을 알립니다." else "채널 프로필에서 종(🔔)을 켠 채널이 방송을 켰을 때만 알립니다.",
                    color = C.Dim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            }
            item {
                val pm = ctx.getSystemService(android.os.PowerManager::class.java)
                val ignoring = pm.isIgnoringBatteryOptimizations(ctx.packageName)
                SettingButton(if (ignoring) "배터리 최적화 제외됨 ✓" else "배터리 최적화에서 제외하기 (알림 지연 방지)", !ignoring) {
                    runCatching {
                        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:${ctx.packageName}")))
                    }
                }
            }
            item {
                SettingButton("알림 테스트 보내기", true) {
                    val err = LiveAlerts.testNotify(ctx)
                    if (err != null) {
                        android.widget.Toast.makeText(ctx, err, android.widget.Toast.LENGTH_LONG).show()
                        runCatching {
                            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                        }
                    }
                }
            }
            item {
                val last = LiveAlerts.lastChecked()
                Text(if (last > 0) "마지막 확인: " + java.text.SimpleDateFormat("M월 d일 HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date(last))
                     else if (user?.loggedIn == true) "아직 확인 전입니다" else "로그인해야 알림을 받을 수 있습니다",
                    color = C.Dim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
        item { ToggleRow("통나무 자동 받기", autoClaim, Prefs::setAutoClaim) }
        if (user?.loggedIn == true) item {
            Text("로그아웃", color = C.Live, fontSize = 16.sp, modifier = Modifier.fillMaxWidth()
                .clickable { Auth.logout() }.padding(16.dp))
        }
    }
}

@Composable
private fun SettingHeader(t: String) =
    Text(t, color = C.Sub, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))

@Composable
private fun SettingButton(label: String, highlight: Boolean, onClick: () -> Unit) {
    Text(label, color = if (highlight) C.Accent else C.Sub, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = Color.Black))
    }
}

@Composable
fun SearchScreen() {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchChannel>?>(emptyList()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(q) {
        if (q.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(300)   // 타이핑 디바운스
        results = null
        results = runCatching { Api.searchChannels(q.trim()).data }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
            IconButton({ Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = C.Text) }
            TextField(q, { q = it }, placeholder = { Text("채널 검색") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = C.Surface2, unfocusedContainerColor = C.Surface2, cursorColor = C.Accent),
                modifier = Modifier.weight(1f).focusRequester(focus))
        }
        val list = results
        when {
            list == null -> Loading()
            list.isEmpty() && q.isNotBlank() -> Message("검색 결과가 없습니다")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list, key = { it.channel.channelId }) { r ->
                    val ch = r.channel
                    Row(Modifier.fillMaxWidth().clickable { Nav.openChannel(ch.channelId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box { Avatar(ch.channelImageUrl, 52.dp, liveRing = ch.openLive) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            ChannelLine(ch.channelName, null, ch.verifiedMark, 0.dp)
                            Text("팔로워 ${fmtShort(ch.followerCount)}", color = C.Sub, fontSize = 13.sp)
                        }
                        if (ch.openLive) Text("LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(C.Live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}
