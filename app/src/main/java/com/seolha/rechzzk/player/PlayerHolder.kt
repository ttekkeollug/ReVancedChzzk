package com.seolha.rechzzk.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.seolha.rechzzk.net.VodSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.seolha.rechzzk.LatencyMode
import com.seolha.rechzzk.net.Http
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QualityOption(val label: String, val height: Int, val groupIndex: Int, val trackIndex: Int)

/**
 * 앱 전체에서 하나만 쓰는 플레이어.
 * 지연을 줄이는 핵심 설정:
 *  1) LL-HLS 주소 사용 (파셜 세그먼트 단위로 받아서 엣지에 붙어 재생)
 *  2) 재생 시작/재버퍼링 후 시작 버퍼를 300ms/800ms로 최소화
 *  3) LiveConfiguration 목표 지연 + 배속 제어로 뒤처지면 최대 1.08~1.15배로 따라잡음
 *     (목표를 정하지 않으면 플레이리스트의 PART-HOLD-BACK을 그대로 사용)
 */
@OptIn(UnstableApi::class)
object PlayerHolder {
    lateinit var player: ExoPlayer
        private set
    private lateinit var trackSelector: DefaultTrackSelector

    private val _radio = MutableStateFlow(false)
    val radio: StateFlow<Boolean> = _radio.asStateFlow()

    private val _quality = MutableStateFlow("자동")
    val quality: StateFlow<String> = _quality.asStateFlow()

    private val mediaFactory by lazy {
        OkHttpDataSource.Factory(Http.mediaClient)
            .setUserAgent(Http.UA)
            .setDefaultRequestProperties(mapOf("Referer" to "https://chzzk.naver.com/", "Origin" to "https://chzzk.naver.com"))
    }

    fun init(ctx: Context) {
        if (::player.isInitialized) return
        trackSelector = DefaultTrackSelector(ctx)
        val loadControl = DefaultLoadControl.Builder()
            // minBuffer, maxBuffer, 재생 시작 버퍼, 재버퍼링 후 시작 버퍼
            .setBufferDurationsMs(1_500, 20_000, 300, 800)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val speedControl = DefaultLivePlaybackSpeedControl.Builder()
            .setFallbackMinPlaybackSpeed(0.97f)
            .setFallbackMaxPlaybackSpeed(1.08f)
            .setProportionalControlFactor(0.3f)       // 기본 0.1보다 빠르게 목표 지연으로 수렴
            .setMaxLiveOffsetErrorMsForUnitSpeed(60)
            .setMinUpdateIntervalMs(500)
            .setTargetLiveOffsetIncrementOnRebufferMs(250) // 끊기면 목표를 조금만 늘림
            .build()
        player = ExoPlayer.Builder(ctx)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setLivePlaybackSpeedControl(speedControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    private fun metadata(title: String, artist: String?, artwork: String?) =
        MediaMetadata.Builder().setTitle(title).setArtist(artist)
            .apply { if (artwork != null) setArtworkUri(Uri.parse(artwork)) }
            .build()

    fun playLive(url: String, mode: LatencyMode, title: String, artist: String?, artwork: String?) {
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setMediaMetadata(metadata(title, artist, artwork))
        if (mode.targetMs > 0) {
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(mode.targetMs)
                    .setMinOffsetMs(mode.minMs)
                    .setMaxOffsetMs(mode.maxMs)
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(mode.maxSpeed)
                    .build()
            )
        }
        val source = HlsMediaSource.Factory(mediaFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(builder.build())
        resetQuality()
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    fun playVod(src: VodSource, title: String, artist: String?, artwork: String?) {
        val item = MediaItem.Builder()
            .setUri(src.url)
            .setMimeType(if (src.isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            .setMediaMetadata(metadata(title, artist, artwork))
            .build()
        val source = if (src.isHls) HlsMediaSource.Factory(mediaFactory).createMediaSource(item)
        else ProgressiveMediaSource.Factory(mediaFactory).createMediaSource(item)
        resetQuality()
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    /** 클립 피드처럼 별도 플레이어에서 쓸 미디어 소스 */
    fun vodMediaSource(src: VodSource): androidx.media3.exoplayer.source.MediaSource {
        val item = MediaItem.Builder().setUri(src.url)
            .setMimeType(if (src.isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4).build()
        return if (src.isHls) HlsMediaSource.Factory(mediaFactory).createMediaSource(item)
        else ProgressiveMediaSource.Factory(mediaFactory).createMediaSource(item)
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    /** 실시간 엣지로 점프 */
    fun jumpToLive() {
        if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
    }

    /** 현재 라이브 지연(ms). 라이브가 아니면 null */
    fun liveOffsetMs(): Long? {
        if (!player.isCurrentMediaItemLive) return null
        val v = player.currentLiveOffset
        return if (v == C.TIME_UNSET) null else v
    }

    /** 라디오 모드: 영상 렌더링을 끄고 480p 이하 변형만 받아 데이터/배터리 절약 */
    fun setRadio(on: Boolean) {
        _radio.value = on
        val b = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, on)
        if (on) b.setMaxVideoBitrate(1_600_000).clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        else b.setMaxVideoBitrate(Int.MAX_VALUE)
        player.trackSelectionParameters = b.build()
        if (on) _quality.value = "자동"
    }

    fun qualityOptions(): List<QualityOption> {
        val out = mutableListOf<QualityOption>()
        player.currentTracks.groups.forEachIndexed { gi, g ->
            if (g.type != C.TRACK_TYPE_VIDEO) return@forEachIndexed
            for (ti in 0 until g.length) {
                val f = g.getTrackFormat(ti)
                if (f.height <= 0 || !g.isTrackSupported(ti)) continue
                val fps = if (f.frameRate >= 50f) "60" else ""
                out += QualityOption("${f.height}p$fps", f.height, gi, ti)
            }
        }
        return out.distinctBy { it.label }.sortedByDescending { it.height }
    }

    fun setQuality(opt: QualityOption?) {
        val b = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (opt != null) {
            val group = player.currentTracks.groups.getOrNull(opt.groupIndex) ?: return
            b.addOverride(TrackSelectionOverride(group.mediaTrackGroup, opt.trackIndex))
        }
        player.trackSelectionParameters = b.build()
        _quality.value = opt?.label ?: "자동"
    }

    private fun resetQuality() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
        _quality.value = "자동"
        if (_radio.value) setRadio(true)
    }
}
