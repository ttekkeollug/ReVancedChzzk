package com.seolha.rechzzk

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.seolha.rechzzk.net.Http
import com.seolha.rechzzk.player.PlayerHolder

class App : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        PlayerHolder.init(this)
        Auth.init()
        com.seolha.rechzzk.notify.LiveAlerts.setup(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { Http.mediaClient }))
                // 움직이는 이모티콘·배지: Android 9 이상은 시스템 디코더(GIF·애니메이션 WebP), 그 아래는 GIF 디코더
                if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
