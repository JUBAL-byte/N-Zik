package app.n_zik.android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PlaylistWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        refreshIdleWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        refreshIdleWidget(context, appWidgetId, newOptions)
    }

    private fun refreshIdleWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(NzikDispatchers.UI).launch {
            try {
                appWidgetIds.forEach { appWidgetId ->
                    PlaylistWidgetManager.updateIdleWidget(
                        context = context,
                        appWidgetId = appWidgetId,
                        options = appWidgetManager.getAppWidgetOptions(appWidgetId),
                    )
                }
            } catch (e: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun refreshIdleWidget(context: Context, appWidgetId: Int, options: Bundle) {
        val pendingResult = goAsync()
        CoroutineScope(NzikDispatchers.UI).launch {
            try {
                PlaylistWidgetManager.updateIdleWidget(context, appWidgetId, options)
            } catch (e: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val TARGET_TYPE_LOCAL = "local"
        const val TARGET_TYPE_ONLINE = "online"
        const val TARGET_TYPE_LIKED = "liked"
        const val TARGET_TYPE_DOWNLOADED = "downloaded"
        const val TARGET_TYPE_TOP = "top"
    }
}
