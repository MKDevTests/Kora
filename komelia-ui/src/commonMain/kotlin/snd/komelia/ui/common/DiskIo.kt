package snd.komelia.ui.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a disk-cache **write** away from the caller's dispatcher.
 *
 * Every persisted cache in this module is reached from a ViewModel or a
 * ScreenModel, and those run their coroutines on Main. Nothing in those files
 * said so, so the file work happened there.
 *
 * **Writes only, and that limit was measured rather than assumed.** The first
 * version of this moved the reads too, and it made things worse. The home shelf
 * snapshot is 328KB of JSON: read on Main it took 203ms, and moved to
 * [Dispatchers.Default] it took 451-538ms across four cold starts, because at
 * startup that pool is already saturated by composition. The first shelf reached
 * the screen at 4962ms instead of 4808ms.
 *
 * The reason is structural, not a tuning problem. Nothing else can use the main
 * thread while a snapshot is being read, because the only thing waiting on that
 * thread is the painting of the very data being read. Moving the read off Main
 * buys no parallelism and adds a dispatch plus pool contention. Writes are the
 * opposite: no frame waits on them, and they land exactly when the user starts
 * scrolling a freshly painted screen.
 *
 * [Dispatchers.Default] rather than `Dispatchers.IO`: the work is a short write
 * behind a comparatively long serialize, and IO does not exist on the wasmJs
 * target this module also builds for.
 *
 * None of the wrapped bodies touch shared state. Where a cache updates an
 * in-memory map (`BookPagesCache.memory`, `LibraryProgressCache.entries`), that
 * assignment deliberately stays on the caller's dispatcher: every caller reached
 * it from Main, which serialised it for free, and handing that guarantee away
 * for a field write would buy nothing.
 */
suspend fun <T> onDisk(block: suspend () -> T): T = withContext(Dispatchers.Default) { block() }
