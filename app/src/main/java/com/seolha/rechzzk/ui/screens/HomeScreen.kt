package com.seolha.rechzzk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.seolha.rechzzk.Auth
import com.seolha.rechzzk.net.Api
import com.seolha.rechzzk.net.LiveItem
import com.seolha.rechzzk.net.thumb
import com.seolha.rechzzk.ui.C
import com.seolha.rechzzk.ui.Nav
import com.seolha.rechzzk.ui.Paged
import com.seolha.rechzzk.ui.Route
import com.seolha.rechzzk.ui.components.Avatar
import com.seolha.rechzzk.ui.components.ChannelLine
import com.seolha.rechzzk.ui.components.LiveGridCard
import com.seolha.rechzzk.ui.components.Loading
import com.seolha.rechzzk.ui.components.Message
import com.seolha.rechzzk.ui.components.ViewerBadge

@Composable
fun AppTopBar() {
    val user by Auth.user.collectAsState()
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("ReChzzk", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
        Spacer(Modifier.weight(1f))
        IconButton({ Nav.push(Route.Search) }) { Icon(Icons.Filled.Search, "검색", tint = C.Text, modifier = Modifier.size(28.dp)) }
        IconButton({ Nav.push(Route.Login) }) {
            if (user?.profileImageUrl != null) Avatar(user?.profileImageUrl, 34.dp)
            else Icon(Icons.Filled.Person, "로그인", tint = C.Text, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun TopTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            tabs.forEachIndexed { i, t ->
                Column(Modifier.clickable { onSelect(i) }.padding(horizontal = 6.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (i == selected) C.Accent else C.Dim)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(44.dp).height(3.dp).background(if (i == selected) C.Accent else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = C.Surface2)
    }
}

private val homeTabs = listOf("추천", "게임", "스포츠", "엔터", "기타")
private val tabType = listOf(null, "GAME", "SPORTS", "ENTERTAINMENT", "ETC")

@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var hero by remember { mutableStateOf<LiveItem?>(null) }
    val paged = remember {
        Paged(scope, { it.liveId }) { next ->
            val p = Api.lives("POPULAR", next, 30)
            p.data to p.page?.next
        }
    }
    LaunchedEffect(Unit) {
        if (paged.items.isEmpty()) paged.refresh()
        hero = runCatching { Api.recommended().topRecommendedLives.firstOrNull() }.getOrNull()
    }
    val type = tabType[tab]
    val list = if (type == null) paged.items.toList() else paged.items.filter { it.categoryType == type }
    val grid = rememberLazyGridState()
    val nearEnd by remember { derivedStateOf {
        val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        last >= grid.layoutInfo.totalItemsCount - 6
    } }
    LaunchedEffect(nearEnd, tab) { if (nearEnd) paged.loadMore() }

    Column(Modifier.fillMaxSize()) {
        AppTopBar()
        TopTabs(homeTabs, tab) { tab = it }
        LazyVerticalGrid(GridCells.Fixed(2), state = grid, modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            val heroItem = if (tab == 0) hero ?: list.firstOrNull() else list.firstOrNull()
            if (heroItem != null) item(span = { GridItemSpan(2) }) { HeroCard(heroItem) }
            itemsIndexed(list.filter { it.liveId != heroItem?.liveId }, key = { _, l -> l.liveId }) { _, l ->
                LiveGridCard(l) { l.channel?.channelId?.let(Nav::openLive) }
            }
            item(span = { GridItemSpan(2) }) {
                when {
                    paged.loading -> Loading()
                    paged.error != null -> Message(paged.error!!, "다시 시도") { paged.loadMore() }
                    list.isEmpty() && paged.end -> Message("방송이 없습니다")
                    !paged.end && type != null && list.size < 6 -> LaunchedEffect(list.size) { paged.loadMore() }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(live: LiveItem) {
    Box(Modifier.padding(top = 12.dp, bottom = 16.dp).fillMaxWidth().aspectRatio(1.15f)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
        .clickable { live.channel?.channelId?.let(Nav::openLive) }) {
        AsyncImage(thumb(live.liveImageUrl, live.defaultThumbnailImageUrl), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, C.Bg))))
        ViewerBadge(live.concurrentUserCount, Modifier.padding(16.dp), showLiveLabel = true)
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f, fill = false)) {
                    live.channel?.let { ChannelLine(it.channelName, it.channelImageUrl, it.verifiedMark, 30.dp) }
                }
                live.liveCategoryValue?.let {
                    Text("  ·  $it", color = C.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(live.liveTitle, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
