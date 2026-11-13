package com.seolha.rechzzk.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.ui.unit.coerceIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.SpeakerNotesOff
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C as MC
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.seolha.rechzzk.Auth
import com.seolha.rechzzk.LatencyMode
import com.seolha.rechzzk.MainActivity
import com.seolha.rechzzk.Prefs
import com.seolha.rechzzk.chat.ChatMessage
import com.seolha.rechzzk.player.NowPlaying
import com.seolha.rechzzk.player.PlayerHolder
import com.seolha.rechzzk.player.Session
import com.seolha.rechzzk.ui.C
import com.seolha.rechzzk.ui.Nav
import com.seolha.rechzzk.ui.Route
import com.seolha.rechzzk.ui.components.Avatar
import com.seolha.rechzzk.ui.components.ChannelLine
import com.seolha.rechzzk.ui.components.TagRow
import com.seolha.rechzzk.ui.components.fmtCount
import com.seolha.rechzzk.ui.components.fmtDuration
import com.seolha.rechzzk.ui.components.uptime
import kotlinx.coroutines.delay

/**
 * 위에 덮인 화면의 빈 곳을 눌러도 아래 화면(홈 목록 등)이 눌리지 않도록 터치를 여기서 받아 막음.
 * Compose는 터치 처리기가 없는 영역의 터치를 아래 형제 레이아웃으로 넘기기 때문에 필요.
 */
fun Modifier.blockTouches(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent() }
}

/** 플레이어 컨트롤 표시 상태. 방송 정보 패널도 같은 상태를 따라 나타났다 사라짐 */
class ControlsUi {
    var visible by mutableStateOf(true)
    /** 값이 바뀔 때마다 자동 숨김 타이머가 다시 시작됨 */
    var touch by mutableIntStateOf(0)
    fun poke() { touch++ }
}

/** 플레이어 상태를 Compose 상태로 노출 */
class PlayerUiState {
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var offsetMs by mutableStateOf<Long?>(null)
    var speed by mutableFloatStateOf(1f)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
}

@Composable
fun rememberPlayerUiState(): PlayerUiState {
    val st = remember { PlayerUiState() }
    val p = PlayerHolder.player
    DisposableEffect(p) {
        val l = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                st.isPlaying = player.isPlaying
                st.buffering = player.playbackState == Player.STATE_BUFFERING
            }
        }
        p.addListener(l)
        st.isPlaying = p.isPlaying
        onDispose { p.removeListener(l) }
    }
    LaunchedEffect(p) {
        while (true) {
            st.offsetMs = PlayerHolder.liveOffsetMs()
            st.speed = p.playbackParameters.speed
            st.position = p.currentPosition
            st.duration = p.duration.takeIf { it != MC.TIME_UNSET } ?: 0L
            delay(500)
        }
    }
    return st
}

@Composable
fun PlayerSurface(modifier: Modifier = Modifier) {
    val radio by PlayerHolder.radio.collectAsState()
    val now by Session.now.collectAsState()
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                }
            },
            update = { if (it.player !== PlayerHolder.player) it.player = PlayerHolder.player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        if (radio) {
            Column(Modifier.fillMaxSize().background(Color(0xFF101114)), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Avatar(now?.channelImage, 56.dp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Headphones, null, tint = C.Accent, modifier = Modifier.size(16.dp))
                    Text(" 라디오 모드", color = C.Sub, fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen() {
    val now by Session.now.collectAsState()
    val activity = LocalContext.current as? MainActivity
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val st = rememberPlayerUiState()
    val controls = remember { ControlsUi() }
    var showSheet by remember { mutableStateOf(false) }

    BackHandler {
        if (landscape) activity?.setFullscreen(false) else Nav.pop()
    }
    val cur = now ?: run { LaunchedEffect(Unit) { Nav.pop() }; return }

    if (landscape) {
        val chatOpen by Prefs.landscapeChat.collectAsState()
        val showChat = chatOpen && cur is NowPlaying.Live && cur.error == null
        Box(Modifier.fillMaxSize().background(Color.Black).blockTouches()) {
            // 채팅을 열면 영상은 남은 공간에 맞춰 줄어들고, 채팅은 오른쪽에 붙음
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PlayerSurface(Modifier.fillMaxSize())
                    PlayerControls(cur, st, controls, fullscreen = true, onMore = { showSheet = true },
                        chatOpen = if (cur is NowPlaying.Live) chatOpen else null,
                        onToggleChat = { Prefs.setLandscapeChat(!chatOpen) })
                }
                AnimatedVisibility(showChat,
                    enter = androidx.compose.animation.expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                    exit = androidx.compose.animation.shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()) {
                    LandscapeChatPanel(cur as NowPlaying.Live) { Prefs.setLandscapeChat(false) }
                }
            }
        }
    } else {
        // 영상을 아래로 끌면 화면이 손가락을 따라 내려가고, 충분히 내리거나 빠르게 튕기면 미니 플레이어로 전환
        val dragY = remember { androidx.compose.animation.core.Animatable(0f) }
        val dragScope = rememberCoroutineScope()
        var screenH by remember { mutableFloatStateOf(1f) }
        val dragState = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
            dragScope.launch { dragY.snapTo((dragY.value + delta).coerceAtLeast(0f)) }
        }
        Column(Modifier.fillMaxSize()
            .onSizeChanged { screenH = it.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                val p = (dragY.value / screenH).coerceIn(0f, 1f)
                translationY = dragY.value
                scaleX = 1f - p * 0.15f; scaleY = scaleX
                alpha = 1f - p * 0.6f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)
            }
            .blockTouches().background(C.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .draggable(dragState, androidx.compose.foundation.gestures.Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if (dragY.value > screenH * 0.22f || velocity > 1800f) {
                            dragY.animateTo(screenH, androidx.compose.animation.core.tween(160))
                            Nav.pop()   // 재생은 그대로, 미니 플레이어로 전환
                        } else {
                            dragY.animateTo(0f, androidx.compose.animation.core.spring())
                        }
                    })) {
                PlayerSurface(Modifier.fillMaxSize())
                PlayerControls(cur, st, controls, fullscreen = false, onMore = { showSheet = true })
            }
            when (cur) {
                is NowPlaying.Live -> LiveInfoAndChat(cur, controls)
                is NowPlaying.Vod -> VodInfo(cur)
            }
        }
    }
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, containerColor = C.Surface) {
            PlayerSettings(cur is NowPlaying.Live) { showSheet = false }
        }
    }
}

@Composable
private fun PlayerControls(cur: NowPlaying, st: PlayerUiState, ui: ControlsUi, fullscreen: Boolean, onMore: () -> Unit,
                           chatOpen: Boolean? = null, onToggleChat: () -> Unit = {}) {
    val activity = LocalContext.current as? MainActivity
    val radio by PlayerHolder.radio.collectAsState()
    val latency by Prefs.latency.collectAsState()
    var visible by ui::visible
    var touch by ui::touch
    LaunchedEffect(visible, touch, st.isPlaying) {
        if (visible && st.isPlaying) { delay(3500); visible = false }
    }
    val isLive = cur is NowPlaying.Live
    val error = (cur as? NowPlaying.Live)?.error ?: (cur as? NowPlaying.Vod)?.error

    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { visible = !visible; touch++ }) {
        if (error != null) {
            Text(error, color = C.Sub, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
        } else if (st.buffering) {
            CircularProgressIndicator(color = C.Accent, strokeWidth = 2.dp, modifier = Modifier.size(36.dp).align(Alignment.Center))
        }

        // 라이브 지연이 목표보다 많이 벌어지면 항상 보이는 "실시간으로" 버튼
        val off = st.offsetMs
        val behind = isLive && off != null && off > latency.behindThresholdMs
        if (behind && !visible) {
            JumpLiveChip(Modifier.align(Alignment.BottomStart).padding(10.dp))
        }

        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Filled.KeyboardArrowDown, "접기") {
                        if (fullscreen) activity?.setFullscreen(false) else Nav.pop()
                    }
                    if (isLive) {
                        Text("LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(C.Live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                        if (off != null) {
                            Spacer(Modifier.width(6.dp))
                            val speedTxt = if (st.speed > 1.005f) " ⏩${"%.2f".format(st.speed)}x" else ""
                            Text("지연 ${"%.1f".format(off / 1000f)}초$speedTxt", color = Color.White, fontSize = 12.sp,
                                modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconBtn(Icons.Filled.Headphones, "라디오 모드", tint = if (radio) C.Accent else Color.White) {
                        PlayerHolder.setRadio(!radio)
                    }
                    if (fullscreen && chatOpen != null) {
                        IconBtn(if (chatOpen) Icons.Filled.SpeakerNotesOff else Icons.AutoMirrored.Filled.Chat,
                            if (chatOpen) "채팅 닫기" else "채팅 열기", tint = if (chatOpen) C.Accent else Color.White) {
                            onToggleChat(); touch++
                        }
                    }
                    IconBtn(Icons.Filled.PictureInPictureAlt, "PIP") { activity?.enterPip() }
                    IconBtn(Icons.Filled.MoreVert, "설정", onClick = onMore)
                }
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    if (!isLive) IconBtn(Icons.Filled.Replay10, "10초 뒤로", size = 36.dp) {
                        PlayerHolder.player.seekBack(); touch++
                    }
                    Spacer(Modifier.width(20.dp))
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0x55000000))
                        .clickable {
                            val p = PlayerHolder.player
                            if (p.isPlaying) p.pause() else {
                                if (p.playbackState == Player.STATE_IDLE) p.prepare()
                                p.play(); if (isLive) PlayerHolder.jumpToLive()   // 라이브는 재개 시 엣지로 복귀
                            }
                            touch++
                        }, contentAlignment = Alignment.Center) {
                        Icon(if (st.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White,
                            modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.width(20.dp))
                    if (!isLive) IconBtn(Icons.Filled.Forward10, "10초 앞으로", size = 36.dp) {
                        PlayerHolder.player.seekForward(); touch++
                    }
                }
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (isLive) {
                        if (behind) JumpLiveChip(Modifier) else Text("● 실시간", color = C.Live, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                        Spacer(Modifier.weight(1f))
                    } else {
                        Text(fmtDuration(st.position / 1000), color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = if (st.duration > 0) (st.position.toFloat() / st.duration).coerceIn(0f, 1f) else 0f,
                            onValueChange = { PlayerHolder.player.seekTo((it * st.duration).toLong()); touch++ },
                            colors = SliderDefaults.colors(thumbColor = C.Accent, activeTrackColor = C.Accent),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(fmtDuration(st.duration / 1000), color = Color.White, fontSize = 12.sp)
                    }
                    IconBtn(if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, "전체화면") {
                        activity?.setFullscreen(!fullscreen)
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpLiveChip(modifier: Modifier) {
    Text("▶▶ 실시간으로", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(C.Accent)
            .clickable { PlayerHolder.jumpToLive() }.padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
private fun IconBtn(icon: ImageVector, desc: String, tint: Color = Color.White, size: androidx.compose.ui.unit.Dp = 26.dp,
                    onClick: () -> Unit) {
    IconButton(onClick) { Icon(icon, desc, tint = tint, modifier = Modifier.size(size)) }
}

@Composable
private fun LiveInfoAndChat(cur: NowPlaying.Live, controls: ControlsUi) {
    val d = cur.detail
    val canSend by Session.chat.canSend.collectAsState()
    val user by Auth.user.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    var showPrediction by remember { mutableStateOf(false) }
    var showCheese by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        Session.toasts.collect { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show() }
    }
    if (showPrediction) PredictionSheet { showPrediction = false }
    if (showCheese) CheeseSheet { showCheese = false }

    val prediction by Session.prediction.collectAsState()
    val pinned by Session.chat.pinned.collectAsState()

    Column {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                pinned?.let { PinnedBar(it) }
                ChatMessages(Modifier.weight(1f).fillMaxWidth())
            }
            // 진행 중인 투표는 항상 오른쪽 위에 작게 표시
            if (prediction?.status == "ACTIVE") {
                Text("🗳 투표", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = if (pinned != null) 62.dp else 8.dp, end = 10.dp)
                        .clip(RoundedCornerShape(12.dp)).background(C.Accent)
                        .clickable { showPrediction = true }.padding(horizontal = 10.dp, vertical = 5.dp))
            }
            // 화면(영상)을 건드려 컨트롤이 보이는 동안만 방송 정보가 채팅 위로 나타남
            androidx.compose.animation.AnimatedVisibility(controls.visible && cur.error == null,
                enter = fadeIn() + androidx.compose.animation.expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top)) {
                Column(Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { controls.poke() }  // 만지는 동안은 숨기지 않음
                    .padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(d?.liveTitle ?: "", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 3,
                        overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    TagRow(d?.tags ?: emptyList(), d?.liveCategoryValue)
                    Spacer(Modifier.height(6.dp))
                    if (d != null) {
                        val up = remember(tick, d.openDate) { uptime(d.openDate) ?: "" }
                        Text("${fmtCount(d.concurrentUserCount)}명 시청 중 · $up 스트리밍 중", color = C.Sub, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    d?.channel?.let { ch ->
                        Box(Modifier.clickable { Nav.openChannel(ch.channelId) }) {
                            ChannelLine(ch.channelName, ch.channelImageUrl, ch.verifiedMark, 32.dp)
                        }
                    }
                    if (user?.loggedIn == true) PowerBar { controls.poke(); showPrediction = true }
                }
            }
        }
        ChatInput(loggedIn = user?.loggedIn == true, canSend = canSend, chatActive = d?.chatActive != false) { showCheese = true }
    }
}

/** 고정 메시지: 한 줄로 보이고, 누르면 전체 내용과 작성자를 펼침. X로 이번 시청 동안 숨김 */
@Composable
private fun PinnedBar(m: ChatMessage) {
    var expanded by remember(m.text) { mutableStateOf(false) }
    var hidden by remember(m.text) { mutableStateOf(false) }
    if (hidden) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        .border(1.dp, C.Surface2, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(C.Bg)
        .clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PushPin, "고정 메시지", tint = C.Accent, modifier = Modifier.size(18.dp))
            Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(16.dp).background(C.Surface2))
            Text(m.text.replace(Regex("\\{:[^:{}]+:\\}"), "").trim(), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = if (expanded) 8 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (expanded) IconButton({ hidden = true }, Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, "고정 메시지 숨기기", tint = C.Sub, modifier = Modifier.size(18.dp))
            } else Icon(Icons.Filled.MoreHoriz, null, tint = C.Sub, modifier = Modifier.size(20.dp))
        }
        if (expanded) Text("${m.nickname}님이 고정함", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(start = 29.dp, top = 6.dp))
    }
}

/**
 * 채팅 목록. 사용자가 직접 위로 올렸을 때만 자동 스크롤을 멈춤.
 * (위치 인덱스로 판단하면 PIP 복귀 직후처럼 채팅이 한꺼번에 몰려올 때 기준이 밀려 멈춰버림)
 * fontScale: 가로 전체화면 패널에서는 글자를 조금 작게.
 */
@Composable
private fun ChatMessages(modifier: Modifier, fontScale: Float = 1f) {
    val messages by Session.chat.messages.collectAsState()
    val cleanBot by Prefs.cleanBot.collectAsState()
    val visible = if (cleanBot) messages.filter { !it.blinded } else messages
    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    var userDragged by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is androidx.compose.foundation.interaction.DragInteraction.Start) userDragged = true
        }
    }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && userDragged) {
                userDragged = false
                follow = !listState.canScrollBackward   // reverseLayout: backward = 최신(아래) 방향
            }
        }
    }
    LaunchedEffect(visible.lastOrNull()?.id, follow) {
        if (follow && visible.isNotEmpty()) listState.scrollToItem(0)
    }
    Box(modifier) {
        LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
            items(visible.asReversed(), key = { it.id }) { ChatLine(it, fontScale) }
        }
        if (!follow) {
            Text("↓ 최신 채팅 보기", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).clip(RoundedCornerShape(16.dp))
                    .background(C.Accent).clickable { follow = true }.padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun ChatLine(m: ChatMessage, fontScale: Float = 1f) {
    val inline = remember(m.id) { mutableMapOf<String, InlineTextContent>() }
    val text = remember(m.id) {
        buildAnnotatedString {
            // 닉네임 앞 배지
            m.badges.forEachIndexed { i, url ->
                val id = "b$i"
                appendInlineContent(id, " ")
                inline[id] = InlineTextContent(Placeholder((19 * fontScale).sp, (19 * fontScale).sp, PlaceholderVerticalAlign.Center)) {
                    AsyncImage(url, null, Modifier.fillMaxSize().padding(end = 2.dp), contentScale = ContentScale.Fit)
                }
            }
            if (m.badges.isNotEmpty()) append(" ")
            withStyle(SpanStyle(color = if (m.isStreamer) C.Accent else C.nick(m.userIdHash), fontWeight = FontWeight.SemiBold)) {
                append(m.nickname)
            }
            append("  ")
            if (m.donation > 0) withStyle(SpanStyle(color = Color(0xFFFFC857), fontWeight = FontWeight.Bold)) {
                append("🧀 ${fmtCount(m.donation)}  ")
            }
            if (m.blinded) {
                withStyle(SpanStyle(color = C.Dim)) { append("클린봇이 부적절한 표현을 감지했습니다") }
                return@buildAnnotatedString
            }
            // {:emoji_name:} 형식의 이모티콘을 이미지로 치환
            val re = Regex("\\{:([^:{}]+):\\}")
            var last = 0
            re.findAll(m.text).forEach { mr ->
                append(m.text.substring(last, mr.range.first))
                val url = m.emojis[mr.groupValues[1]]
                if (url != null) {
                    val id = "e${mr.range.first}"
                    appendInlineContent(id, mr.value)
                    inline[id] = InlineTextContent(Placeholder(22.sp, 22.sp, PlaceholderVerticalAlign.Center)) {
                        AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                } else append(mr.value)
                last = mr.range.last + 1
            }
            append(m.text.substring(last))
        }
    }
    val bg = if (m.donation > 0) Modifier.background(Color(0x22FFC857), RoundedCornerShape(6.dp)) else Modifier
    Text(text, inlineContent = inline, color = C.Text, fontSize = (15 * fontScale).sp, lineHeight = (21 * fontScale).sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).then(bg).padding(horizontal = 2.dp))
}

@Composable
private fun ChatInput(loggedIn: Boolean, canSend: Boolean, chatActive: Boolean, pickerHeight: androidx.compose.ui.unit.Dp = 260.dp,
                      onCheese: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var showEmoji by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val packs by Session.emojiPacks.collectAsState()
    val emojiMap = remember(packs) { Session.usableEmojis() }
    // 이모티콘 창이 열려 있으면 뒤로가기로 먼저 닫음
    BackHandler(enabled = showEmoji) { showEmoji = false }

    Column(Modifier.fillMaxWidth().background(C.Bg)) {
        if (loggedIn && chatActive) EmojiPreview(text, emojiMap)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                !loggedIn -> Text("로그인하고 채팅에 참여하기", color = C.Sub, fontSize = 15.sp,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(C.Surface2)
                        .clickable { Nav.push(Route.Login) }.padding(horizontal = 18.dp, vertical = 13.dp))
                !chatActive -> Text("채팅이 비활성화된 방송입니다", color = C.Dim, fontSize = 15.sp,
                    modifier = Modifier.weight(1f).padding(12.dp))
                else -> {
                    val send: () -> Unit = {
                        if (text.isNotBlank() && Session.chat.send(text.trim(), emojiMap)) { text = ""; showEmoji = false }
                    }
                    TextField(text, { text = it.take(100) }, enabled = canSend, singleLine = true,
                        placeholder = { Text(if (canSend) "채팅을 입력해 주세요." else "채팅 연결 중…") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            // 공식 앱처럼 입력칸 안쪽 오른쪽에 이모티콘 버튼
                            IconButton({
                                if (showEmoji) { showEmoji = false } else { keyboard?.hide(); focus.clearFocus(); showEmoji = true }
                            }, enabled = canSend) {
                                Icon(Icons.Filled.EmojiEmotions, "이모티콘", tint = if (showEmoji) C.Accent else C.Sub)
                            }
                        },
                        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent, focusedContainerColor = C.Surface2,
                            unfocusedContainerColor = C.Surface2, disabledContainerColor = C.Surface2, cursorColor = C.Accent),
                        modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) showEmoji = false })
                    if (text.isNotBlank()) IconButton(send, enabled = canSend) {
                        Icon(Icons.AutoMirrored.Filled.Send, "전송", tint = C.Accent)
                    } else IconButton(onCheese) {   // 입력이 비어 있으면 치즈 후원 버튼
                        Text("🧀", fontSize = 24.sp)
                    }
                }
            }
        }
        if (showEmoji && loggedIn && chatActive) {
            EmojiPicker(pickerHeight) { token -> if (text.length + token.length <= 100) text += token }
        }
    }
}

/** 가로 전체화면 오른쪽 채팅 패널 (화면 너비의 약 1/3) */
@Composable
private fun LandscapeChatPanel(cur: NowPlaying.Live, onClose: () -> Unit) {
    val canSend by Session.chat.canSend.collectAsState()
    val user by Auth.user.collectAsState()
    var showCheese by remember { mutableStateOf(false) }
    if (showCheese) CheeseSheet { showCheese = false }
    val width = (LocalConfiguration.current.screenWidthDp * 0.34f).dp.coerceIn(260.dp, 420.dp)
    Column(Modifier.width(width).fillMaxHeight().background(Color(0xF0141517)).blockTouches()
        .navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("채팅", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            cur.detail?.let { Text("${fmtCount(it.concurrentUserCount)}명", color = C.Sub, fontSize = 12.sp) }
            IconButton(onClose) { Icon(Icons.Filled.Close, "채팅 닫기", tint = C.Sub, modifier = Modifier.size(20.dp)) }
        }
        HorizontalDivider(color = C.Surface2)
        ChatMessages(Modifier.weight(1f).fillMaxWidth(), fontScale = 0.9f)
        ChatInput(loggedIn = user?.loggedIn == true, canSend = canSend, chatActive = cur.detail?.chatActive != false, pickerHeight = 170.dp) { showCheese = true }
    }
}

@Composable
private fun VodInfo(cur: NowPlaying.Vod) {
    val info = cur.info
    Column(Modifier.padding(16.dp)) {
        Text(info?.contentTitle ?: "", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (info != null) Text("조회수 ${fmtCount(info.readCount)}회", color = C.Sub, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        info?.ownerChannel?.let { ch ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clickable { Nav.openChannel(ch.channelId) }) { ChannelLine(ch.channelName, ch.channelImageUrl, ch.verifiedMark, 36.dp) }
                Text("채널 라이브", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(C.Accent)
                        .clickable { Nav.openLive(ch.channelId) }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
}

/** 스크린샷의 "더보기" 시트와 같은 구성 + 지연 모드/자동 PIP */
@Composable
private fun PlayerSettings(isLive: Boolean, onClose: () -> Unit) {
    val quality by PlayerHolder.quality.collectAsState()
    val radio by PlayerHolder.radio.collectAsState()
    val latency by Prefs.latency.collectAsState()
    val autoPip by Prefs.autoPip.collectAsState()
    val cleanBot by Prefs.cleanBot.collectAsState()
    var page by remember { mutableIntStateOf(0) }   // 0 메인, 1 해상도, 2 지연 모드

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
        when (page) {
            1 -> {
                SheetRow("자동", if (quality == "자동") "✓" else null) { PlayerHolder.setQuality(null); onClose() }
                PlayerHolder.qualityOptions().forEach { q ->
                    SheetRow(q.label, if (quality == q.label) "✓" else null) { PlayerHolder.setQuality(q); onClose() }
                }
            }
            2 -> LatencyMode.entries.forEach { m ->
                SheetRow("${m.label}  ·  ${m.desc}", if (m == latency) "✓" else null) { Prefs.setLatency(m); onClose() }
            }
            else -> {
                SheetRow("해상도", if (radio) "라디오 모드" else quality) { if (!radio) page = 1 }
                if (isLive) SheetRow("지연 모드", latency.label) { page = 2 }
                ToggleRow("라디오 모드 (음성만 듣기)", radio) { PlayerHolder.setRadio(it) }
                ToggleRow("홈으로 나가면 자동 PIP", autoPip, Prefs::setAutoPip)
                if (isLive) ToggleRow("클린봇", cleanBot, Prefs::setCleanBot)
                if (isLive) SheetRow("실시간으로 이동", null) { PlayerHolder.jumpToLive(); onClose() }
                SheetRow("닫기", null, onClose)
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, value: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = if (value == "✓") C.Accent else C.Sub, fontSize = 15.sp)
    }
}

/** 다른 화면을 볼 때 우측 하단에 떠 있는 미니 플레이어 */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val st = rememberPlayerUiState()
    val radio by PlayerHolder.radio.collectAsState()
    Box(modifier.width(210.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black)
        .clickable { Nav.push(Route.Player) }) {
        PlayerSurface(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color(0x33000000)))
        Row(Modifier.align(Alignment.TopEnd)) {
            IconBtn(Icons.Filled.OpenInFull, "크게", size = 20.dp) { Nav.push(Route.Player) }
            IconBtn(Icons.Filled.Close, "닫기", size = 22.dp) { Session.close() }
        }
        Row(Modifier.align(Alignment.BottomCenter), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Filled.Headphones, "라디오", tint = if (radio) C.Accent else Color.White, size = 22.dp) {
                PlayerHolder.setRadio(!radio)
            }
            IconBtn(if (st.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "재생", size = 26.dp) {
                val p = PlayerHolder.player
                if (p.isPlaying) p.pause() else { p.play(); PlayerHolder.jumpToLive() }
            }
        }
    }
}
