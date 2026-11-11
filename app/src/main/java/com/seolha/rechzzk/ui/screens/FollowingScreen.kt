package com.seolha.rechzzk.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seolha.rechzzk.Auth
import com.seolha.rechzzk.net.Api
import com.seolha.rechzzk.net.FollowingItem
import com.seolha.rechzzk.net.thumb
import com.seolha.rechzzk.ui.C
import com.seolha.rechzzk.ui.Nav
import com.seolha.rechzzk.ui.Route
import com.seolha.rechzzk.ui.components.Avatar
import com.seolha.rechzzk.ui.components.LiveRowCard
import com.seolha.rechzzk.ui.components.Loading
import com.seolha.rechzzk.ui.components.Message
import com.seolha.rechzzk.ui.components.SectionTitle
import com.seolha.rechzzk.ui.components.SortRow

private val sortTypes = listOf("RECOMMEND", "POPULAR", "UNPOPULAR", "LATEST")

@Composable
fun FollowingScreen() {
    val user by Auth.user.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var lives by remember { mutableStateOf<List<FollowingItem>?>(null) }
    var channels by remember { mutableStateOf<List<FollowingItem>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(user, reload, sort) {
        if (user?.loggedIn != true) return@LaunchedEffect
        error = false
        runCatching { Api.followingLives(sortTypes[sort]).followingList }
            .onSuccess { lives = it }.onFailure { error = true }
        channels = runCatching { Api.followings().followingList }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize()) {
        TopTabs(listOf("전체", "라이브"), tab) { tab = it }
        if (user?.loggedIn != true) {
            Message("로그인하면 팔로우한 채널을 볼 수 있습니다", "네이버 로그인") { Nav.push(Route.Login) }
            return@Column
        }
        val list = lives
        if (list == null) { if (error) Message("불러오지 못했습니다", "다시 시도") { reload++ } else Loading(); return@Column }

        val sorted = list   // 서버에서 정렬해서 내려줌
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (tab == 0) {
                item { SectionTitle("라이브", list.size, if (list.size > 5) "전체보기" else null) { tab = 1 } }
                items(sorted.take(5), key = { "l" + it.channelId }) { LiveRow(it) }
                if (list.isEmpty()) item { Message("지금 방송 중인 채널이 없습니다") }
                item { SectionTitle("팔로잉 채널", channels.size) }
                item {
                    val ordered = channels.sortedByDescending { it.streamer?.openLive == true }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(ordered, key = { it.channelId }) { f ->
                            val live = f.streamer?.openLive == true
                            Column(Modifier.width(76.dp).clickable { if (live) Nav.openLive(f.channelId) else Nav.openChannel(f.channelId) },
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Avatar(f.channel?.channelImageUrl, 70.dp, liveRing = live)
                                Spacer(Modifier.height(6.dp))
                                Text(f.channel?.channelName ?: "", color = if (live) C.Text else C.Sub, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                        SortRow(listOf("추천순", "시청자순", "시청자역순", "최신순"), sort, { sort = it })
                    }
                }
                items(sorted, key = { "a" + it.channelId }) { LiveRow(it) }
                if (list.isEmpty()) item { Message("지금 방송 중인 채널이 없습니다") }
            }
        }
    }
}

@Composable
private fun LiveRow(f: FollowingItem) {
    val info = f.liveInfo
    LiveRowCard(
        title = info?.liveTitle ?: "",
        // 웹과 같은 우선순위: 스트리머가 지정한 기본 썸네일 → 실시간 스냅샷
        image = thumb(info?.defaultThumbnailImageUrl ?: info?.liveImageUrl, null, 480),
        viewers = info?.concurrentUserCount ?: 0,
        channelName = f.channel?.channelName ?: "",
        channelImage = f.channel?.channelImageUrl,
        verified = f.channel?.verifiedMark == true,
        category = info?.liveCategoryValue,
        tags = info?.tags ?: emptyList(),
    ) { Nav.openLive(f.channelId) }
}
