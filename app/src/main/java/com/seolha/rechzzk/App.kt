package com.seolha.rechzzk

import android.app.Application
import coil3.ImageLoader
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
        Auth.refresh()
        com.seolha.rechzzk.notify.LiveAlerts.setup(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Http.mediaClient })) }
            .crossfade(true)
            .build()
}
