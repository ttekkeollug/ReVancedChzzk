package com.seolha.rechzzk

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 지연 모드. 치지직 LL-HLS는 파셜 세그먼트 1초(PART-TARGET=1.0), 서버 권장 홀드백 3.05초(PART-HOLD-BACK).
 * targetMs: 라이브 엣지로부터 유지할 목표 지연(0이면 서버 권장값 사용). maxSpeed: 뒤처졌을 때 따라잡는 최대 배속.
 * 너무 낮게 잡으면 파셜이 아직 안 만들어져 끊기므로, 끊길 때마다 플레이어가 목표를 250ms씩 자동으로 늘림.
 */
enum class LatencyMode(val label: String, val desc: String, val useLlHls: Boolean,
                       val targetMs: Long, val minMs: Long, val maxMs: Long, val maxSpeed: Float) {
    ULTRA("초저지연", "약 1.5초 · 네트워크가 좋을 때", true, 1500, 1000, 4000, 1.15f),
    LOW("저지연", "약 2.5초 · 기본값", true, 2500, 1500, 6000, 1.08f),
    STABLE("안정", "약 3초 · 서버 권장값", true, 0, 0, 0, 1.05f),
    COMPAT("호환", "일반 HLS · LL-HLS가 안 될 때", false, 0, 0, 0, 1.0f),
    ;

    /** "실시간으로" 버튼을 띄울 기준 지연 */
    val behindThresholdMs: Long get() = (if (targetMs > 0) targetMs else if (useLlHls) 3100 else 12000) + 2500
}

object Prefs {
    private lateinit var sp: SharedPreferences

    private val _latency = MutableStateFlow(LatencyMode.LOW)
    val latency: StateFlow<LatencyMode> = _latency.asStateFlow()

    private val _autoPip = MutableStateFlow(true)
    val autoPip: StateFlow<Boolean> = _autoPip.asStateFlow()

    private val _cleanBot = MutableStateFlow(false)
    val cleanBot: StateFlow<Boolean> = _cleanBot.asStateFlow()

    private val _backgroundPlay = MutableStateFlow(true)
    /** 앱을 나가도 라디오 모드일 때 계속 재생 */
    val backgroundPlay: StateFlow<Boolean> = _backgroundPlay.asStateFlow()

    private val _autoClaim = MutableStateFlow(true)
    /** 방송 시청 중 생기는 통나무 자동 받기 */
    val autoClaim: StateFlow<Boolean> = _autoClaim.asStateFlow()

    private val _liveAlerts = MutableStateFlow(true)
    /** 팔로우 채널 방송 시작 알림 */
    val liveAlerts: StateFlow<Boolean> = _liveAlerts.asStateFlow()

    private val _alertAll = MutableStateFlow(false)
    /** true면 알림(종) 설정과 상관없이 팔로우한 모든 채널 알림 */
    val alertAll: StateFlow<Boolean> = _alertAll.asStateFlow()

    private val _fastAlerts = MutableStateFlow(true)
    /** 빠른 알림(1분 간격, 상시 알림 표시) */
    val fastAlerts: StateFlow<Boolean> = _fastAlerts.asStateFlow()

    private val _landscapeChat = MutableStateFlow(true)
    /** 가로 전체화면에서 채팅 패널 열림 여부 (마지막 상태 기억) */
    val landscapeChat: StateFlow<Boolean> = _landscapeChat.asStateFlow()

    private val _recentEmojis = MutableStateFlow<List<com.seolha.rechzzk.net.Emoji>>(emptyList())
    /** 최근 사용한 이모티콘 (최신순, 최대 30개) */
    val recentEmojis: StateFlow<List<com.seolha.rechzzk.net.Emoji>> = _recentEmojis.asStateFlow()

    fun addRecentEmoji(e: com.seolha.rechzzk.net.Emoji) {
        val list = (listOf(e) + _recentEmojis.value.filter { it.emojiId != e.emojiId }).take(30)
        _recentEmojis.value = list
        sp.edit().putString("recentEmojis", com.seolha.rechzzk.net.Http.json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(com.seolha.rechzzk.net.Emoji.serializer()), list)).apply()
    }

    lateinit var raw: SharedPreferences
        private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("rechzzk", Context.MODE_PRIVATE)
        _latency.value = runCatching { LatencyMode.valueOf(sp.getString("latency", "LOW")!!) }.getOrDefault(LatencyMode.LOW)
        _autoPip.value = sp.getBoolean("autoPip", true)
        _cleanBot.value = sp.getBoolean("cleanBot", false)
        _backgroundPlay.value = sp.getBoolean("bgPlay", true)
        _autoClaim.value = sp.getBoolean("autoClaim", true)
        _liveAlerts.value = sp.getBoolean("liveAlerts", true)
        _alertAll.value = sp.getBoolean("alertAll", false)
        _fastAlerts.value = sp.getBoolean("fastAlerts", true)
        _landscapeChat.value = sp.getBoolean("landscapeChat", true)
        _recentEmojis.value = runCatching {
            com.seolha.rechzzk.net.Http.json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(com.seolha.rechzzk.net.Emoji.serializer()), sp.getString("recentEmojis", "[]")!!)
        }.getOrDefault(emptyList())
        raw = sp
    }

    fun setLandscapeChat(v: Boolean) { _landscapeChat.value = v; sp.edit().putBoolean("landscapeChat", v).apply() }

    fun setFastAlerts(v: Boolean) { _fastAlerts.value = v; sp.edit().putBoolean("fastAlerts", v).apply() }

    fun setAutoClaim(v: Boolean) { _autoClaim.value = v; sp.edit().putBoolean("autoClaim", v).apply() }
    fun setLiveAlerts(v: Boolean) { _liveAlerts.value = v; sp.edit().putBoolean("liveAlerts", v).apply() }
    fun setAlertAll(v: Boolean) { _alertAll.value = v; sp.edit().putBoolean("alertAll", v).apply() }

    fun setLatency(m: LatencyMode) { _latency.value = m; sp.edit().putString("latency", m.name).apply() }
    fun setAutoPip(v: Boolean) { _autoPip.value = v; sp.edit().putBoolean("autoPip", v).apply() }
    fun setCleanBot(v: Boolean) { _cleanBot.value = v; sp.edit().putBoolean("cleanBot", v).apply() }
    fun setBackgroundPlay(v: Boolean) { _backgroundPlay.value = v; sp.edit().putBoolean("bgPlay", v).apply() }
}
