package com.seolha.rechzzk.chat

import com.seolha.rechzzk.Auth
import com.seolha.rechzzk.net.Api
import com.seolha.rechzzk.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicLong

data class ChatMessage(
    val id: Long,
    val userIdHash: String,
    val nickname: String,
    val text: String,
    val emojis: Map<String, String>,
    val donation: Int = 0,
    val blinded: Boolean = false,
    val isStreamer: Boolean = false,
    /** 닉네임 앞 배지 이미지(역할 → 구독 → 시청자 배지 순) */
    val badges: List<String> = emptyList(),
)

/**
 * 치지직 채팅 WebSocket 클라이언트.
 * cmd: 100 연결, 10100 연결응답, 0 ping, 10000 pong, 5101 최근채팅요청, 15101 최근채팅,
 *      3101 채팅전송, 93101 채팅, 93102 후원
 */
class ChatClient(private val scope: CoroutineScope) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _canSend = MutableStateFlow(false)
    val canSend: StateFlow<Boolean> = _canSend.asStateFlow()

    private val _pinned = MutableStateFlow<ChatMessage?>(null)
    /** 고정 메시지(공지). 처음 접속할 때와 cmd 94010으로 갱신 */
    val pinned: StateFlow<ChatMessage?> = _pinned.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var ws: WebSocket? = null
    private var sid: String? = null
    private var chatChannelId: String? = null
    private var streamingChannelId: String? = null
    private var extraToken: String? = null
    private var closedByUser = false
    private var connectJob: Job? = null
    private var tid = 3
    private val idGen = AtomicLong()

    fun connect(chatChannelId: String, streamingChannelId: String) {
        disconnect()
        closedByUser = false
        this.chatChannelId = chatChannelId
        this.streamingChannelId = streamingChannelId
        _messages.value = emptyList()
        _pinned.value = null
        connectJob = scope.launch { openSocket() }
    }

    private suspend fun openSocket() {
        val cid = chatChannelId ?: return
        val token = runCatching { Api.chatToken(cid) }.getOrNull()
        if (token == null) {
            delay(3000); if (!closedByUser && scope.isActive) openSocket(); return
        }
        extraToken = token.extraToken
        val user = Auth.user.value?.takeIf { it.loggedIn }
        val auth = if (user?.userIdHash != null) "SEND" else "READ"
        val server = (cid.sumOf { it.code } % 5) + 1
        val req = Request.Builder()
            .url("wss://kr-ss$server.chat.naver.com/chat")
            .header("Origin", "https://chzzk.naver.com")
            .build()
        val connectMsg = buildJsonObject {
            put("ver", "3"); put("cmd", 100); put("svcid", "game"); put("cid", cid); put("tid", 1)
            put("bdy", buildJsonObject {
                put("uid", user?.userIdHash)
                put("devType", 2001)
                put("accTkn", token.accessToken)
                put("auth", auth)
            })
        }
        ws = Http.wsClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(connectMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== ws) return
                runCatching { handle(webSocket, Http.json.parseToJsonElement(text).jsonObject, auth) }
            }

            // 이전 소켓의 종료 이벤트가 새 연결을 건드리지 않도록 현재 소켓일 때만 재연결
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket === ws) onDrop()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket === ws) onDrop()
            }
        })
    }

    private fun onDrop() {
        _connected.value = false
        _canSend.value = false
        if (closedByUser) return
        connectJob = scope.launch { delay(2000); if (!closedByUser) openSocket() }
    }

    private fun handle(socket: WebSocket, obj: JsonObject, auth: String) {
        when (obj["cmd"]?.jsonPrimitive?.intOrNull) {
            0 -> socket.send("""{"ver":"3","cmd":10000}""")
            10100 -> {
                sid = obj["bdy"]?.jsonObject?.get("sid")?.jsonPrimitive?.contentOrNull
                _connected.value = true
                _canSend.value = auth == "SEND"
                val recent = buildJsonObject {
                    put("ver", "3"); put("cmd", 5101); put("svcid", "game"); put("cid", chatChannelId)
                    put("sid", sid); put("tid", 2)
                    put("bdy", buildJsonObject { put("recentMessageCount", 50) })
                }
                socket.send(recent.toString())
            }
            15101 -> {
                val bdy = obj["bdy"] as? JsonObject ?: return
                _pinned.value = (bdy["notice"] as? JsonObject)?.let { parse(it, forNotice = true) }
                val list = bdy["messageList"] as? JsonArray ?: return
                append(list.mapNotNull { parse(it) })
            }
            94010 -> {
                // 고정 메시지 변경. 내용이 없으면 고정 해제
                _pinned.value = (obj["bdy"] as? JsonObject)?.let { parse(it, forNotice = true) }
            }
            93101, 93102 -> {
                val list = obj["bdy"] as? JsonArray ?: return
                append(list.mapNotNull { parse(it) })
            }
        }
    }

    private fun str(o: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.contentOrNull }

    private fun parse(el: JsonElement, forNotice: Boolean = false): ChatMessage? {
        val o = el as? JsonObject ?: return null
        val text = str(o, "msg", "content") ?: return null
        val profile = str(o, "profile")?.let { runCatching { Http.json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val extras = str(o, "extras")?.let { runCatching { Http.json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val nickname = profile?.let { str(it, "nickname") } ?: "익명"
        val uid = profile?.let { str(it, "userIdHash") } ?: str(o, "uid", "userId") ?: ""
        val role = profile?.let { str(it, "userRoleCode") }
        val type = (o["msgTypeCode"] ?: o["messageTypeCode"])?.jsonPrimitive?.intOrNull ?: 1
        val status = str(o, "msgStatusType", "messageStatusType")
        val emojis = (extras?.get("emojis") as? JsonObject)
            ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap() ?: emptyMap()
        val pay = extras?.get("payAmount")?.jsonPrimitive?.longOrNull?.toInt() ?: 0
        if (!forNotice && type != 1 && type != 10) return null
        if (forNotice && text.isBlank()) return null
        return ChatMessage(
            id = idGen.incrementAndGet(),
            userIdHash = uid,
            nickname = nickname,
            text = text,
            emojis = emojis,
            donation = if (type == 10) pay else 0,
            blinded = status == "CBOTBLIND" || status == "HIDDEN",
            isStreamer = role == "streamer",
            badges = profile?.let { badgesOf(it) } ?: emptyList(),
        )
    }

    private fun imageOf(el: JsonElement?): String? =
        ((el as? JsonObject)?.get("imageUrl") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** 공식 앱처럼 역할 배지(매니저·스트리머) → 구독 배지 → 시청자 배지(후원 등) 순서 */
    private fun badgesOf(p: JsonObject): List<String> {
        val out = mutableListOf<String>()
        imageOf(p["badge"])?.let(out::add)
        val sub = ((p["streamingProperty"] as? JsonObject)?.get("subscription") as? JsonObject)?.get("badge")
        imageOf(sub)?.let(out::add)
        (p["viewerBadges"] as? JsonArray)?.forEach { vb -> imageOf((vb as? JsonObject)?.get("badge"))?.let(out::add) }
        return out.distinct().take(4)
    }

    private fun append(items: List<ChatMessage>) {
        if (items.isEmpty()) return
        _messages.update { (it + items).takeLast(MAX) }
    }

    /**
     * 채팅 전송. 메시지 안의 {:이모티콘ID:}는 emojis(ID → 이미지 주소)에 함께 담아 보내야
     * 다른 시청자 화면에서 이미지로 보임.
     */
    fun send(text: String, emojis: Map<String, String> = emptyMap()): Boolean {
        val socket = ws ?: return false
        if (!_canSend.value || text.isBlank()) return false
        val used = EMOJI_TOKEN.findAll(text).map { it.groupValues[1] }.toSet()
        val extras = buildJsonObject {
            put("chatType", "STREAMING"); put("osType", "PC")
            put("extraToken", extraToken); put("streamingChannelId", streamingChannelId)
            put("emojis", buildJsonObject { used.forEach { id -> emojis[id]?.let { put(id, it) } } })
        }
        val msg = buildJsonObject {
            put("ver", "3"); put("cmd", 3101); put("svcid", "game"); put("cid", chatChannelId)
            put("sid", sid); put("retry", false); put("tid", ++tid)
            put("bdy", buildJsonObject {
                put("msg", text.take(100)); put("msgTypeCode", 1)
                put("extras", extras.toString()); put("msgTime", System.currentTimeMillis())
            })
        }
        return socket.send(msg.toString())
    }

    fun disconnect() {
        closedByUser = true
        connectJob?.cancel()
        ws?.close(1000, null)
        ws = null
        _connected.value = false
        _canSend.value = false
    }

    companion object {
        const val MAX = 300
        val EMOJI_TOKEN = Regex("\\{:([^:{}]+):\\}")
    }
}
