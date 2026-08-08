package com.fardeenkhan.moodtune.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.fardeenkhan.moodtune.app.di.appModule
import com.fardeenkhan.moodtune.app.widget.WidgetMediaConnection
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MoodTuneApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@MoodTuneApp)
            modules(appModule)
        }

        // The widget has no periodic update (updatePeriodMillis=0, push-only), and its own
        // hooks (onEnabled/provideGlance) only fire when the widget itself redraws - which
        // never happens on its own if it's already sitting on the home screen from a previous
        // process. Application.onCreate() runs before any component in this process (Activity,
        // service, or the widget's receiver/action callback), so connecting here guarantees the
        // widget starts receiving live playback updates as soon as the process exists.
        WidgetMediaConnection.ensureConnected(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(AlbumArtFetcher.Factory(context))
            }
            .build()
    }
}
