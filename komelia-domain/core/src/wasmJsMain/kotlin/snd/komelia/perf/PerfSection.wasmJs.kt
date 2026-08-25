package snd.komelia.perf

/** No system trace to write into off Android; every span is a no-op. */
actual object PerfSection {
    actual val enabled: Boolean get() = false
    actual fun begin(name: String) {}
    actual fun end() {}
}
