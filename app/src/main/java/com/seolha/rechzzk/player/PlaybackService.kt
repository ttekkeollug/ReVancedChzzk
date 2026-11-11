package com.seolha.rechzzk.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.seolha.rechzzk.MainActivity

/** 백그라운드/라디오 재생과 잠금화면·알림 컨트롤을 담당하는 서비스 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, PlayerHolder.player).setSessionActivity(intent).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 최근 앱에서 스와이프로 닫으면 재생도 종료
        PlayerHolder.player.pause()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()   // 플레이어는 앱 전역 싱글톤이라 해제하지 않음
        session = null
        super.onDestroy()
    }
}
