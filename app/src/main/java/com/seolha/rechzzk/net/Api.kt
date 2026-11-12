package com.seolha.rechzzk.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

class ApiException(val code: Int, msg: String?) : IOException(msg ?: "API error $code")

object Api {
    private const val BASE = "https://api.chzzk.naver.com/service"
    private const val COMM = "https://comm-api.game.naver.com/nng_main/v1"

    private suspend fun <T> get(url: String, serializer: KSerializer<T>): T {
        val text = Http.getText(url)
        val res = Http.json.decodeFromString(ApiResponse.serializer(serializer), text)
        if (res.code != 200 || res.content == null) throw ApiException(res.code, res.message)
        return res.content
    }

    private fun url(path: String, params: Map<String, Any?> = emptyMap()): String {
        val b = path.toHttpUrl().newBuilder()
        params.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v.toString()) }
        return b.build().toString()
    }

    /** 페이지 커서(next 객체)를 쿼리 파라미터로 변환 */
    fun cursorParams(next: JsonObject?): Map<String, Any?> =
        next?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content } ?: emptyMap()

    // ── 라이브 ──
    suspend fun lives(sortType: String = "POPULAR", next: JsonObject? = null, size: Int = 20) =
        get(url("$BASE/v1/lives", mapOf("size" to size, "sortType" to sortType) + cursorParams(next)),
            Page.serializer(LiveItem.serializer()))

    suspend fun recommended() = get(url("$BASE/v1/home/recommended"), RecommendedHome.serializer())

    suspend fun liveDetail(channelId: String) =
        get(url("$BASE/v3/channels/$channelId/live-detail"), LiveDetail.serializer())

    // ── 팔로잉 (로그인 필요) ──
    /** 웹 팔로잉 페이지가 쓰는 엔드포인트 (썸네일 포함, 서버 정렬: RECOMMEND·POPULAR·UNPOPULAR·LATEST) */
    suspend fun followingLives(sortType: String = "RECOMMEND") =
        get(url("$BASE/v1/channels/following-lives", mapOf("sortType" to sortType)), FollowingList.serializer())

    suspend fun followings(size: Int = 505) =
        get(url("$BASE/v1/channels/followings", mapOf("page" to 0, "size" to size, "sortType" to "FOLLOW")),
            FollowingList.serializer())

    // ── 클립 / 동영상 ──
    suspend fun clips(orderType: String? = null, filterType: String? = null, next: JsonObject? = null) =
        get(url("$BASE/v1/home/recommended/clips",
            mapOf("orderType" to orderType, "filterType" to filterType) + cursorParams(next)),
            Page.serializer(Clip.serializer()))

    suspend fun homeVideos(next: JsonObject? = null) =
        get(url("$BASE/v1/home/videos", mapOf("size" to 20) + cursorParams(next)), Page.serializer(Video.serializer()))

    suspend fun clipPlayInfo(clipUID: String) = get(url("$BASE/v1/play-info/clip/$clipUID"), PlayInfo.serializer())

    suspend fun videoPlayInfo(videoNo: String) = get(url("$BASE/v1/play-info/video/$videoNo"), PlayInfo.serializer())

    fun vodManifestUrl(info: PlayInfo): String? {
        val id = info.videoId ?: return null
        val key = info.inKey ?: return null
        return url("https://play.vod.naver.com/v4/playback/$id", mapOf("key" to key))
    }

    /**
     * 네이버 VOD의 MPD는 화질별로 세그먼트 인덱스 없는 통짜 mp4(BaseURL)만 담고 있어 ExoPlayer DASH로는 재생되지 않음.
     * 대신 MPD 안의 nvod:m3u 속성(HLS 마스터, 화질 자동 전환)을 꺼내 쓰고, 없으면 가장 높은 화질의 mp4를 직접 재생.
     */
    suspend fun resolveVod(info: PlayInfo): VodSource? {
        val manifest = vodManifestUrl(info) ?: return null
        val xml = Http.getText(manifest, accept = "application/dash+xml")
        fun unescape(v: String) = v.replace("&amp;", "&")
        Regex("<AdaptationSet[^>]*mimeType=\"video/mp2t\"[^>]*nvod:m3u=\"([^\"]+)\"").find(xml)
            ?.let { return VodSource(unescape(it.groupValues[1]), true) }
        Regex("nvod:m3u=\"([^\"]+)\"").find(xml)?.let { return VodSource(unescape(it.groupValues[1]), true) }
        val best = Regex("<Representation[^>]*bandwidth=\"(\\d+)\"[^>]*codecs=\"[^\"]*mp4a[^\"]*\"[^>]*>.*?<BaseURL>([^<]+)</BaseURL>",
            RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).maxByOrNull { it.groupValues[1].toLongOrNull() ?: 0 }
        return best?.let { VodSource(unescape(it.groupValues[2]), false) }
    }

    // ── 카테고리 / 검색 ──
    suspend fun categories(next: JsonObject? = null) =
        get(url("$BASE/v1/categories/live", mapOf("size" to 30) + cursorParams(next)), Page.serializer(Category.serializer()))

    suspend fun categoryLives(type: String, id: String) =
        get(url("$BASE/v1/categories/$type/$id/lives", mapOf("size" to 50)), CategoryLives.serializer())

    suspend fun searchChannels(keyword: String) =
        get(url("$BASE/v1/search/channels", mapOf("keyword" to keyword, "size" to 30)), Page.serializer(SearchChannel.serializer()))

    // ── 계정 / 채팅 ──
    suspend fun userStatus() = get(url("$COMM/user/getUserStatus"), UserStatus.serializer())

    suspend fun chatToken(chatChannelId: String) =
        get(url("$COMM/chats/access-token", mapOf("channelId" to chatChannelId, "chatType" to "STREAMING")),
            ChatToken.serializer())

    // ── 통나무 파워 / 투표 ──
    suspend fun logPower(channelId: String) = get(url("$BASE/v1/channels/$channelId/log-power"), LogPower.serializer())

    /** 통나무 받기. 받은 양 반환 */
    suspend fun claimPower(channelId: String, claimId: String): Long =
        write("PUT", url("$BASE/v1/channels/$channelId/log-power/claims/$claimId"), "{}", PowerClaimResult.serializer()).amount

    /** 진행 중인 투표. 응답이 단일 객체 또는 목록일 수 있어 predictionId를 가진 첫 객체를 찾음 */
    suspend fun currentPrediction(channelId: String): Prediction? {
        val text = Http.getText(url("$BASE/v1/channels/$channelId/log-power/prediction"))
        val content = runCatching { Http.json.parseToJsonElement(text).jsonObject["content"] }.getOrNull() ?: return null
        val found = findPrediction(content) ?: return null
        // 투표가 없으면 predictionId가 null인 객체가 옴
        val id = (found["predictionId"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: return null
        // 내 참여 정보까지 포함된 상세
        return runCatching {
            get(url("$BASE/v1/channels/$channelId/log-power/predictions/$id", mapOf("fields" to "participation")), Prediction.serializer())
        }.getOrElse { Http.json.decodeFromJsonElement(Prediction.serializer(), found) }
    }

    private fun findPrediction(el: JsonElement): JsonObject? = when (el) {
        is JsonObject -> if (el.containsKey("predictionId")) el else el.values.firstNotNullOfOrNull { findPrediction(it) }
        is JsonArray -> el.firstNotNullOfOrNull { findPrediction(it) }
        else -> null
    }

    suspend fun participatePrediction(channelId: String, predictionId: String, optionNo: Int, powers: Long) {
        val body = buildJsonObject { put("selectedOptionNo", optionNo); put("bettingPowers", powers) }.toString()
        writeRaw("PUT", url("$BASE/v1/channels/$channelId/log-power/predictions/$predictionId/participation"), body)
    }

    // ── 치즈 ──
    private const val COMMERCIAL = "https://api.chzzk.naver.com/commercial"

    suspend fun cheeseBalance() = get(url("$COMMERCIAL/v1/coin/balance"), CoinBalance.serializer()).coinBalance

    /** 채팅 후원(치즈). 실제 결제되는 요청이므로 UI에서 반드시 확인을 거친 뒤 호출 */
    suspend fun donateCheese(channelId: String, liveId: Long, amount: Long, text: String, anonymous: Boolean) {
        val extras = buildJsonObject {
            put("chatType", "STREAMING"); put("osType", "PC"); put("streamingChannelId", channelId)
            put("emojis", buildJsonObject { })
        }.toString()
        val body = buildJsonObject {
            put("channelId", channelId); put("liveId", liveId); put("isAnonymous", anonymous)
            put("donationType", "CHAT"); put("payType", "CURRENCY"); put("payAmount", amount)
            put("donationText", text); put("donationImageUrl", ""); put("donationVideoUrl", "")
            put("replaceProhibitWord", false); put("useSpeech", false); put("extras", extras)
        }.toString()
        writeRaw("POST", url("$COMMERCIAL/v1/donate"), body)
    }

    /** 본문 있는 요청. 실패하면 서버 메시지를 담은 ApiException */
    private suspend fun writeRaw(method: String, url: String, body: String): JsonObject? {
        val (status, text) = Http.send(method, url, body)
        val obj = runCatching { Http.json.parseToJsonElement(text).jsonObject }.getOrNull()
        val code = (obj?.get("code") as? JsonPrimitive)?.content?.toIntOrNull() ?: status
        if (status !in 200..299 || code != 200) {
            val msg = (obj?.get("message") as? JsonPrimitive)?.content
            throw ApiException(code, msg ?: "요청 실패 ($status)")
        }
        return obj
    }

    private suspend fun <T> write(method: String, url: String, body: String, serializer: KSerializer<T>): T {
        val obj = writeRaw(method, url, body)
        val content = obj?.get("content") ?: throw ApiException(0, "빈 응답")
        return Http.json.decodeFromJsonElement(serializer, content)
    }

    // ── 채널 프로필 ──
    suspend fun channel(id: String) = get(url("$BASE/v1/channels/$id"), Channel.serializer())

    suspend fun channelData(id: String) = get(url("$BASE/v1/channels/$id/data",
        mapOf("fields" to "description,socialLinks,logPowerActive,logPowerRankingExposure,topExposedVideos")), ChannelData.serializer())

    /** sortType: LATEST·POPULAR, videoType: ""(전체)·REPLAY(지난 방송)·UPLOAD(업로드) */
    suspend fun channelVideos(id: String, sortType: String, videoType: String, page: Int) =
        get(url("$BASE/v1/channels/$id/videos", mapOf("sortType" to sortType, "pagingType" to "PAGE", "page" to page,
            "size" to 24, "publishDateAt" to "", "videoType" to videoType)), ChannelVideos.serializer())

    /** filterType: ALL·WITHIN_ONE_DAY·WITHIN_SEVEN_DAYS·WITHIN_THIRTY_DAYS, orderType: POPULAR·RECENT */
    suspend fun channelClips(id: String, filterType: String, orderType: String, next: JsonObject?) =
        get(url("$BASE/v1/channels/$id/clips", mapOf("filterType" to filterType, "orderType" to orderType, "size" to 30) + cursorParams(next)),
            Page.serializer(Clip.serializer()))

    suspend fun powerRankMonthly(id: String) = get(url("$BASE/v1/channels/$id/log-power/rank/monthly"), PowerRank.serializer())

    /** 커뮤니티 글 (limit는 10 이상이어야 함) */
    suspend fun communityPosts(id: String, page: Int) =
        get(url("https://apis.naver.com/nng_main/nng_comment_api/v1/type/CHANNEL_POST/id/$id/comments",
            mapOf("limit" to 10, "offset" to page * 10, "orderType" to "DESC", "pagingType" to "PAGE")), CommunityPage.serializer())

    // ── 팔로우 / 채널 알림(종) — 로그인 필요 ──
    suspend fun followState(id: String): FollowState? =
        get(url("$BASE/v1/channels/$id/follow"), FollowInfo.serializer()).channel?.personalData?.following

    suspend fun setFollow(id: String, on: Boolean) { writeRaw(if (on) "POST" else "DELETE", url("$BASE/v1/channels/$id/follow"), "{}") }

    suspend fun setChannelAlert(id: String, on: Boolean) { writeRaw(if (on) "PUT" else "DELETE", url("$BASE/v1/channels/$id/notification"), "{}") }

    /** 채널에서 쓸 수 있는 이모티콘 묶음 (로그인하면 구독 이모티콘의 잠금 여부까지 내려옴) */
    suspend fun emojiPacks(channelId: String) = get(url("$BASE/v1/channels/$channelId/emoji-packs"), EmojiPacks.serializer())

    fun parsePlayback(detail: LiveDetail): LivePlayback? =
        detail.livePlaybackJson?.let { runCatching { Http.json.decodeFromString(LivePlayback.serializer(), it) }.getOrNull() }
}
