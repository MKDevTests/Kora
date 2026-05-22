package snd.komelia.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Fired by the small refresh icon in the widget header. Re-poking the
 * widget instance is enough — `NextBookWidget.provideGlance` does the
 * actual refetch + cache rewrite on every update.
 */
class RefreshNextBookWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        NextBookWidget().update(context, glanceId)
    }
}
