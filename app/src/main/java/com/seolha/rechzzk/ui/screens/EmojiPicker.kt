package com.seolha.rechzzk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.seolha.rechzzk.Auth
import com.seolha.rechzzk.Prefs
import com.seolha.rechzzk.chat.ChatClient
import com.seolha.rechzzk.net.Emoji
import com.seolha.rechzzk.net.EmojiPack
import com.seolha.rechzzk.player.NowPlaying
import com.seolha.rechzzk.player.Session
import com.seolha.rechzzk.ui.C

private sealed interface PickerTab {
    data object Recent : PickerTab
    data object ChannelLogin : PickerTab
    data class Pack(val pack: EmojiPack) : PickerTab
}

/**
 * 치지직 이모티콘 고르기. 탭 순서: 최근 사용 → 채널(구독) 이모티콘(보고 있는 채널 먼저) → 기본·행사 이모티콘.
 * 잠긴 이모티콘(구독하지 않은 채널, 2단계 구독 전용)은 흐리게 보이고 누를 수 없음.
 */
@Composable
fun EmojiPicker(height: Dp, onPick: (String) -> Unit) {
    val packs by Session.emojiPacks.collectAsState()
    val recentAll by Prefs.recentEmojis.collectAsState()
    val user by Auth.user.collectAsState()
    val now by Session.now.collectAsState()
    val live = now as? NowPlaying.Live
    val channelId = live?.channelId
    val channelName = live?.detail?.channel?.channelName

    // 최근 사용 중 지금 이 채널에서 쓸 수 있는 것만 (공식 웹과 같은 방식)
    val usable = remember(packs) { Session.usableEmojis() }
    val recent = remember(recentAll, usable) { recentAll.filter { usable[it.emojiId] == it.imageUrl } }
    val channelPacks = packs.filter { it.isChannelPack }
        .sortedByDescending { it.emojiPackId == channelId || it.emojiPackName == channelName }
    val otherPacks = packs.filter { !it.isChannelPack }
    val tabs = buildList {
        if (recent.isNotEmpty()) add(PickerTab.Recent)
        channelPacks.forEach { add(PickerTab.Pack(it)) }
        if (channelPacks.isEmpty() && user?.loggedIn != true) add(PickerTab.ChannelLogin)
        otherPacks.forEach { add(PickerTab.Pack(it)) }
    }
    // 처음 열 때: 최근 사용이 있으면 그것, 없으면 쓸 수 있는 첫 묶음
    var sel by remember(packs.size, recent.isNotEmpty()) {
        mutableIntStateOf(tabs.indexOfFirst { it is PickerTab.Recent || (it is PickerTab.Pack && !it.pack.emojiPackLocked) }.coerceAtLeast(0))
    }
    val pick: (Emoji) -> Unit = { e -> Prefs.addRecentEmoji(e); onPick("{:${e.emojiId}:}") }

    Column(Modifier.fillMaxWidth().height(height).background(C.Surface)) {
        if (packs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("이모티콘을 불러오는 중…", color = C.Sub, fontSize = 14.sp)
            }
            return@Column
        }
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(tabs) { i, t ->
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .then(if (i == sel) Modifier.border(2.dp, C.Accent, RoundedCornerShape(10.dp)).background(C.Surface2) else Modifier)
                    .clickable { sel = i }.padding(5.dp), contentAlignment = Alignment.Center) {
                    when (t) {
                        PickerTab.Recent -> Icon(Icons.Filled.History, "최근 사용", tint = if (i == sel) C.Accent else C.Sub)
                        PickerTab.ChannelLogin -> Icon(Icons.Filled.Lock, "채널 이모티콘", tint = C.Sub)
                        is PickerTab.Pack -> {
                            val p = t.pack
                            AsyncImage(p.emojiPackImageUrl ?: p.display.firstOrNull()?.imageUrl, p.emojiPackName,
                                Modifier.fillMaxSize().clip(RoundedCornerShape(if (p.isChannelPack) 20.dp else 0.dp))
                                    .alpha(if (p.emojiPackLocked) 0.4f else 1f), contentScale = ContentScale.Crop)
                            if (p.emojiPackLocked) Icon(Icons.Filled.Lock, null, tint = Color.White,
                                modifier = Modifier.size(14.dp).align(Alignment.BottomEnd))
                        }
                    }
                }
            }
        }
        when (val t = tabs.getOrNull(sel)) {
            PickerTab.Recent -> {
                Label("최근 사용한 이모티콘")
                EmojiGrid(recent, { true }, pick)
            }
            PickerTab.ChannelLogin -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text("로그인하면 채널(구독) 이모티콘을 볼 수 있습니다", color = C.Sub, fontSize = 14.sp)
            }
            is PickerTab.Pack -> {
                val p = t.pack
                Label(p.emojiPackName + when {
                    p.isChannelPack && p.emojiPackLocked -> "  ·  구독하면 쓸 수 있어요"
                    p.isChannelPack && p.emojiTier2Locked && !p.tier2Emojis.isNullOrEmpty() -> "  ·  흐린 것은 2단계 구독 전용"
                    p.isChannelPack -> "  ·  채널 이모티콘"
                    else -> ""
                })
                EmojiGrid(p.display, p::usable, pick)
            }
            null -> {}
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = C.Sub, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp))
}

@Composable
private fun EmojiGrid(list: List<Emoji>, usable: (Emoji) -> Boolean, onPick: (Emoji) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(48.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(list, key = { it.emojiId + it.imageUrl }) { e ->
            val ok = usable(e)
            Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                .then(if (ok) Modifier.clickable { onPick(e) } else Modifier).padding(4.dp)) {
                AsyncImage(e.imageUrl, e.emojiId, Modifier.fillMaxSize().alpha(if (ok) 1f else 0.3f), contentScale = ContentScale.Fit)
            }
        }
    }
}

/** 입력 중인 메시지 미리보기: {:ID:}를 이모티콘 그림으로 바꿔 보여줌 */
@Composable
fun EmojiPreview(text: String, emojis: Map<String, String>) {
    if (!ChatClient.EMOJI_TOKEN.containsMatchIn(text)) return
    val inline = HashMap<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        var last = 0
        ChatClient.EMOJI_TOKEN.findAll(text).forEachIndexed { i, m ->
            append(text.substring(last, m.range.first))
            val url = emojis[m.groupValues[1]]
            if (url != null) {
                val id = "p$i"
                appendInlineContent(id, m.value)
                inline[id] = InlineTextContent(Placeholder(24.sp, 24.sp, PlaceholderVerticalAlign.Center)) {
                    AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            } else append(m.value)
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
    Text(annotated, inlineContent = inline, color = C.Text, fontSize = 15.sp, maxLines = 2,
        modifier = Modifier.fillMaxWidth().background(C.Surface).padding(horizontal = 16.dp, vertical = 6.dp))
}
