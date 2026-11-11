package com.seolha.rechzzk.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.seolha.rechzzk.player.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

sealed interface Route {
    data object Player : Route
    data object Login : Route
    data object Search : Route
    data class Category(val type: String, val id: String, val name: String) : Route
    data class Channel(val channelId: String) : Route
}

object Nav {
    val stack = mutableStateListOf<Route>()
    var tab by mutableIntStateOf(0)

    val top: Route? get() = stack.lastOrNull()

    fun push(r: Route) {
        stack.remove(r)
        stack.add(r)
    }

    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex); return true
    }

    /** 채널 프로필 열기 (플레이어 위에서 열면 플레이어는 미니 플레이어로 내려감) */
    fun openChannel(channelId: String) { stack.remove(Route.Player); push(Route.Channel(channelId)) }

    fun openLive(channelId: String) { Session.openLive(channelId); push(Route.Player) }
    fun openClip(uid: String) { Session.openClip(uid); push(Route.Player) }
    fun openVideo(no: Long) { Session.openVideo(no); push(Route.Player) }
}

/** 커서 기반 무한 스크롤 목록 */
class Paged<T>(
    private val scope: CoroutineScope,
    /** 페이지 사이에 같은 항목이 다시 오면(시청자 수 변동 등) LazyList 키 중복으로 크래시하므로 걸러냄 */
    private val keyOf: (T) -> Any,
    private val fetch: suspend (next: JsonObject?) -> Pair<List<T>, JsonObject?>,
) {
    private val seen = HashSet<Any>()
    val items = mutableStateListOf<T>()
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var end by mutableStateOf(false); private set
    private var next: JsonObject? = null
    private var job: Job? = null

    fun refresh() {
        job?.cancel(); items.clear(); seen.clear(); next = null; end = false; error = null; loading = false
        loadMore()
    }

    fun loadMore() {
        if (loading || end) return
        loading = true
        job = scope.launch {
            try {
                val (list, n) = fetch(next)
                items.addAll(list.filter { seen.add(keyOf(it)) }); next = n
                if (n == null || list.isEmpty()) end = true
                error = null
                loading = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // refresh()로 취소된 요청은 상태를 건드리지 않음
            } catch (e: Exception) {
                error = "불러오지 못했습니다"
                loading = false
            }
        }
    }
}
