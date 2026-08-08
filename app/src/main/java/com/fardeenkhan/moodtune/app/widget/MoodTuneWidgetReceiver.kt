package com.fardeenkhan.moodtune.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class MoodTuneWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoodTuneWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        android.util.Log.d("MoodTuneWidgetReceiver", "onEnabled")
        // First widget instance was just added - connect right away rather than waiting for
        // the first provideGlance() render or button tap.
        WidgetMediaConnection.ensureConnected(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        android.util.Log.d("MoodTuneWidgetReceiver", "onDisabled")
        // Last widget instance was removed from the home screen - release the MediaController
        // instead of leaving it connected with nothing left to update.
        WidgetMediaConnection.release()
    }
}
