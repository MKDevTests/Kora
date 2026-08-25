package snd.komelia.perf

import android.os.Build
import android.os.Trace

actual object PerfSection {
    /**
     * `Trace.isEnabled` is API 29; the app's floor is 28, where the honest
     * answer is "cannot tell", and "no" is the answer that costs nothing.
     *
     * Read fresh on every call rather than cached: a capture can start at any
     * moment. Callers read it once when they build a Modifier, which means a
     * trace has to be running before the screen composes — which is what
     * starting atrace before launching the app already guarantees.
     */
    actual val enabled: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Trace.isEnabled()

    /** Names are truncated by the platform at 127 characters. Keep them short. */
    actual fun begin(name: String) = Trace.beginSection(name)

    actual fun end() = Trace.endSection()
}
