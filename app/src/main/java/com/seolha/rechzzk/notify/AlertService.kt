package com.seolha.rechzzk.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.seolha.rechzzk.MainActivity
import com.seolha.rechzzk.Prefs
import com.seolha.rechzzk.R
import com.seolha.rechzzk.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 빠른 방송 알림: 앱이 꺼져 있어도 1분마다 팔로우 채널 방송 시작을 확인.
 * 안드로이드는 백그라운드 작업을 15분 이상 간격으로 미루기 때문에(삼성 기기는 더 심함),
 * 공식 앱에 가까운 속도를 내려면 상단에 작은 상시 알림을 띄우는 포그라운드 서비스가 필요함.
 */
class AlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        val started = runCatching { ServiceCompat.startForeground(this, NOTI_ID, ongoing(), type) }.isSuccess
        if (!started) { stopSelf(); return START_NOT_STICKY }
        if (!looping) {
            looping = true
            scope.launch {
                while (isActive) {
                    if (!Prefs.liveAlerts.value || !Prefs.fastAlerts.value) { stopSelf(); break }
                    runCatching { LiveAlerts.check(applicationContext) }
                    delay(INTERVAL_MS)
                }
            }
        }
        return START_STICKY
    }

    private var looping = false

    private fun ongoing(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(STATUS_CHANNEL, "알림 확인 중 표시", NotificationManager.IMPORTANCE_MIN)
            .apply { description = "빠른 방송 알림을 위해 필요한 상시 알림입니다. 이 채널을 꺼서 숨길 수 있습니다." ; setShowBadge(false) })
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("방송 알림 확인 중")
            .setContentText("팔로우 채널이 방송을 켜면 1분 안에 알려 드립니다")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true).setSilent(true).setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTI_ID = 7001
        private const val STATUS_CHANNEL = "alert_status"
        private const val INTERVAL_MS = 60_000L

        /** 설정에 맞춰 서비스 시작/정지 */
        fun sync(ctx: Context) {
            val want = Prefs.liveAlerts.value && Prefs.fastAlerts.value && Http.isLoggedInCookie()
            val intent = Intent(ctx, AlertService::class.java)
            if (want) runCatching { ContextCompat.startForegroundService(ctx, intent) }
            else ctx.stopService(intent)
        }
    }
}

/** 재부팅 후 빠른 알림 다시 시작 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            LiveAlerts.setup(context)
        }
    }
}
