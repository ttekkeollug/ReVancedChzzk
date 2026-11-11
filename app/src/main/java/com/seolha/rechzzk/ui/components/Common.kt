package com.seolha.rechzzk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.seolha.rechzzk.ui.C
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun fmtCount(n: Number): String = NumberFormat.getInstance(Locale.KOREA).format(n)

fun fmtShort(n: Long): String = when {
    n >= 10_000 -> String.format(Locale.KOREA, "%.1f만", n / 10_000.0).replace(".0만", "만")
    else -> fmtCount(n)
}

fun fmtDuration(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** "yyyy-MM-dd HH:mm:ss"(KST) → 지금까지 경과 시간 */
fun uptime(openDate: String?): String? = runCatching {
    val open = LocalDateTime.parse(openDate, dateFmt).atZone(ZoneId.of("Asia/Seoul"))
    fmtDuration(Duration.between(open.toInstant(), java.time.Instant.now()).seconds.coerceAtLeast(0))
}.getOrNull()

@Composable
fun Thumb(url: String?, modifier: Modifier = Modifier, ratio: Float = 16f / 9f, corner: Dp = 10.dp,
          overlay: @Composable () -> Unit = {}) {
    Box(modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(corner)).background(C.Surface2)) {
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        overlay()
    }
}

@Composable
fun ViewerBadge(count: Int, modifier: Modifier = Modifier, showLiveLabel: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showLiveLabel) {
            Text("LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(C.Live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            Spacer(Modifier.width(4.dp))
        }
        Row(Modifier.background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (!showLiveLabel) {
                Box(Modifier.size(6.dp).background(C.Live, CircleShape)); Spacer(Modifier.width(4.dp))
            }
            Text("${fmtCount(count)}명", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier, liveRing: Boolean = false) {
    val m = if (liveRing) modifier.border(2.dp, C.Accent, CircleShape).padding(3.dp) else modifier
    Box(m.size(size).clip(CircleShape).background(C.Surface2)) {
        if (!url.isNullOrBlank()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun ChannelLine(name: String, image: String?, verified: Boolean, avatar: Dp = 22.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(image, avatar)
        Spacer(Modifier.width(6.dp))
        Text(name, color = C.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        if (verified) {
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Filled.Verified, null, tint = C.Accent, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
fun TagRow(tags: List<String>, category: String? = null) {
    val all = listOfNotNull(category) + tags
    if (all.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(all) { t ->
            val isCat = t == category
            Text(t, fontSize = 12.sp, color = if (isCat) C.Text else C.Sub, maxLines = 1,
                modifier = Modifier
                    .then(if (isCat) Modifier.background(C.Surface2, RoundedCornerShape(6.dp))
                    else Modifier.border(1.dp, C.Surface2, RoundedCornerShape(6.dp)))
                    .padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(text, fontSize = 14.sp, color = if (selected) Color.Black else C.Sub,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) C.Text else C.Surface2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp))
}

@Composable
fun SortRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { i, s ->
            if (i > 0) Text(" · ", color = C.Dim, fontSize = 13.sp)
            Text(s, fontSize = 13.sp, color = if (i == selected) C.Text else C.Dim,
                fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onSelect(i) })
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = C.Accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun Message(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = C.Sub, fontSize = 14.sp)
        if (action != null && onAction != null) {
            Spacer(Modifier.size(12.dp))
            Text(action, color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(C.Accent)
                    .clickable(onClick = onAction).padding(horizontal = 18.dp, vertical = 9.dp))
        }
    }
}
