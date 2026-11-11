package com.seolha.rechzzk.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.seolha.rechzzk.MainActivity
import com.seolha.rechzzk.Prefs
import com.seolha.rechzzk.R
import com.seolha.rechzzk.net.Api
import com.seolha.rechzzk.net.FollowingItem
import com.seolha.rechzzk.net.Http
import com.seolha.rechzzk.net.thumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 팔로우 채널 방송 시작 알림.
 * 공식 앱은 네이버 서버 푸시를 받지만 비공식 앱은 그 푸시를 받을 수 없어서
 * 안드로이드가 허용하는 최소 주기(15분)로 팔로잉 라이브 목록을 확인하고, 앱을 열 때도 즉시 확인함.
 * 공식 앱처럼 채널 알림(종)을 켠 채널만 알리며, 설정에서 "모든 팔로우 채널"로 바꿀 수 있음.
 */
object LiveAlerts {
    const val CHANNEL_ID = "live_start"
    private const val WORK = "live_check"
    private const val KEY_SEEN = "seenLives"
    private const val KEY_INIT = "seenInit2"
    private const val KEY_LAST = "lastCheck"

    fun setup(ctx: Context) {
        FastAlerts.sync(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("alert_status")   // 이전 버전의 '방송 알림 확인 중' 상시 알림 채널 정리
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "방송 시작 알림", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "팔로우한 채널이 방송을 시작하면 알려줍니다" }
        )
        schedule(ctx)
    }

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!Prefs.liveAlerts.value) { wm.cancelUniqueWork(WORK); return }
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LiveCheckWorker>(15, TimeUnit.MINUTES).setConstraints(net).build())
    }

    /** 앱을 열었을 때 바로 한 번 확인 */
    fun checkNow(ctx: Context) {
        if (!Prefs.liveAlerts.value) return
        WorkManager.getInstance(ctx).enqueueUniqueWork("${WORK}_now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<LiveCheckWorker>().build())
    }

    private val lock = Mutex()

    /**
     * 방송 중인 팔로우 채널 목록을 직전 확인 결과와 비교해 새로 켜진 채널만 알림.
     * 채널 ID로 비교하므로 방송 제목이 바뀌어도 다시 알리지 않고, 방송을 껐다 다시 켜면 새로 알림.
     */
    suspend fun check(ctx: Context) = lock.withLock {
        if (!Prefs.liveAlerts.value || !Http.isLoggedInCookie()) return@withLock
        val lives = Api.followingLives().followingList.filter { it.liveInfo != null || it.streamer?.openLive == true }
        val sp = Prefs.raw
        val seen = sp.getStringSet(KEY_SEEN, emptySet())!!
        val current = lives.associateBy { it.channelId }
        val firstRun = !sp.getBoolean(KEY_INIT, false)
        if (!firstRun) {
            for (f in current.filterKeys { it !in seen }.values) {
                if (Prefs.alertAll.value || bellOn(f)) notify(ctx, f)
            }
        }
        sp.edit().putStringSet(KEY_SEEN, current.keys).putBoolean(KEY_INIT, true)
            .putLong(KEY_LAST, System.currentTimeMillis()).apply()
    }

    /**
     * 채널 알림(종)이 켜져 있는지. 목록 응답에 값이 있으면 그대로 쓰고,
     * 없으면 해당 채널의 팔로우 상태를 직접 조회해서 확인(새로 방송을 켠 채널만 조회하므로 요청이 적음).
     */
    private suspend fun bellOn(f: FollowingItem): Boolean {
        f.channel?.personalData?.following?.notification?.let { return it }
        return runCatching { Api.followState(f.channelId)?.notification }.getOrNull() == true
    }

    /** 마지막으로 확인한 시각(설정 화면 표시용) */
    fun lastChecked(): Long = Prefs.raw.getLong(KEY_LAST, 0)

    /** 알림이 실제로 뜨는지 확인하는 테스트 */
    fun testNotify(ctx: Context): String? {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return "알림 권한이 꺼져 있습니다. 설정에서 알림을 허용해 주세요."
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return "앱 알림이 꺼져 있습니다."
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("ReChzzk 알림 테스트")
            .setContentText("팔로우 채널이 방송을 켜면 이렇게 알려 드립니다")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(1, n)
        return null
    }

    private suspend fun notify(ctx: Context, f: FollowingItem) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val name = f.channel?.channelName ?: "팔로우 채널"
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("https://chzzk.naver.com/live/${f.channelId}"))
            .setClass(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, f.channelId.hashCode(), open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val avatar = loadBitmap(f.channel?.channelImageUrl)
        val preview = loadBitmap(thumb(f.liveInfo?.defaultThumbnailImageUrl ?: f.liveInfo?.liveImageUrl, null, 480))
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("$name 님이 방송을 시작했습니다")
            .setContentText(f.liveInfo?.liveTitle ?: "")
            .setLargeIcon(avatar)
            .apply { if (preview != null) setStyle(NotificationCompat.BigPictureStyle().bigPicture(preview).bigLargeIcon(null as android.graphics.Bitmap?)) }
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(f.channelId.hashCode(), n) }
    }

    private suspend fun loadBitmap(url: String?) = if (url.isNullOrBlank()) null else withContext(Dispatchers.IO) {
        runCatching {
            Http.mediaClient.newCall(Request.Builder().url(url).build()).execute().use {
                it.body?.bytes()?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size) }
            }
        }.getOrNull()
    }
}

class LiveCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result =
        runCatching { LiveAlerts.check(applicationContext) }.fold({ Result.success() }, { Result.retry() })
}
