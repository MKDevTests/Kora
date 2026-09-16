package snd.komelia.ui.settings.appearance

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import snd.komelia.settings.model.AppIcon

private const val ALIAS_PREFIX = "snd.komelia.icon."

private fun AppIcon.aliasName(): String = ALIAS_PREFIX + when (this) {
    AppIcon.DEFAULT -> "Default"
    AppIcon.EMBER -> "Ember"
    AppIcon.FOREST -> "Forest"
    AppIcon.MONO -> "Mono"
}

/**
 * The switch is deferred to ON_STOP on purpose. The launcher starts the
 * app through the alias, so the alias is the root of the task, and
 * disabling it makes Android remove the whole task on the spot
 * (`ActivityTaskSupervisor.removeTask`, measured on One UI 6): applied
 * immediately, picking an icon threw the user out of the app. Applied once
 * the app is in the background, the task goes away unseen and the next
 * tap on the (new) icon starts it again, process still warm.
 */
@Composable
actual fun rememberAppIconSwitcher(): (AppIcon) -> Unit {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    var pending by remember { mutableStateOf<AppIcon?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                pending?.let { icon ->
                    pending = null
                    applyAppIcon(context, icon)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(context) {
        { icon: AppIcon -> if (currentAppIcon(context) != icon) pending = icon }
    }
}

private fun componentOf(context: Context, icon: AppIcon) =
    ComponentName(context.packageName, icon.aliasName())

/** The alias the launcher currently sees, from the package manager's own state. */
private fun currentAppIcon(context: Context): AppIcon? {
    val pm = context.packageManager
    return AppIcon.entries.firstOrNull { icon ->
        when (pm.getComponentEnabledSetting(componentOf(context, icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            // The manifest default: only the Default alias is enabled there.
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.DEFAULT
            else -> false
        }
    }
}

private fun applyAppIcon(context: Context, icon: AppIcon) {
    val pm = context.packageManager
    // Enable the new alias first so the app never has zero launcher
    // entries, not even for the instant between the two calls.
    fun set(target: AppIcon, enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val component = componentOf(context, target)
        if (pm.getComponentEnabledSetting(component) != state) {
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
    runCatching {
        set(icon, true)
        AppIcon.entries.filter { it != icon }.forEach { set(it, false) }
    }
}
