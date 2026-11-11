package com.seolha.rechzzk

import com.seolha.rechzzk.chat.ChatClient
import com.seolha.rechzzk.net.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** 실제 방송 채팅에서 고정 메시지와 배지 파싱 확인 */
class ChatPinnedTest {
    @Test fun pinnedAndBadges() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var foundPinned = false; var foundBadges = false
        for (l in Api.lives(size = 20).data) {
            val d = runCatching { Api.liveDetail(l.channel!!.channelId) }.getOrNull() ?: continue
            val cid = d.chatChannelId ?: continue
            val chat = ChatClient(scope)
            chat.connect(cid, l.channel!!.channelId)
            var w = 0
            while (chat.messages.value.isEmpty() && w < 8000) { delay(300); w += 300 }
            chat.pinned.value?.let { foundPinned = true; println("PINNED [${l.channel!!.channelName}] ${it.nickname}: ${it.text}") }
            chat.messages.value.firstOrNull { it.badges.isNotEmpty() }?.let { foundBadges = true; println("BADGES ${it.nickname} ${it.badges}") }
            chat.disconnect()
            if (foundPinned && foundBadges) break
        }
        println("foundPinned=$foundPinned foundBadges=$foundBadges")
        assertTrue(foundBadges)
    }
}
