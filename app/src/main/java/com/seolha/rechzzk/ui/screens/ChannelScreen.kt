package com.seolha.rechzzk.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.seolha.rechzzk.Auth
import com.seolha.rechzzk.net.Api
import com.seolha.rechzzk.net.Channel
import com.seolha.rechzzk.net.ChannelData
import com.seolha.rechzzk.net.Clip
import com.seolha.rechzzk.net.CommunityPost
import com.seolha.rechzzk.net.FollowState
import com.seolha.rechzzk.net.PowerRank
import com.seolha.rechzzk.net.Video
import com.seolha.rechzzk.net.thumb
import com.seolha.rechzzk.ui.C
import com.seolha.rechzzk.ui.Nav
import com.seolha.rechzzk.ui.Paged
import com.seolha.rechzzk.ui.Route
import com.seolha.rechzzk.ui.components.Avatar
import com.seolha.rechzzk.ui.components.Chip
import com.seolha.rechzzk.ui.components.Loading
import com.seolha.rechzzk.ui.components.Message
import com.seolha.rechzzk.ui.components.SortRow
import com.seolha.rechzzk.ui.components.TagRow
import com.seolha.rechzzk.ui.components.Thumb
import com.seolha.rechzzk.ui.components.ViewerBadge
import com.seolha.rechzzk.ui.components.fmtCount
import com.seolha.rechzzk.ui.components.fmtDuration
import com.seolha.rechzzk.ui.components.fmtShort
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private val channelTabs = listOf("홈", "동영상", "클립", "커뮤니티", "정보")

/** 채널 프로필: 공식 앱과 같은 홈·동영상·클립·커뮤니티·정보 탭 */
@Composable
fun ChannelScreen(route: Route.Channel) {
    val id = route.channelId
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val user by Auth.user.collectAsState()
    var channel by remember(id) { mutableStateOf<Channel?>(null) }
    var data by remember(id) { mutableStateOf<ChannelData?>(null) }
    var follow by remember(id) { mutableStateOf<FollowState?>(null) }
    var error by remember(id) { mutableStateOf(false) }
    var tab by remember(id) { mutableIntStateOf(0) }

    LaunchedEffect(id) {
        runCatching { Api.channel(id) }.onSuccess { channel = it }.onFailure { error = true }
        data = runCatching { Api.channelData(id) }.getOrNull()
    }
    LaunchedEffect(id, user) {
        follow = if (user?.loggedIn == true) runCatching { Api.followState(id) }.getOrNull() else null
    }

    // 탭별 목록(채널을 열 때 한 번씩만 만들고 재사용)
    var videoType by remember(id) { mutableIntStateOf(0) }
    var videoSort by remember(id) { mutableIntStateOf(0) }
    val videos = remember(id, videoType, videoSort) {
        Paged<Video>(scope, { it.videoNo }) { next ->
            val page = (next?.get("page") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
            val r = Api.channelVideos(id, if (videoSort == 0) "LATEST" else "POPULAR", listOf("", "REPLAY", "UPLOAD")[videoType], page)
            r.data to (if (page + 1 < r.totalPages) buildJsonObject { put("page", JsonPrimitive(page + 1)) } else null)
        }
    }
    var clipFilter by remember(id) { mutableIntStateOf(0) }
    var clipSort by remember(id) { mutableIntStateOf(0) }
    val clips = remember(id, clipFilter, clipSort) {
        Paged<Clip>(scope, { it.clipUID }) { next ->
            val r = Api.channelClips(id, listOf("ALL", "WITHIN_ONE_DAY", "WITHIN_SEVEN_DAYS", "WITHIN_THIRTY_DAYS")[clipFilter],
                if (clipSort == 0) "POPULAR" else "RECENT", next)
            r.data to r.page?.next
        }
    }
    val posts = remember(id) {
        Paged<CommunityPost>(scope, { it.comment.commentId }) { next ->
            val page = (next?.get("page") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
            val r = Api.communityPosts(id, page).comments
            val list = r?.data.orEmpty()
            list to (if ((page + 1) * 10 < (r?.totalCount ?: 0)) buildJsonObject { put("page", JsonPrimitive(page + 1)) } else null)
        }
    }
    var rank by remember(id) { mutableStateOf<PowerRank?>(null) }
    LaunchedEffect(tab, videos) { if (tab == 1 && videos.items.isEmpty()) videos.refresh() }
    LaunchedEffect(tab, clips) { if (tab == 2 && clips.items.isEmpty()) clips.refresh() }
    LaunchedEffect(tab) {
        if (tab == 0 && videos.items.isEmpty()) videos.refresh()
        if (tab == 3 && posts.items.isEmpty()) posts.refresh()
        if (tab == 4 && rank == null) rank = runCatching { Api.powerRankMonthly(id) }.getOrNull()
    }

    val grid = rememberLazyGridState()
    val nearEnd by remember { derivedStateOf {
        (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= grid.layoutInfo.totalItemsCount - 4
    } }
    LaunchedEffect(nearEnd, tab) {
        if (!nearEnd) return@LaunchedEffect
        when (tab) { 1 -> videos.loadMore(); 2 -> clips.loadMore(); 3 -> posts.loadMore() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = C.Text) }
        }
        if (error) { Message("채널 정보를 불러오지 못했습니다"); return@Column }
        val ch = channel ?: run { Loading(); return@Column }

        LazyVerticalGrid(GridCells.Fixed(2), state = grid, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            full { Header(ch, data, follow, user?.loggedIn == true,
                onFollow = { on ->
                    scope.launch {
                        runCatching { Api.setFollow(id, on) }
                            .onSuccess { follow = runCatching { Api.followState(id) }.getOrNull() ?: FollowState(on, on) }
                            .onFailure { Toast.makeText(ctx, it.message, Toast.LENGTH_SHORT).show() }
                    }
                },
                onAlert = { on ->
                    scope.launch {
                        runCatching { Api.setChannelAlert(id, on) }
                            .onSuccess {
                                follow = follow?.copy(notification = on)
                                Toast.makeText(ctx, if (on) "방송 알림을 켰습니다" else "방송 알림을 껐습니다", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { Toast.makeText(ctx, it.message, Toast.LENGTH_SHORT).show() }
                    }
                }) }
            full {
                Column {
                    LazyRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        itemsIndexed(channelTabs) { i, t ->
                            Column(Modifier.clickable { tab = i }.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(t, color = if (i == tab) C.Text else C.Dim, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.width(36.dp).height(3.dp).background(if (i == tab) C.Text else Color.Transparent))
                            }
                        }
                    }
                }
            }
            when (tab) {
                0 -> homeTab(ch, data, videos)
                1 -> {
                    full {
                        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(listOf("전체", "지난 방송", "업로드 영상")) { i, t -> Chip(t, i == videoType) { videoType = i } }
                            }
                            SortRow(listOf("최신순", "인기순"), videoSort, { videoSort = it })
                        }
                    }
                    items(videos.items, key = { "v" + it.videoNo }, span = { GridItemSpan(2) }) { VideoRow(it) }
                    pagedFooter(videos.loading, videos.error, videos.items.isEmpty() && videos.end, "동영상이 없습니다") { videos.loadMore() }
                }
                2 -> {
                    full {
                        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(listOf("전체", "24시간", "7일", "30일")) { i, t -> Chip(t, i == clipFilter) { clipFilter = i } }
                            }
                            SortRow(listOf("인기순", "최신순"), clipSort, { clipSort = it })
                        }
                    }
                    items(clips.items, key = { "c" + it.clipUID }) { ClipCard(it) }
                    pagedFooter(clips.loading, clips.error, clips.items.isEmpty() && clips.end, "클립이 없습니다") { clips.loadMore() }
                }
                3 -> {
                    items(posts.items, key = { "p" + it.comment.commentId }, span = { GridItemSpan(2) }) { PostCard(it) }
                    pagedFooter(posts.loading, posts.error, posts.items.isEmpty() && posts.end, "커뮤니티 글이 없습니다") { posts.loadMore() }
                }
                else -> infoTab(ch, data, rank)
            }
        }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) = item(span = { GridItemSpan(2) }) { content() }

private fun LazyGridScope.pagedFooter(loading: Boolean, error: String?, empty: Boolean, emptyText: String, retry: () -> Unit) = full {
    when {
        loading -> Loading()
        error != null -> Message(error, "다시 시도", retry)
        empty -> Message(emptyText)
    }
}

@Composable
private fun Header(ch: Channel, data: ChannelData?, follow: FollowState?, loggedIn: Boolean,
                   onFollow: (Boolean) -> Unit, onAlert: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(ch.channelImageUrl, 96.dp, liveRing = ch.openLive)
            Spacer(Modifier.width(18.dp))
            Column {
                Text(ch.channelName, color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text("팔로워 ${fmtCount(ch.followerCount)}명", color = C.Sub, fontSize = 15.sp)
                val desc = data?.description ?: ch.channelDescription
                if (!desc.isNullOrBlank()) Text(desc, color = C.Dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val following = follow?.following == true
            val alertOn = follow?.notification == true
            HeaderButton(Modifier.weight(1f), if (following) C.Accent else C.Surface2) {
                if (!loggedIn) Nav.push(Route.Login) else onFollow(!following)
            }
            Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(10.dp)).background(C.Surface2)
                .clickable { if (!loggedIn) Nav.push(Route.Login) else if (following) onAlert(!alertOn)
                    else Toast.makeText(ctx, "팔로우한 채널만 알림을 켤 수 있습니다", Toast.LENGTH_SHORT).show() },
                contentAlignment = Alignment.Center) {
                Icon(if (alertOn) Icons.Filled.Notifications else Icons.Filled.NotificationsOff, "방송 알림",
                    tint = if (alertOn) C.Accent else C.Text)
            }
            // 구독은 결제가 필요해 공식 웹에서 진행
            Box(Modifier.weight(1.8f).height(46.dp).clip(RoundedCornerShape(10.dp)).background(C.Text)
                .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chzzk.naver.com/${ch.channelId}"))) },
                contentAlignment = Alignment.Center) {
                Text("⊕ 구독", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeaderButton(modifier: Modifier, bg: Color, onClick: () -> Unit) {
    val following = bg == C.Accent
    Box(modifier.height(46.dp).clip(RoundedCornerShape(10.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(if (following) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (following) "팔로잉" else "팔로우",
            tint = if (following) Color.Black else C.Text)
    }
}

private fun LazyGridScope.homeTab(ch: Channel, data: ChannelData?, videos: Paged<Video>) {
    val top = data?.topExposedVideos
    val live = top?.openLive
    if (ch.openLive || live != null) full {
        Column(Modifier.padding(top = 12.dp).clickable { Nav.openLive(ch.channelId) }) {
            Thumb(thumb(live?.liveImageUrl, live?.defaultThumbnailImageUrl), corner = 12.dp) {
                ViewerBadge(live?.concurrentUserCount ?: 0, Modifier.padding(10.dp), showLiveLabel = true)
            }
            Spacer(Modifier.height(10.dp))
            Text(live?.liveTitle ?: "방송 중", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            TagRow(live?.tags ?: emptyList(), live?.liveCategoryValue)
        }
    } else {
        val v = top?.fixedVideo ?: top?.latestVideo
        if (v != null) full {
            Column(Modifier.padding(top = 12.dp).clickable { Nav.openVideo(v.videoNo) }) {
                VideoThumb(v, corner = 12.dp)
                Spacer(Modifier.height(10.dp))
                Text(v.videoTitle, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text("▷ ${fmtShort(v.readCount)} · ${shortDate(v.publishDate)}", color = C.Sub, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                TagRow(v.tags, v.videoCategoryValue)
            }
        }
    }
    val links = data?.socialLinks.orEmpty()
    if (links.isNotEmpty()) full {
        LazyRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(links) { _, l -> LinkChip(l.title, l.landingUrl) }
        }
    }
    full { Text("동영상", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp)) }
    items(videos.items.take(6), key = { "hv" + it.videoNo }, span = { GridItemSpan(2) }) { VideoRow(it) }
    if (videos.loading) full { Loading() } else if (videos.items.isEmpty() && videos.end) full { Message("동영상이 없습니다") }
}

private fun LazyGridScope.infoTab(ch: Channel, data: ChannelData?, rank: PowerRank?) {
    full {
        InfoCard("채널 소개") {
            Text(data?.description ?: ch.channelDescription ?: "소개가 없습니다", color = C.Text, fontSize = 16.sp)
        }
    }
    val links = data?.socialLinks.orEmpty()
    if (links.isNotEmpty()) full {
        InfoCard("정보") {
            val ctx = LocalContext.current
            links.forEach { l ->
                Row(Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.landingUrl))) }
                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(C.Dim), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Link, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(l.title, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (data?.logPowerActive == true && data.logPowerRankingExposure) full {
        InfoCard("월간 통나무 파워 랭킹") {
            val r = rank
            if (r == null) { Loading(); return@InfoCard }
            r.updatedDate?.let { Text("마지막 업데이트: ${it.take(13).replace('-', '.')}시", color = C.Sub, fontSize = 13.sp) }
            if (r.rankList.isEmpty()) Text("아직 랭킹이 없습니다", color = C.Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            r.rankList.take(30).forEach { u ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(when (u.ranking) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "${u.ranking}" },
                        color = C.Sub, fontSize = 18.sp, modifier = Modifier.width(34.dp))
                    Avatar(u.profileImageUrl, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(u.nickname, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text("🪵 ${fmtCount(u.amount)}", color = C.Sub, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(C.Surface).padding(18.dp)) {
        Text(title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun LinkChip(title: String, url: String) {
    val ctx = LocalContext.current
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(C.Surface2)
        .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Link, null, tint = C.Sub, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, color = C.Text, fontSize = 14.sp, maxLines = 1)
    }
}

private fun shortDate(d: String?) = d?.take(10)?.drop(5)?.replace('-', '.') ?: ""

@Composable
private fun VideoThumb(v: Video, corner: androidx.compose.ui.unit.Dp = 10.dp) {
    Thumb(v.thumbnailImageUrl, corner = corner) {
        Box(Modifier.fillMaxSize().padding(6.dp)) {
            if (v.videoType == "REPLAY") Text("다시보기", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color(0xFF5B3CF5), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
            Text(fmtDuration(v.duration), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp))
        }
    }
}

@Composable
private fun VideoRow(v: Video) {
    Row(Modifier.fillMaxWidth().clickable { Nav.openVideo(v.videoNo) }.padding(vertical = 8.dp)) {
        Box(Modifier.weight(0.42f)) { VideoThumb(v) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(0.58f)) {
            Text(v.videoTitle, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Text("▷ ${fmtShort(v.readCount)} · ${shortDate(v.publishDate)}", color = C.Sub, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            TagRow(v.tags.take(2), v.videoCategoryValue)
        }
    }
}

@Composable
private fun ClipCard(c: Clip) {
    Box(Modifier.padding(vertical = 5.dp).clickable { Nav.openClip(c.clipUID) }) {
        Thumb(c.thumbnailImageUrl, ratio = 9f / 16f, corner = 12.dp) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))).padding(10.dp)) {
                Text(c.clipTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text("▷ ${fmtShort(c.readCount)}", color = Color(0xCCFFFFFF), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PostCard(p: CommunityPost) {
    var expanded by remember(p.comment.commentId) { mutableStateOf(false) }
    val content = p.comment.content
    val long = content.length > 140 || content.count { it == '\n' } > 5
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.user.profileImageUrl, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(p.user.userNickname, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                // createdDate 형식: yyyyMMddHHmmss
                val d = p.comment.createdDate
                if (d != null && d.length >= 8) Text("${d.substring(4, 6)}.${d.substring(6, 8)}", color = C.Sub, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(content.replace("**", ""), color = C.Text, fontSize = 15.sp, lineHeight = 22.sp,
            maxLines = if (expanded || !long) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
        if (long && !expanded) Text("… 더보기", color = C.Sub, fontSize = 14.sp,
            modifier = Modifier.clickable { expanded = true }.padding(vertical = 6.dp))
        p.comment.attaches.orEmpty().filter { it.attachType == "PHOTO" || it.attachType == "STICKER" }.forEach { a ->
            Spacer(Modifier.height(8.dp))
            AsyncImage(a.attachValue, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.FillWidth)
        }
        Spacer(Modifier.height(8.dp))
        Text("⇧ ${p.buffNerf?.buffCount ?: 0}    💬 ${maxOf(p.comment.childObjectCount, p.comment.replyCount)}",
            color = C.Sub, fontSize = 14.sp)
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(C.Surface2))
    }
}
