package com.seolha.rechzzk.player

import com.seolha.rechzzk.Auth
import com.seolha.rechzzk.LatencyMode
import com.seolha.rechzzk.Prefs
import com.seolha.rechzzk.chat.ChatClient
import com.seolha.rechzzk.net.Api
import com.seolha.rechzzk.net.LiveDetail
import com.seolha.rechzzk.net.LogPower
import com.seolha.rechzzk.net.Prediction
import com.seolha.rechzzk.net.PlayInfo
import com.seolha.rechzzk.net.thumb
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface NowPlaying {
    val title: String
    val channelName: String
    val channelImage: String?

    data class Live(
        val channelId: String,
        val detail: LiveDetail?,
        val error: String? = null,
    ) : NowPlaying {
        override val title get() = detail?.liveTitle ?: ""
        override val channelName get() = detail?.channel?.channelName ?: ""
        override val channelImage get() = detail?.channel?.channelImageUrl
    }

    data class Vod(
        val key: String,
        val info: PlayInfo?,
        val error: String? = null,
    ) : NowPlaying {
        override val title get() = info?.contentTitle ?: ""
        override val channelName get() = info?.ownerChannel?.channelName ?: ""
        override val channelImage get() = info?.ownerChannel?.channelImageUrl
    }
}

/** 현재 재생 중인 콘텐츠(라이브/다시보기/클립)와 채팅 연결을 관리 */
object Session {
    private val scope = MainScope()
    val chat = ChatClient(scope)

    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now.asStateFlow()

    private var pollJob: Job? = null
    private var loadJob: Job? = null
    private var powerJob: Job? = null

    private val _power = MutableStateFlow<LogPower?>(null)
    /** 현재 채널의 통나무 파워 (로그인 시) */
    val power: StateFlow<LogPower?> = _power.asStateFlow()

    private val _prediction = MutableStateFlow<Prediction?>(null)
    val prediction: StateFlow<Prediction?> = _prediction.asStateFlow()

    /** "통나무 +N" 같은 짧은 안내 */
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init {
        // 지연 모드를 바꾸면 현재 라이브를 새 설정으로 다시 연결
        scope.launch {
            Prefs.latency.drop(1).collect { mode ->
                val cur = _now.value as? NowPlaying.Live ?: return@collect
                cur.detail?.let { startPlayer(it, mode) }
            }
        }
    }

    fun openLive(channelId: String) {
        val cur = _now.value
        if (cur is NowPlaying.Live && cur.channelId == channelId && cur.error == null) {
            if (!PlayerHolder.player.isPlaying) { PlayerHolder.player.play(); PlayerHolder.jumpToLive() }
            return
        }
        stopInternal()
        _now.value = NowPlaying.Live(channelId, null)
        loadJob = scope.launch {
            val detail = runCatching { Api.liveDetail(channelId) }.getOrElse {
                _now.value = NowPlaying.Live(channelId, null, "방송 정보를 불러오지 못했습니다"); return@launch
            }
            if (detail.status != "OPEN") {
                _now.value = NowPlaying.Live(channelId, detail, "현재 방송 중이 아닙니다"); return@launch
            }
            _now.value = NowPlaying.Live(channelId, detail)
            if (!startPlayer(detail, Prefs.latency.value)) return@launch
            detail.chatChannelId?.let { chat.connect(it, channelId) }
            pollJob = scope.launch { pollDetail(channelId) }
            powerJob = scope.launch { pollPower(channelId) }
        }
    }

    private fun startPlayer(detail: LiveDetail, mode: LatencyMode): Boolean {
        val media = Api.parsePlayback(detail)?.media.orEmpty()
        val ll = media.firstOrNull { it.mediaId == "LLHLS" }?.path
        val hls = media.firstOrNull { it.mediaId == "HLS" }?.path
        val url = (if (mode.useLlHls) ll ?: hls else hls ?: ll)
        if (url == null) {
            _now.value = NowPlaying.Live(detail.channel?.channelId ?: "", detail,
                if (detail.adult) "성인 인증이 필요한 방송입니다" else "재생 주소를 찾지 못했습니다")
            return false
        }
        // LL-HLS가 없어 일반 HLS로 대체되면 목표 지연 설정을 쓰지 않음
        val effective = if (url == hls && mode.useLlHls) LatencyMode.COMPAT else mode
        PlayerHolder.playLive(url, effective, detail.liveTitle, detail.channel?.channelName,
            thumb(detail.liveImageUrl, detail.defaultThumbnailImageUrl))
        return true
    }

    /** 시청자 수·제목 갱신 (30초 간격) */
    private suspend fun pollDetail(channelId: String) {
        while (scope.isActive) {
            delay(30_000)
            val cur = _now.value as? NowPlaying.Live ?: return
            if (cur.channelId != channelId) return
            runCatching { Api.liveDetail(channelId) }.getOrNull()?.let { d ->
                _now.value = cur.copy(detail = d, error = if (d.status != "OPEN") "방송이 종료되었습니다" else null)
            }
        }
    }

    /** 로그인 상태가 바뀌었을 때 채팅을 다시 연결(읽기 전용 ↔ 전송 가능) */
    fun reconnectChat() {
        val cur = _now.value as? NowPlaying.Live ?: return
        val cid = cur.detail?.chatChannelId ?: return
        if (cur.error == null) {
            chat.connect(cid, cur.channelId)
            powerJob?.cancel()
            if (!Auth.loggedIn) { _power.value = null; _prediction.value = null }
            powerJob = scope.launch { pollPower(cur.channelId) }
        }
    }

    /**
     * 통나무·투표 주기 확인. 공식 웹은 채팅 이벤트로 받기 버튼을 띄우지만,
     * 같은 claims 목록을 log-power에서 주므로 30초마다 확인해서 자동으로 받음.
     */
    private suspend fun pollPower(channelId: String) {
        while (scope.isActive) {
            val cur = _now.value as? NowPlaying.Live
            if (cur?.channelId != channelId) return
            if (Auth.loggedIn) {
                refreshPower(channelId)
                _prediction.value = runCatching { Api.currentPrediction(channelId) }.getOrNull()
                    ?.takeIf { it.status == "ACTIVE" || it.status == "EXPIRED" || it.status == "COMPLETED" }
            }
            delay(30_000)
        }
    }

    suspend fun refreshPower(channelId: String) {
        val p = runCatching { Api.logPower(channelId) }.getOrNull() ?: return
        _power.value = p
        if (!Prefs.autoClaim.value || p.claims.isEmpty()) return
        var got = 0L
        for (c in p.claims) {
            got += runCatching { Api.claimPower(channelId, c.claimId) }.getOrDefault(0)
        }
        if (got > 0) {
            toasts.tryEmit("통나무 +${got} 자동 획득")
            _power.value = runCatching { Api.logPower(channelId) }.getOrNull() ?: p.copy(amount = p.amount + got, claims = emptyList())
        }
    }

    /** 수동 받기(자동 받기를 끈 경우) */
    fun claimAll() {
        val id = (_now.value as? NowPlaying.Live)?.channelId ?: return
        val claims = _power.value?.claims.orEmpty()
        scope.launch {
            var got = 0L
            claims.forEach { got += runCatching { Api.claimPower(id, it.claimId) }.getOrDefault(0) }
            if (got > 0) toasts.tryEmit("통나무 +$got")
            _power.value = runCatching { Api.logPower(id) }.getOrNull() ?: _power.value
        }
    }

    suspend fun participate(optionNo: Int, powers: Long): String {
        val id = (_now.value as? NowPlaying.Live)?.channelId ?: return "방송 정보가 없습니다"
        val pred = _prediction.value ?: return "진행 중인 투표가 없습니다"
        return try {
            Api.participatePrediction(id, pred.predictionId, optionNo, powers)
            _prediction.value = runCatching { Api.currentPrediction(id) }.getOrNull() ?: pred
            _power.value = runCatching { Api.logPower(id) }.getOrNull() ?: _power.value
            "투표에 참여했습니다"
        } catch (e: Exception) {
            e.message ?: "참여하지 못했습니다"
        }
    }

    suspend fun donate(amount: Long, text: String, anonymous: Boolean): String {
        val cur = _now.value as? NowPlaying.Live ?: return "방송 정보가 없습니다"
        val liveId = cur.detail?.liveId ?: return "방송 정보가 없습니다"
        return try {
            Api.donateCheese(cur.channelId, liveId, amount, text, anonymous)
            "${amount}치즈를 후원했습니다"
        } catch (e: Exception) {
            e.message ?: "후원하지 못했습니다"
        }
    }

    fun openClip(clipUID: String) = openVod("clip:$clipUID") { Api.clipPlayInfo(clipUID) }
    fun openVideo(videoNo: Long) = openVod("video:$videoNo") { Api.videoPlayInfo(videoNo.toString()) }

    private fun openVod(key: String, fetch: suspend () -> PlayInfo) {
        val cur = _now.value
        if (cur is NowPlaying.Vod && cur.key == key && cur.error == null) return
        stopInternal()
        _now.value = NowPlaying.Vod(key, null)
        loadJob = scope.launch {
            val info = runCatching { fetch() }.getOrElse {
                _now.value = NowPlaying.Vod(key, null, "영상 정보를 불러오지 못했습니다"); return@launch
            }
            val src = if (info.vodStatus == "ABR_HLS") runCatching { Api.resolveVod(info) }.getOrNull() else null
            if (src == null) {
                _now.value = NowPlaying.Vod(key, info, if (info.adult) "성인 인증이 필요합니다" else "아직 재생할 수 없는 영상입니다")
                return@launch
            }
            _now.value = NowPlaying.Vod(key, info)
            PlayerHolder.playVod(src, info.contentTitle, info.ownerChannel?.channelName, info.ownerChannel?.channelImageUrl)
        }
    }

    private fun stopInternal() {
        loadJob?.cancel(); pollJob?.cancel(); powerJob?.cancel()
        _power.value = null; _prediction.value = null
        chat.disconnect()
        PlayerHolder.stop()
    }

    fun close() {
        stopInternal()
        _now.value = null
    }
}
