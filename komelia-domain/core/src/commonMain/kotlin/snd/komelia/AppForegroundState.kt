package snd.komelia

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the app is currently in front of the user.
 *
 * Android publishes this from ProcessLifecycleOwner; the desktop and web
 * targets never touch it and therefore stay on the [true] default, which is
 * the behaviour they had before this existed.
 *
 * It defaults to true rather than false on purpose: a process that comes up
 * without an Activity (a widget update, a WorkManager job) is short-lived, and
 * defaulting to "background" would mean a normal launch runs without live
 * events until the first onStart lands.
 */
object AppForegroundState {
    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground

    fun setForeground(foreground: Boolean) {
        _isForeground.value = foreground
    }
}
