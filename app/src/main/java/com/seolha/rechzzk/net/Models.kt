package com.seolha.rechzzk.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiResponse<T>(val code: Int = 0, val message: String? = null, val content: T? = null)

@Serializable
data class Channel(
    val channelId: String = "",
    val channelName: String = "",
    val channelImageUrl: String? = null,
    val verifiedMark: Boolean = false,
    val followerCount: Long = 0,
    val openLive: Boolean = false,
    val channelDescription: String? = null,
    val personalData: PersonalData? = null,
)

@Serializable
data class PersonalData(val following: FollowState? = null)

@Serializable
data class FollowState(val following: Boolean = false, val notification: Boolean? = null)

@Serializable
data class LiveItem(
    val liveId: Long = 0,
    val liveTitle: String = "",
    val liveImageUrl: String? = null,
    val defaultThumbnailImageUrl: String? = null,
    val concurrentUserCount: Int = 0,
    val openDate: String? = null,
    val adult: Boolean = false,
    val tags: List<String> = emptyList(),
    val categoryType: String? = null,
    val liveCategory: String? = null,
    val liveCategoryValue: String? = null,
    val dropsCampaignNo: Long? = null,
    val watchPartyTag: String? = null,
    val channel: Channel? = null,
)

@Serializable
data class PageCursor(val next: JsonObject? = null)

@Serializable
data class Page<T>(val size: Int = 0, val page: PageCursor? = null, val data: List<T> = emptyList())

@Serializable
data class RecommendedHome(val topRecommendedLives: List<LiveItem> = emptyList())

@Serializable
data class LiveDetail(
    val liveId: Long = 0,
    val liveTitle: String = "",
    val status: String = "CLOSE",
    val liveImageUrl: String? = null,
    val defaultThumbnailImageUrl: String? = null,
    val concurrentUserCount: Int = 0,
    val openDate: String? = null,
    val adult: Boolean = false,
    val tags: List<String> = emptyList(),
    val chatChannelId: String? = null,
    val chatActive: Boolean = true,
    val categoryType: String? = null,
    val liveCategory: String? = null,
    val liveCategoryValue: String? = null,
    val livePlaybackJson: String? = null,
    val channel: Channel? = null,
)

@Serializable
data class LivePlayback(val media: List<PlaybackMedia> = emptyList())

@Serializable
data class PlaybackMedia(val mediaId: String = "", val protocol: String = "", val path: String = "")

@Serializable
data class FollowingList(val totalCount: Int = 0, val followingList: List<FollowingItem> = emptyList())

@Serializable
data class FollowingItem(
    val channelId: String = "",
    val channel: Channel? = null,
    val streamer: Streamer? = null,
    val liveInfo: FollowLiveInfo? = null,
)

@Serializable
data class Streamer(val openLive: Boolean = false)

@Serializable
data class FollowLiveInfo(
    val liveTitle: String? = null,
    val concurrentUserCount: Int = 0,
    val liveImageUrl: String? = null,
    val defaultThumbnailImageUrl: String? = null,
    val liveCategoryValue: String? = null,
    val openDate: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class Clip(
    val clipUID: String = "",
    val videoId: String? = null,
    val clipTitle: String = "",
    val ownerChannel: Channel? = null,
    val thumbnailImageUrl: String? = null,
    val duration: Int = 0,
    val readCount: Long = 0,
    val createdDate: String? = null,
    val adult: Boolean = false,
)

@Serializable
data class Video(
    val videoNo: Long = 0,
    val videoId: String? = null,
    val videoTitle: String = "",
    val videoType: String? = null,
    val publishDate: String? = null,
    val thumbnailImageUrl: String? = null,
    val duration: Long = 0,
    val readCount: Long = 0,
    val livePv: Long = 0,
    val videoCategoryValue: String? = null,
    val tags: List<String> = emptyList(),
    val adult: Boolean = false,
    val channel: Channel? = null,
)

@Serializable
data class Category(
    val categoryType: String = "",
    val categoryId: String = "",
    val categoryValue: String = "",
    val posterImageUrl: String? = null,
    val openLiveCount: Int = 0,
    val concurrentUserCount: Int = 0,
)

@Serializable
data class CategoryLives(val liveInfoResponseList: List<LiveItem> = emptyList())

@Serializable
data class PlayInfo(
    val contentType: String? = null,
    val videoId: String? = null,
    val inKey: String? = null,
    val vodStatus: String? = null,
    val contentTitle: String = "",
    val adult: Boolean = false,
    val readCount: Long = 0,
    val ownerChannel: Channel? = null,
)

/** 다시보기·클립 재생 주소. isHls=false면 통짜 mp4 */
data class VodSource(val url: String, val isHls: Boolean)

@Serializable
data class SearchChannel(val channel: Channel = Channel())

@Serializable
data class UserStatus(
    val loggedIn: Boolean = false,
    val userIdHash: String? = null,
    val nickname: String? = null,
    val profileImageUrl: String? = null,
)

@Serializable
data class ChatToken(val accessToken: String = "", val extraToken: String? = null)

/** 썸네일 URL의 {type} 자리표시자를 채움 */
fun thumb(url: String?, fallback: String? = null, size: Int = 720): String? =
    (url ?: fallback)?.replace("{type}", size.toString())

// ── 통나무 파워 ──
@Serializable
data class LogPower(val active: Boolean = false, val amount: Long = 0, val claims: List<PowerClaim> = emptyList())

@Serializable
data class PowerClaim(val claimId: String = "", val claimType: String? = null, val amount: Long = 0)

@Serializable
data class PowerClaimResult(val amount: Long = 0)

@Serializable
data class Prediction(
    val predictionId: String = "",
    val predictionTitle: String = "",
    val status: String = "",              // ACTIVE · EXPIRED(마감) · COMPLETED(결과) · CANCEL
    val remainingDuration: Long = 0,
    val totalDuration: Long = 0,
    val totalLogPowers: Long = 0,
    val optionList: List<PredictionOption> = emptyList(),
    val participation: Participation? = null,
    val winningOptionNo: Int? = null,
)

@Serializable
data class PredictionOption(val optionNo: Int = 0, val optionText: String = "", val totalLogPowers: Long = 0, val participantCount: Int = 0)

@Serializable
data class Participation(val selectedOptionNo: Int? = null, val bettingPowers: Long = 0)

// ── 치즈 ──
@Serializable
data class CoinBalance(val coinBalance: Long = 0)

// ── 채널 프로필 ──
@Serializable
data class ChannelVideos(val page: Int = 0, val totalPages: Int = 0, val totalCount: Int = 0, val data: List<Video> = emptyList())

@Serializable
data class SocialLink(val title: String = "", val landingUrl: String = "")

@Serializable
data class TopExposedVideos(val openLive: LiveItem? = null, val fixedVideo: Video? = null, val latestVideo: Video? = null)

@Serializable
data class ChannelData(
    val description: String? = null,
    val socialLinks: List<SocialLink> = emptyList(),
    val logPowerActive: Boolean = false,
    val logPowerRankingExposure: Boolean = false,
    val topExposedVideos: TopExposedVideos? = null,
)

@Serializable
data class PowerRank(val updatedDate: String? = null, val rankList: List<PowerRankUser> = emptyList())

@Serializable
data class PowerRankUser(val userIdHash: String = "", val nickname: String = "", val profileImageUrl: String? = null,
                         val amount: Long = 0, val ranking: Int = 0)

@Serializable
data class CommunityPage(val comments: CommunityList? = null)

@Serializable
data class CommunityList(val totalCount: Int = 0, val data: List<CommunityPost> = emptyList())

@Serializable
data class CommunityPost(val comment: PostBody = PostBody(), val user: PostUser = PostUser(), val buffNerf: BuffNerf? = null)

@Serializable
data class PostBody(
    val commentId: Long = 0, val content: String = "", val createdDate: String? = null,
    val replyCount: Int = 0, val childObjectCount: Int = 0, val deleted: Boolean = false,
    val attaches: List<PostAttach>? = null,
)

@Serializable
data class PostAttach(val attachType: String = "", val attachValue: String = "")

@Serializable
data class PostUser(val userNickname: String = "", val profileImageUrl: String? = null)

@Serializable
data class BuffNerf(val buffCount: Int = 0)

@Serializable
data class FollowInfo(val channel: FollowChannel? = null)

@Serializable
data class FollowChannel(val personalData: PersonalData? = null)
