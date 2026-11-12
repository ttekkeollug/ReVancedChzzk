package com.seolha.rechzzk.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.seolha.rechzzk.net.Api
import com.seolha.rechzzk.net.Clip
import com.seolha.rechzzk.player.PlayerHolder
import com.seolha.rechzzk.ui.C
import com.seolha.rechzzk.ui.Nav
import com.seolha.rechzzk.ui.Paged
import com.seolha.rechzzk.ui.components.Avatar
import com.seolha.rechzzk.ui.components.Chip
import com.seolha.rechzzk.ui.components.Loading
import com.seolha.rechzzk.ui.components.Message
import com.seolha.rechzzk.ui.components.Thumb
import com.seolha.rechzzk.ui.components.fmtShort

@Composable
fun ClipsScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        if (tab == 0) ClipFeed() else PopularClips()
        Row(Modifier.statusBarsPadding().padding(start = 16.dp, top = 12.dp)) {
            listOf("추천", "인기").forEachIndexed { i, t ->
                Column(Modifier.padding(end = 18.dp).clickable { tab = i }) {
                    Text(t, color = if (i == tab) Color.White else Color(0x99FFFFFF), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(40.dp).height(3.dp).background(if (i == tab) Color.White else Color.Transparent))
                }
            }
        }
    }
}

/** 쇼츠처럼 세로로 넘기는 추천 클립. 미니 플레이어와 소리가 겹치지 않도록 전용 플레이어를 쓰고 메인 재생은 일시정지 */
@OptIn(UnstableApi::class)
@Composable
private fun ClipFeed() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val paged = remember { Paged(scope, { it.clipUID }) { next -> Api.clips(next = next).let { it.data to it.page?.next } } }
    LaunchedEffect(Unit) { if (paged.items.isEmpty()) paged.refresh() }
    val feedPlayer = remember {
        ExoPlayer.Builder(ctx).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    // 클립이 재생되는 동안 화면이 꺼지지 않게 (클립 피드는 별도 플레이어라 따로 처리)
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { view.keepScreenOn = isPlaying }
        }
        feedPlayer.addListener(l)
        onDispose {
            feedPlayer.removeListener(l)
            view.keepScreenOn = false
            feedPlayer.release()
        }
    }
    // 앱이 백그라운드로 가면 클립 피드 정지 (메인 플레이어와 달리 서비스에 묶여 있지 않음)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) feedPlayer.pause()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    // 다른 화면(플레이어/검색 등)이 위에 열리면 정지. 돌아와도 미니 플레이어와 소리가 겹치지 않게 자동 재개하지 않음(탭하면 재생)
    val covered = Nav.top != null
    LaunchedEffect(covered) { if (covered) feedPlayer.pause() }

    if (paged.items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (paged.error != null) Message(paged.error!!, "다시 시도") { paged.refresh() } else Loading()
        }
        return
    }
    val pager = rememberPagerState { paged.items.size }
    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage >= paged.items.size - 3) paged.loadMore()
        val clip = paged.items.getOrNull(pager.currentPage) ?: return@LaunchedEffect
        feedPlayer.stop()
        val info = runCatching { Api.clipPlayInfo(clip.clipUID) }.getOrNull() ?: return@LaunchedEffect
        val src = runCatching { Api.resolveVod(info) }.getOrNull() ?: return@LaunchedEffect
        val media = PlayerHolder.vodMediaSource(src)
        if (Nav.top != null) return@LaunchedEffect
        PlayerHolder.player.pause()
        feedPlayer.setMediaSource(media)
        feedPlayer.prepare()
        feedPlayer.play()
    }
    VerticalPager(pager, Modifier.fillMaxSize().background(Color.Black), key = { paged.items[it].clipUID }) { page ->
        val clip = paged.items[page]
        Box(Modifier.fillMaxSize()) {
            AsyncImage(clip.thumbnailImageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            if (page == pager.currentPage) {
                AndroidView(
                    factory = { PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT) } },
                    update = { it.player = feedPlayer },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize().clickable {
                        if (feedPlayer.isPlaying) feedPlayer.pause() else { PlayerHolder.player.pause(); feedPlayer.play() }
                    },
                )
            }
            ClipOverlay(clip, Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun ClipOverlay(clip: Clip, modifier: Modifier) {
    Column(modifier.fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
        .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 110.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { clip.ownerChannel?.channelId?.let(Nav::openChannel) }) {
            Avatar(clip.ownerChannel?.channelImageUrl, 44.dp)
            Spacer(Modifier.width(10.dp))
            Text(clip.ownerChannel?.channelName ?: "", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text(clip.clipTitle, color = Color.White, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton("(•) 라이브 보러가기") { clip.ownerChannel?.channelId?.let(Nav::openLive) }
            OutlineButton("▶ 크게 보기") { Nav.openClip(clip.clipUID) }
        }
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit) {
    Text(text, color = Color.White, fontSize = 14.sp, modifier = Modifier
        .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(20.dp))
        .background(Color(0x33000000), RoundedCornerShape(20.dp))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
private fun PopularClips() {
    val scope = rememberCoroutineScope()
    var weekly by rememberSaveable { mutableStateOf(false) }
    val paged = remember(weekly) {
        Paged(scope, { it.clipUID }) { next ->
            Api.clips("POPULAR", if (weekly) "WITHIN_7_DAYS" else "WITHIN_1_DAY", next).let { it.data to it.page?.next }
        }
    }
    LaunchedEffect(paged) { paged.refresh() }
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(span = { GridItemSpan(2) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Chip("실시간", !weekly) { weekly = false }
                Chip("주간", weekly) { weekly = true }
            }
        }
        items(paged.items, key = { it.clipUID }) { c ->
            Box(Modifier.clickable { Nav.openClip(c.clipUID) }) {
                Thumb(c.thumbnailImageUrl, ratio = 9f / 16f, corner = 12.dp) {
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))).padding(10.dp)) {
                        Text(c.clipTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text(c.ownerChannel?.channelName ?: "", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                        Text("▷ ${fmtShort(c.readCount)}", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            if (paged.loading) Loading() else if (!paged.end) LaunchedEffect(paged.items.size) { paged.loadMore() }
        }
    }
}
