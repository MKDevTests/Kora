package snd.komelia.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Fired by the small refresh icon in the widget header. The update alone is no
 * longer enough now that `NextBookWidget.provideGlance` honours a TTL — an
 * explicit tap has to mean "go and look", so it clears the marker first.
 */
class RefreshNextBookWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WidgetCache(context).markStale()
        NextBookWidget().update(context, glanceId)
    }
}
