package com.seolha.rechzzk

import com.seolha.rechzzk.chat.ChatClient
import com.seolha.rechzzk.net.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test

/** 실제 치지직 서버로 API 파싱·채팅 동작을 검증 (네트워크 필요) */
class LiveApiTest {
    @Test fun endToEnd() = runBlocking {
        val lives = Api.lives()
        println("lives=${lives.data.size} next=${lives.page?.next}")
        assertTrue(lives.data.isNotEmpty())
        val next = Api.lives(next = lives.page?.next)
        println("page2=${next.data.size} first=${next.data.firstOrNull()?.liveTitle}")
        println("page overlap=${next.data.count { n -> lives.data.any { it.liveId == n.liveId } }}")

        println("recommended=${Api.recommended().topRecommendedLives.size}")
        // 성인 방송 등은 재생 정보가 없으므로 재생 가능한 첫 방송으로 검증
        var pick: Pair<com.seolha.rechzzk.net.LiveItem, com.seolha.rechzzk.net.LiveDetail>? = null
        for (l in lives.data) {
            val det = Api.liveDetail(l.channel!!.channelId)
            if (Api.parsePlayback(det)?.media?.isNotEmpty() == true) { pick = l to det; break }
        }
        val (top, d) = pick!!
        val media = Api.parsePlayback(d)!!.media
        println("detail status=${d.status} chat=${d.chatChannelId} media=${media.map { it.mediaId }}")
        assertTrue(media.any { it.mediaId == "LLHLS" })

        // LL-HLS 마스터 → 미디어 플레이리스트에 파셜 세그먼트(EXT-X-PART)가 있는지 확인
        val http = OkHttpClient()
        val ll = media.first { it.mediaId == "LLHLS" }.path
        val master = http.newCall(Request.Builder().url(ll).header("User-Agent", "Mozilla/5.0").build()).execute().body!!.string()
        val variant = master.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") }
        println("master ok=${master.startsWith("#EXTM3U")} variant=${variant?.take(60)}")
        if (variant != null) {
            val vurl = okhttp3.HttpUrl.Builder().scheme("https").host("x").build().let { ll.toHttpUrlCompat().resolve(variant)!! }
            val pl = http.newCall(Request.Builder().url(vurl).header("User-Agent", "Mozilla/5.0").build()).execute().body!!.string()
            println("media playlist tags: " + pl.lines().filter { it.startsWith("#EXT-X-") }.map { it.substringBefore(':') }.distinct())
            println(pl.lines().filter { it.startsWith("#EXT-X-PART-INF") || it.startsWith("#EXT-X-SERVER-CONTROL") || it.startsWith("#EXT-X-TARGETDURATION") })
        }

        val clips = Api.clips()
        val pop = Api.clips("POPULAR", "WITHIN_1_DAY")
        println("clips=${clips.data.size} popular=${pop.data.size} cursor=${clips.page?.next?.keys}")
        val more = Api.clips(next = clips.page?.next)
        println("clips page2=${more.data.size}")
        val info = Api.clipPlayInfo(pop.data.first().clipUID)
        println("clip manifest=${Api.vodManifestUrl(info)?.take(80)} status=${info.vodStatus}")
        // 클립·다시보기: MPD에서 HLS 주소를 뽑아 실제로 재생 가능한 플레이리스트인지 확인
        val videos = Api.homeVideos()
        val vinfo = Api.videoPlayInfo(videos.data.first().videoNo.toString())
        for ((name, pi) in listOf("clip" to info, "video" to vinfo)) {
            val src = Api.resolveVod(pi)!!
            val body = http.newCall(Request.Builder().url(src.url).header("User-Agent", "okhttp").build()).execute().use { it.code to it.body!!.string() }
            println("$name src hls=${src.isHls} http=${body.first} playlist=${body.second.startsWith("#EXTM3U")} variants=${body.second.lines().count { it.startsWith("#EXT-X-STREAM-INF") }}")
            assertTrue(src.isHls && body.first == 200 && body.second.startsWith("#EXTM3U"))
        }

        val cats = Api.categories()
        println("categories=${cats.data.size} next=${cats.page?.next}")
        val cl = Api.categoryLives(cats.data.first().categoryType, cats.data.first().categoryId)
        println("category lives=${cl.liveInfoResponseList.size}")
        val s = Api.searchChannels("울프")
        println("search=${s.data.map { it.channel.channelName + ":" + it.channel.openLive }.take(3)}")

        // 채팅: 익명(READ)으로 접속해 메시지 수신 확인
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val chat = ChatClient(scope)
        chat.connect(d.chatChannelId!!, top.channel!!.channelId)
        var waited = 0
        while (chat.messages.value.size < 5 && waited < 20000) { delay(500); waited += 500 }
        println("chat connected=${chat.connected.value} canSend=${chat.canSend.value} msgs=${chat.messages.value.size}")
        chat.messages.value.take(5).forEach { println("  [${it.nickname}] ${it.text} emojis=${it.emojis.size} don=${it.donation}") }
        assertTrue(chat.messages.value.isNotEmpty())
        chat.disconnect()
    }
}

private fun String.toHttpUrlCompat() = okhttp3.HttpUrl.Companion.run { this@toHttpUrlCompat.toHttpUrl() }
