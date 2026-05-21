package snd.komelia.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.oshai.kotlinlogging.KotlinLogging
import snd.komelia.stats.BookCompletionEvents

private val logger = KotlinLogging.logger { }

/**
 * Subscribes to [BookCompletionEvents] and asks the system to redraw
 * the "Next book up" widget every time a book is marked completed.
 * Provides instant feedback: finish a chapter inside the app, background
 * to launcher, and the widget already reflects the new next book up.
 *
 * Suspending — meant to be launched from [snd.komelia.App.onCreate] in a
 * coroutine that lives for the app lifetime. Also restarts itself if
 * the dependency graph is replaced (server switch).
 */
class WidgetRefresher(
    private val context: Context,
    private val events: BookCompletionEvents,
) {
    suspend fun start() {
        val widget = NextBookWidget()
        val manager = GlanceAppWidgetManager(context)
        events.events.collect {
            try {
                manager.getGlanceIds(NextBookWidget::class.java).forEach { id ->
                    widget.update(context, id)
                }
            } catch (t: Throwable) {
                logger.warn(t) { "Widget update failed" }
            }
        }
    }
}
