package com.seolha.rechzzk

import com.seolha.rechzzk.net.Api
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** 스크린샷의 채널(샵샵샵)로 프로필 API 파싱 검증 */
class ChannelApiTest {
    private val id = "4064e082140b83787fe24382efaefeab"

    @Test fun profile() = runBlocking {
        val ch = Api.channel(id)
        println("CH ${ch.channelName} followers=${ch.followerCount} desc=${ch.channelDescription}")
        val data = Api.channelData(id)
        println("DATA links=${data.socialLinks.map { it.title }} logPower=${data.logPowerActive}/${data.logPowerRankingExposure} latest=${data.topExposedVideos?.latestVideo?.videoTitle}")
        assertTrue(data.socialLinks.isNotEmpty())
        for ((t, label) in listOf("" to "전체", "REPLAY" to "지난방송", "UPLOAD" to "업로드")) {
            val v = Api.channelVideos(id, "LATEST", t, 0)
            println("VIDEOS $label total=${v.totalCount} pages=${v.totalPages} first=${v.data.firstOrNull()?.videoTitle}")
        }
        val pop = Api.channelVideos(id, "POPULAR", "", 0); println("VIDEOS 인기 first=${pop.data.firstOrNull()?.videoTitle}")
        for (f in listOf("ALL", "WITHIN_ONE_DAY", "WITHIN_SEVEN_DAYS", "WITHIN_THIRTY_DAYS")) {
            val c = Api.channelClips(id, f, "POPULAR", null); println("CLIPS $f n=${c.data.size} next=${c.page?.next}")
        }
        val c1 = Api.channelClips(id, "ALL", "RECENT", null)
        val c2 = Api.channelClips(id, "ALL", "RECENT", c1.page?.next)
        println("CLIPS recent p1=${c1.data.size} p2=${c2.data.size} overlap=${c2.data.count { a -> c1.data.any { it.clipUID == a.clipUID } }}")
        val rank = Api.powerRankMonthly(id); println("RANK ${rank.updatedDate} ${rank.rankList.take(3).map { "${it.ranking}.${it.nickname}:${it.amount}" }}")
        val posts = Api.communityPosts(id, 0).comments!!
        println("POSTS total=${posts.totalCount} n=${posts.data.size} first=${posts.data.first().comment.content.take(30)} by=${posts.data.first().user.userNickname} buff=${posts.data.first().buffNerf?.buffCount}")
        assertTrue(posts.data.isNotEmpty())
    }
}
