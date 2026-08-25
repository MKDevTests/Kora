package snd.komelia.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import snd.komelia.perf.PerfSection

/**
 * Wraps a subtree so a system trace shows how long it takes to measure and how
 * long it takes to record its draw commands, as two separate spans.
 *
 * The split matters. Home's first frame spends 1804-2087 ms inside a single
 * `Record View#draw()` slice, and Compose publishes nothing underneath it. That
 * one number cannot distinguish "laying out three rows of cards is slow" from
 * "painting them is slow", and those have opposite fixes.
 *
 * Composition is not traced here, and the first capture made with these spans
 * showed why that is not the same as composition being cheap. The recompose
 * phase costs 35 ms against 1041 ms for traversal, which looks conclusive and
 * is not: a lazy layout subcomposes its items *inside its own measure pass*, so
 * 517 of the 979 ms spent in measureAndLayout turned out to be Compose
 * recomposing and applying card content. The framework's own `Compose:recompose`
 * spans show it, nested under these ones. Read the two together.
 *
 * **Do not put this on a lazy item.** It returns fresh `layout`/`drawWithContent`
 * lambdas on every call, so the Modifier it produces never compares equal to the
 * previous one and the composable receiving it can never be skipped. Placed on
 * the home cards it made all 20 of them recompose on a state change they were
 * meant to ignore, and the resulting 59 ms looked exactly like an app defect —
 * it was this file. Checking that the instrumentation left the total runtime
 * alone (1934 ms against 1916 ms) did not catch it: it does not change how long
 * the work takes, it changes what Compose skips. Trace containers, not items.
 *
 * **Free when nothing is being traced.** With no capture running this returns
 * the receiver untouched — no layout node, no draw wrapper, no allocation.
 * That is not an optimisation, it is the condition for this being shippable at
 * all: adding a layout node per card would change the very cost being measured,
 * and would go on changing it for every user forever after. The flag is read
 * when the Modifier is built, so a trace must be started before the screen
 * composes.
 */
fun Modifier.traceLayout(name: String): Modifier {
    if (!PerfSection.enabled) return this
    return this
        .layout { measurable, constraints ->
            PerfSection.begin("$name.measure")
            val placeable = try {
                measurable.measure(constraints)
            } finally {
                PerfSection.end()
            }
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
        .drawWithContent {
            PerfSection.begin("$name.draw")
            try {
                drawContent()
            } finally {
                PerfSection.end()
            }
        }
}
