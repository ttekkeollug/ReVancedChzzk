package com.seolha.rechzzk.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.seolha.rechzzk.Prefs
import com.seolha.rechzzk.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 빠른 방송 알림: 상시 알림 없이 1분마다 잠깐 깨어나 팔로우 채널을 확인하고 다음 알람을 예약.
 * (포그라운드 서비스는 상단 알림을 계속 띄워야 해서 정확한 알람 방식으로 대체)
 *
 * 화면이 켜져 있거나 휴대폰을 쓰는 중이면 1분 간격으로 동작하고,
 * 오래 놓아둔 절전(Doze) 상태에서는 안드로이드가 알람 간격을 최대 약 9분까지 늘림.
 * 배터리 최적화에서 제외하면 절전 중에도 네트워크를 쓸 수 있어 알림이 덜 늦음.
 */
object FastAlerts {
    private const val INTERVAL_MS = 60_000L
    private const val REQ = 7101

    private fun pending(ctx: Context, create: Boolean): PendingIntent? {
        val flags = PendingIntent.FLAG_IMMUTABLE or (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(ctx, REQ, Intent(ctx, AlertAlarmReceiver::class.java), flags)
    }

    fun enabled(): Boolean = Prefs.liveAlerts.value && Prefs.fastAlerts.value && Http.isLoggedInCookie()

    /** 설정에 맞춰 알람 예약/취소 */
    fun sync(ctx: Context) {
        // 이전 버전(포그라운드 서비스)의 상시 알림 채널 정리
        runCatching { ctx.getSystemService(android.app.NotificationManager::class.java).deleteNotificationChannel("alert_status") }
        if (enabled()) schedule(ctx, 3_000) else cancel(ctx)
    }

    fun schedule(ctx: Context, delayMs: Long = INTERVAL_MS) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, true) ?: return
        val at = SystemClock.elapsedRealtime() + delayMs
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
    }

    fun cancel(ctx: Context) {
        pending(ctx, false)?.let { ctx.getSystemService(AlarmManager::class.java).cancel(it) }
    }
}

/** 알람이 울리면 한 번 확인하고 다음 알람을 예약 */
class AlertAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (!FastAlerts.enabled()) return
        val result = goAsync()   // 리시버는 약 10초 안에 끝나야 하므로 9초 제한
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9_000) { runCatching { LiveAlerts.check(app) } }
            } finally {
                FastAlerts.schedule(app)
                result.finish()
            }
        }
    }
}

/** 재부팅·앱 업데이트 후 알림 다시 예약 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            LiveAlerts.setup(context)
        }
    }
}
