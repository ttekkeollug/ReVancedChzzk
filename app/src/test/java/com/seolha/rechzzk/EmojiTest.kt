package com.seolha.rechzzk

import com.seolha.rechzzk.chat.ChatClient
import com.seolha.rechzzk.net.Api
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiTest {
    @Test fun packs() = runBlocking {
        val ch = Api.lives(size = 1).data.first().channel!!.channelId
        val packs = Api.emojiPacks(ch).all
        println("PACKS " + packs.map { "${it.emojiPackName}(${it.emojis.size}${if (it.emojiPackLocked) ",잠김" else ""})" })
        assertTrue(packs.isNotEmpty() && packs.first().emojis.isNotEmpty())
        val map = packs.filter { !it.emojiPackLocked }.flatMap { it.emojis }.associate { it.emojiId to it.imageUrl }
        // 전송 시 메시지 안에 쓴 이모티콘만 골라 담기는지
        val text = "안녕 {:d_1:}{:d_2:} {:없는거:}"
        val used = ChatClient.EMOJI_TOKEN.findAll(text).map { it.groupValues[1] }.filter { it in map }.toList()
        println("USED $used -> ${used.map { map[it] }}")
        assertEquals(listOf("d_1", "d_2"), used)
    }

    /** 공식 웹과 같은 구조의 채널(구독) 이모티콘: 1단계는 사용 가능, 2단계 잠김, 미구독 채널은 전체 잠김 */
    @Test fun channelPackTiers() {
        val json = """{"emojiPacks":[{"emojiPackId":"dp_1","emojiPackName":"CHZZK","emojis":[{"emojiId":"d_1","imageUrl":"u1"}]}],
            "cheatKeyEmojiPacks":[],
            "subscriptionEmojiPacks":[
              {"emojiPackId":"ch1","emojiPackName":"내 채널","emojiPackLocked":false,"emojiTier2Locked":true,
               "emojis":[],"tier1Emojis":[{"emojiId":"s_1","imageUrl":"a"}],"tier2Emojis":[{"emojiId":"s_2","imageUrl":"b"}]},
              {"emojiPackId":"ch2","emojiPackName":"다른 채널","emojiPackLocked":true,"emojiTier2Locked":true,
               "emojis":[],"tier1Emojis":[{"emojiId":"x_1","imageUrl":"c"}],"tier2Emojis":[]}]}"""
        val packs = com.seolha.rechzzk.net.Http.json.decodeFromString(com.seolha.rechzzk.net.EmojiPacks.serializer(), json).all
        val usable = packs.flatMap { p -> p.display.filter { p.usable(it) } }.map { it.emojiId }
        println("TIERS order=${packs.map { it.emojiPackName }} channel=${packs.map { it.isChannelPack }} usable=$usable")
        assertEquals(listOf("내 채널", "다른 채널", "CHZZK"), packs.map { it.emojiPackName })
        assertEquals(listOf("s_1", "d_1"), usable)
    }
}
