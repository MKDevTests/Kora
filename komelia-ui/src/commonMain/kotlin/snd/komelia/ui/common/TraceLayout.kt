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
 * Composition is deliberately not traced here: it happens in an earlier frame
 * phase, and the atrace capture already showed that phase costing 31.9 ms
 * against 976.7 ms for traversal. Whatever is expensive is not composition.
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
