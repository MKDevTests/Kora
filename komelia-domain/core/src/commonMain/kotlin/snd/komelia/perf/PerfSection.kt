package snd.komelia.perf

/**
 * Named spans written into the platform's system trace, for the work that
 * happens too deep inside the UI toolkit for [PerfTrace] to see.
 *
 * [PerfTrace] measures whole operations the user waits on, and logs them. This
 * measures slices of a single frame, and logs nothing — it writes into the same
 * stream `atrace` already collects, so a capture shows these spans nested
 * inside Choreographer, traversal and draw.
 *
 * Why it exists: the first frame of Home was measured at 1804-2087 ms with
 * every millisecond inside one `Record View#draw()` slice and no detail below
 * it, because Compose emits no trace spans of its own. atrace could say when
 * the frame was slow but never where.
 *
 * [enabled] is the whole point of the design. When no trace is being captured
 * these spans must cost exactly nothing — an instrument that perturbs what it
 * measures is worse than no instrument, and a layout node added per card would
 * do precisely that. Callers are expected to check it and skip the wrapping
 * entirely, not merely to skip the span.
 */
expect object PerfSection {
    /** True only while a system trace is actually being captured. */
    val enabled: Boolean

    fun begin(name: String)

    fun end()
}
