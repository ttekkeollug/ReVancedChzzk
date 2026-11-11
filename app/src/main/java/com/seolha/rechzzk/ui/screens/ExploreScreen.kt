package com.seolha.rechzzk.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seolha.rechzzk.net.Api
import com.seolha.rechzzk.net.Category
import com.seolha.rechzzk.net.LiveItem
import com.seolha.rechzzk.net.thumb
import com.seolha.rechzzk.ui.C
import com.seolha.rechzzk.ui.Nav
import com.seolha.rechzzk.ui.Paged
import com.seolha.rechzzk.ui.Route
import com.seolha.rechzzk.ui.components.Chip
import com.seolha.rechzzk.ui.components.LiveBigCard
import com.seolha.rechzzk.ui.components.LiveRowCard
import com.seolha.rechzzk.ui.components.Loading
import com.seolha.rechzzk.ui.components.Message
import com.seolha.rechzzk.ui.components.SortRow
import com.seolha.rechzzk.ui.components.Thumb
import com.seolha.rechzzk.ui.components.VideoBigCard
import com.seolha.rechzzk.ui.components.fmtCount

@Composable
fun ExploreScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar()
        TopTabs(listOf("라이브", "동영상", "카테고리"), tab) { tab = it }
        when (tab) {
            0 -> ExploreLives()
            1 -> ExploreVideos()
            else -> ExploreCategories()
        }
    }
}

/** LazyColumn 끝 근처에 오면 다음 페이지 로드 */
@Composable
private fun <T> PagedList(paged: Paged<T>, header: @Composable () -> Unit = {}, row: @Composable (T) -> Unit) {
    val state = rememberLazyListState()
    val nearEnd by remember { derivedStateOf {
        (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 4
    } }
    LaunchedEffect(nearEnd) { if (nearEnd) paged.loadMore() }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item { header() }
        itemsIndexed(paged.items) { _, it -> row(it) }
        item {
            when {
                paged.loading -> Loading()
                paged.error != null -> Message(paged.error!!, "다시 시도") { paged.loadMore() }
                paged.items.isEmpty() && paged.end -> Message("결과가 없습니다")
            }
        }
    }
}

@Composable
private fun ExploreLives() {
    val scope = rememberCoroutineScope()
    var sort by rememberSaveable { mutableIntStateOf(0) }
    val paged = remember(sort) {
        Paged<LiveItem>(scope, { it.liveId }) { next ->
            val p = Api.lives(if (sort == 0) "POPULAR" else "LATEST", next)
            p.data to p.page?.next
        }
    }
    LaunchedEffect(paged) { paged.refresh() }
    PagedList(paged, header = {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            SortRow(listOf("인기순", "최신순"), sort, { sort = it })
        }
    }) { l -> LiveBigCard(l) { l.channel?.channelId?.let(Nav::openLive) } }
}

@Composable
private fun ExploreVideos() {
    val scope = rememberCoroutineScope()
    val paged = remember { Paged(scope, { it.videoNo }) { next -> Api.homeVideos(next).let { it.data to it.page?.next } } }
    LaunchedEffect(Unit) { if (paged.items.isEmpty()) paged.refresh() }
    PagedList(paged) { v -> VideoBigCard(v) { Nav.openVideo(v.videoNo) } }
}

private val catChips = listOf("전체" to null, "게임" to "GAME", "스포츠" to "SPORTS", "엔터" to "ENTERTAINMENT", "기타" to "ETC")

@Composable
private fun ExploreCategories() {
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val paged = remember { Paged(scope, { it.categoryType + it.categoryId }) { next -> Api.categories(next).let { it.data to it.page?.next } } }
    LaunchedEffect(Unit) { if (paged.items.isEmpty()) paged.refresh() }
    val type = catChips[filter].second
    val list = paged.items.filter { type == null || it.categoryType == type }
    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(3) }) {
            LazyRow(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(catChips) { i, c -> Chip(c.first, i == filter) { filter = i } }
            }
        }
        items(list, key = { it.categoryType + it.categoryId }) { CategoryCard(it) }
        item(span = { GridItemSpan(3) }) {
            if (paged.loading) Loading()
            else if (!paged.end) Message("", "더 보기") { paged.loadMore() }
        }
    }
}

@Composable
private fun CategoryCard(c: Category) {
    Column(Modifier.clickable { Nav.push(Route.Category(c.categoryType, c.categoryId, c.categoryValue)) }) {
        Thumb(c.posterImageUrl, ratio = 3f / 4f) {
            Row(Modifier.padding(6.dp).background(androidx.compose.ui.graphics.Color(0xCC000000), CircleShape)
                .padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(C.Live, CircleShape))
                Text(" ${fmtCount(c.concurrentUserCount)}명", color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(c.categoryValue, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        Text("라이브 ${c.openLiveCount}개", color = C.Sub, fontSize = 12.sp)
    }
}

@Composable
fun CategoryScreen(route: Route.Category) {
    var lives by remember(route) { mutableStateOf<List<LiveItem>?>(null) }
    var error by remember(route) { mutableStateOf(false) }
    LaunchedEffect(route) {
        runCatching { Api.categoryLives(route.type, route.id).liveInfoResponseList }
            .onSuccess { lives = it }.onFailure { error = true }
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = C.Text) }
            Text(route.name, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        val list = lives
        when {
            error -> Message("불러오지 못했습니다")
            list == null -> Loading()
            list.isEmpty() -> Message("방송 중인 채널이 없습니다")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list, key = { it.liveId }) { l ->
                    LiveRowCard(l.liveTitle, thumb(l.liveImageUrl, l.defaultThumbnailImageUrl, 480), l.concurrentUserCount,
                        l.channel?.channelName ?: "", l.channel?.channelImageUrl, l.channel?.verifiedMark == true,
                        null, l.tags) { l.channel?.channelId?.let(Nav::openLive) }
                }
            }
        }
    }
}
