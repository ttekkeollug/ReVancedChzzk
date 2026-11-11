package com.seolha.rechzzk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seolha.rechzzk.net.LiveItem
import com.seolha.rechzzk.net.Video
import com.seolha.rechzzk.net.thumb
import com.seolha.rechzzk.ui.C

/** 홈 2열 그리드 카드 */
@Composable
fun LiveGridCard(live: LiveItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 14.dp)) {
        Thumb(thumb(live.liveImageUrl, live.defaultThumbnailImageUrl, 480)) {
            ViewerBadge(live.concurrentUserCount, Modifier.padding(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(live.liveTitle, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        live.channel?.let { ChannelLine(it.channelName, it.channelImageUrl, it.verifiedMark) }
        Spacer(Modifier.height(6.dp))
        TagRow(live.tags.take(2), live.liveCategoryValue)
    }
}

/** 팔로잉/카테고리 목록: 왼쪽 썸네일 + 오른쪽 정보 */
@Composable
fun LiveRowCard(
    title: String, image: String?, viewers: Int, channelName: String, channelImage: String?,
    verified: Boolean, category: String?, tags: List<String>, onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(Modifier.weight(0.42f)) {
            Thumb(image) { ViewerBadge(viewers, Modifier.padding(5.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(0.58f)) {
            Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
            Spacer(Modifier.height(6.dp))
            ChannelLine(channelName, channelImage, verified, 20.dp)
            Spacer(Modifier.height(6.dp))
            TagRow(tags.take(2), category)
        }
    }
}

/** 탐색 탭 큰 카드 */
@Composable
fun LiveBigCard(live: LiveItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Thumb(thumb(live.liveImageUrl, live.defaultThumbnailImageUrl)) {
            ViewerBadge(live.concurrentUserCount, Modifier.padding(8.dp), showLiveLabel = true)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Avatar(live.channel?.channelImageUrl, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(live.liveTitle, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(live.channel?.channelName ?: "", color = C.Sub, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                TagRow(live.tags.take(3), live.liveCategoryValue)
            }
        }
    }
}

@Composable
fun VideoBigCard(v: Video, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Thumb(v.thumbnailImageUrl) {
            Box(Modifier.fillMaxWidth().padding(8.dp)) {
                if (v.videoType == "REPLAY") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("다시보기", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color(0xFF5B3CF5), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp))
                        if (v.livePv > 0) {
                            Spacer(Modifier.width(4.dp))
                            Text("${fmtShort(v.livePv)}회 시청된 라이브", color = Color.White, fontSize = 12.sp,
                                modifier = Modifier.background(Color(0xCC000000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Avatar(v.channel?.channelImageUrl, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(v.videoTitle, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(v.channel?.channelName ?: "", color = C.Sub, fontSize = 14.sp)
                Text("▷ ${fmtShort(v.readCount)} · ${v.publishDate?.take(10)?.drop(5)?.replace('-', '.') ?: ""} · ${fmtDuration(v.duration)}",
                    color = C.Dim, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                TagRow(v.tags.take(3), v.videoCategoryValue)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, count: Int? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Row {
            Text(title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (count != null) { Spacer(Modifier.width(6.dp)); Text("$count", color = C.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
        if (action != null) Text(action, color = C.Sub, fontSize = 13.sp, modifier = Modifier.clickable { onAction?.invoke() })
    }
}
