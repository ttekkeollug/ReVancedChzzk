package com.seolha.rechzzk.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

/** 영상 확대 상태 (1배 ~ 4배, 확대한 상태에서는 끌어서 위치 이동) */
class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    val zoomed: Boolean get() = scale > 1.01f
    fun reset() { scale = 1f; offset = Offset.Zero }
}

/**
 * 두 손가락 꼬집기로 확대·축소, 확대 중에는 한 손가락으로 이동.
 * 부모 단계(Initial)에서 먼저 받아 처리하므로, 꼬집는 동안 아래의 탭(컨트롤 표시)·아래로 끌기(미니 플레이어)가 같이 동작하지 않음.
 * 확대하지 않은 상태의 한 손가락 동작은 건드리지 않아 기존 탭·끌기는 그대로.
 */
fun Modifier.pinchZoom(state: ZoomState): Modifier = pointerInput(state) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val fingers = event.changes.count { it.pressed }
            if (fingers >= 2 || state.zoomed) {
                val zoom = if (fingers >= 2) event.calculateZoom() else 1f
                val pan = event.calculatePan()
                val newScale = (state.scale * zoom).coerceIn(1f, 4f)
                // 확대된 만큼만 움직일 수 있게 범위 제한
                val maxX = (newScale - 1f) * size.width / 2f
                val maxY = (newScale - 1f) * size.height / 2f
                val o = state.offset + pan
                state.scale = newScale
                state.offset = Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                if (fingers >= 2 || pan != Offset.Zero) event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        // 거의 원래 크기로 줄였으면 정확히 원래대로
        if (state.scale < 1.05f) state.reset()
    }
}

/** 확대 상태를 실제 영상에 적용 */
fun Modifier.zoomed(state: ZoomState?): Modifier =
    if (state == null) this else graphicsLayer {
        scaleX = state.scale; scaleY = state.scale
        translationX = state.offset.x; translationY = state.offset.y
    }
