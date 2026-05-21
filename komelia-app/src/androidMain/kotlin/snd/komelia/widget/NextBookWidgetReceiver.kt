package snd.komelia.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Receiver registered in the manifest. Forwards system AppWidget intents
 * (update, enabled, deleted, etc.) to the [NextBookWidget] Glance impl.
 */
class NextBookWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextBookWidget()
}
