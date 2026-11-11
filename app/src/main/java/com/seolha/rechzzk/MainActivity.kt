package com.seolha.rechzzk

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.seolha.rechzzk.player.NowPlaying
import com.seolha.rechzzk.player.PlaybackService
import com.seolha.rechzzk.player.PlayerHolder
import com.seolha.rechzzk.player.Session
import com.seolha.rechzzk.ui.C
import com.seolha.rechzzk.ui.Nav
import com.seolha.rechzzk.ui.ReChzzkTheme
import com.seolha.rechzzk.ui.Route
import com.seolha.rechzzk.ui.screens.CategoryScreen
import com.seolha.rechzzk.ui.screens.ChannelScreen
import com.seolha.rechzzk.ui.screens.ClipsScreen
import com.seolha.rechzzk.ui.screens.ExploreScreen
import com.seolha.rechzzk.ui.screens.FollowingScreen
import com.seolha.rechzzk.ui.screens.HomeScreen
import com.seolha.rechzzk.ui.screens.LoginScreen
import com.seolha.rechzzk.ui.screens.MiniPlayer
import com.seolha.rechzzk.ui.screens.PlayerScreen
import com.seolha.rechzzk.ui.screens.PlayerSurface
import com.seolha.rechzzk.ui.screens.SearchScreen
import com.seolha.rechzzk.ui.screens.blockTouches
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var inPip by mutableStateOf(false)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = updatePipParams()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ReChzzkTheme { Root(inPip) } }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        PlayerHolder.player.addListener(playerListener)
        lifecycleScope.launch {
            combine(Session.now, PlayerHolder.radio, Prefs.autoPip) { _, _, _ -> }.collect { updatePipParams() }
        }
        // 로그인/로그아웃 시 채팅을 다시 연결해 전송 권한 갱신
        lifecycleScope.launch {
            Auth.user.map { it?.userIdHash }.distinctUntilChanged().drop(1).collect {
                Session.reconnectChat()
                com.seolha.rechzzk.notify.AlertService.sync(this@MainActivity)
            }
        }
        handleIntent(intent)
        com.seolha.rechzzk.notify.LiveAlerts.checkNow(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** https://chzzk.naver.com/live/{channelId} 링크로 열면 바로 재생 */
    private fun handleIntent(intent: Intent?) {
        val seg = intent?.data?.pathSegments ?: return
        if (seg.size >= 2 && seg[0] == "live") Nav.openLive(seg[1])
    }

    override fun onStart() {
        super.onStart()
        // 화면이 보이는 상태에서만 포그라운드 서비스를 시작할 수 있으므로 여기서 동기화
        com.seolha.rechzzk.notify.AlertService.sync(this)
        // MediaController로 서비스에 연결해 두면 백그라운드 재생 시 알림·포그라운드 서비스가 유지됨
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        super.onStop()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        if (!isInPictureInPictureMode && !keepPlayingInBackground()) PlayerHolder.player.pause()
    }

    override fun onDestroy() {
        PlayerHolder.player.removeListener(playerListener)
        super.onDestroy()
    }

    private fun keepPlayingInBackground() = PlayerHolder.radio.value && Prefs.backgroundPlay.value

    private fun canAutoPip(): Boolean {
        val now = Session.now.value ?: return false
        val err = (now as? NowPlaying.Live)?.error ?: (now as? NowPlaying.Vod)?.error
        return err == null && Prefs.autoPip.value && !PlayerHolder.radio.value && PlayerHolder.player.isPlaying
    }

    private fun pipParams(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= 31) {
            b.setAutoEnterEnabled(canAutoPip())   // Android 12+: 홈 제스처와 함께 매끄럽게 PIP 진입
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    fun updatePipParams() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            runCatching { setPictureInPictureParams(pipParams()) }
        }
    }

    fun enterPip() {
        if (Session.now.value == null) return
        if (PlayerHolder.radio.value) PlayerHolder.setRadio(false)
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    /** Android 11 이하: 홈 버튼을 누르면 여기서 PIP 진입 */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && canAutoPip()) runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        // PIP 창을 X로 닫으면 액티비티는 STARTED가 아닌 상태로 돌아옴 → 재생 정지
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !keepPlayingInBackground()) {
            PlayerHolder.player.pause()
        }
    }

    fun setFullscreen(on: Boolean) {
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val ctl = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            ctl.show(WindowInsetsCompat.Type.systemBars())
            // 세로로 고정했다가 다시 자동 회전 허용
            window.decorView.postDelayed({ requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }, 1500)
        }
    }
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val tabs = listOf(Tab("홈", Icons.Filled.Home), Tab("팔로잉", Icons.Filled.People),
    Tab("클립", Icons.Filled.PlayCircle), Tab("탐색", Icons.Filled.Explore))

@Composable
private fun Root(inPip: Boolean) {
    if (inPip) {
        PlayerSurface(Modifier.fillMaxSize())
        return
    }
    val now by Session.now.collectAsState()
    val top = Nav.top
    BackHandler(enabled = top != null && top != Route.Player) { Nav.pop() }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).then(if (Nav.tab == 2) Modifier else Modifier.statusBarsPadding())) {
                when (Nav.tab) {
                    0 -> HomeScreen()
                    1 -> FollowingScreen()
                    2 -> ClipsScreen()
                    else -> ExploreScreen()
                }
            }
            NavigationBar(containerColor = C.Bg, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = Nav.tab == i, onClick = { Nav.tab = i; Nav.stack.removeAll { it != Route.Player }; if (Nav.top == Route.Player) Nav.pop() },
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = Color.White,
                            unselectedIconColor = C.Dim, unselectedTextColor = C.Dim, indicatorColor = Color.Transparent),
                    )
                }
            }
        }

        // 위에 쌓이는 화면들
        when (val r = top) {
            Route.Player -> PlayerScreen()
            Route.Login -> Overlay { LoginScreen() }
            Route.Search -> Overlay { SearchScreen() }
            is Route.Category -> Overlay { CategoryScreen(r) }
            is Route.Channel -> Overlay { ChannelScreen(r) }
            null -> {}
        }

        if (now != null && top != Route.Player) {
            MiniPlayer(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 92.dp))
        }
    }
}

@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().blockTouches().background(C.Bg).statusBarsPadding().navigationBarsPadding()) { content() }
}
