package snd.komelia.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.oshai.kotlinlogging.KotlinLogging
import snd.komelia.stats.BookCompletionEvents

private val logger = KotlinLogging.logger { }

/**
 * Pokes the "Next book up" widget on three triggers:
 *  1. [BookCompletionEvents] (book just marked completed) — via [start].
 *  2. App moves to background — via [refreshNow] from a
 *     ProcessLifecycleOwner ON_STOP observer in [snd.komelia.App].
 *  3. Manual refresh button on the widget itself — via [refreshNow] from
 *     `RefreshNextBookWidgetAction`.
 *
 * `provideGlance` in [NextBookWidget] is what actually re-fetches and
 * re-renders; this class just tells the system "redraw all instances".
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
        // Initial poke so the widget reflects the freshly-loaded dependency
        // graph as soon as the app starts (the user may not finish a book
        // before backgrounding, so onStop alone wouldn't cover this).
        refreshNow()
        events.events.collect {
            refreshNow()
        }
    }

    suspend fun refreshNow() {
        try {
            val widget = NextBookWidget()
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(NextBookWidget::class.java).forEach { id ->
                widget.update(context, id)
            }
        } catch (t: Throwable) {
            logger.warn(t) { "Widget update failed" }
        }
    }

    companion object {
        /**
         * Refresh all "Next book up" widget instances from anywhere that
         * has a [Context] (e.g. a [androidx.glance.appwidget.action.ActionCallback]
         * fired by the refresh button, or a Lifecycle observer). Static
         * because the caller doesn't need the events bus.
         */
        suspend fun refreshAll(context: Context) {
            try {
                val widget = NextBookWidget()
                val manager = GlanceAppWidgetManager(context)
                manager.getGlanceIds(NextBookWidget::class.java).forEach { id ->
                    widget.update(context, id)
                }
            } catch (t: Throwable) {
                logger.warn(t) { "Widget update failed" }
            }
        }
    }
}
