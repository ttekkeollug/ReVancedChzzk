package com.seolha.rechzzk

import com.seolha.rechzzk.net.Api
import com.seolha.rechzzk.net.Http
import com.seolha.rechzzk.net.thumb
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Test

class ThumbTest {
    @Test fun thumbs() = runBlocking {
        val l = Api.lives().data.take(3)
        for (x in l) {
            val u = thumb(x.liveImageUrl, x.defaultThumbnailImageUrl, 480) ?: continue
            val r = Http.mediaClient.newCall(Request.Builder().url(u).build()).execute()
            println("THUMB ${r.code} ${r.header("Content-Type")} ${r.body?.bytes()?.size} protocol=${r.protocol} $u")
        }
    }
}
