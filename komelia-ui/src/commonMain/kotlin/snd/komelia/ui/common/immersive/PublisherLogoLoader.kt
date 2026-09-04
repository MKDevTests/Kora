package snd.komelia.ui.common.immersive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

fun normalizePublisherName(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

// Komga returns whatever the metadata says, and the bundled pack is named after
// the publisher's plain name. Measured against a 7232-series library, the exact
// key alone matched 58% of them; these fallbacks bring it to 66%. Each rule below
// is here because it was worth series, not because it sounded plausible:
//
//   "Image - Top Cow", "Glénat / Fayard"   -> split, 1st segment      (+9 pub.)
//   "Ablaze, LLC.", "Markosia Ltd"          -> drop the legal suffix   (+13 pub.)
//   "Kodansha Comics", "Kodansha USA"       -> drop the descriptor     (+9 pub.)
//   "Pika" -> pika_dition, "Seven Seas"     -> unique extension        (+429 series)
private val splitAt = Regex("""\s*(?:[;/:(]|\s-\s)""")
private val legalSuffix =
    Regex("""\s*,?\s*\b(?:llc|inc|ltd|ltda|s\.?a\.?|sas|gmbh|corp|company|publications?|enterprises?)\b\.?$""",
        RegexOption.IGNORE_CASE)
private val editionsPrefix = Regex("""^(?:les_)?(?:editions?|ditions?)_""")
private val descriptorSuffix = Regex(
    """_(?:comics?|manga|books?|publishing|editions?|ditions?|entertainment|studios?|press|group|media|shoten|usa|global)$"""
)

/** "Éditions Glénat" -> "Editions Glenat", so the accent cannot eat the letter. */
private fun deaccent(name: String): String {
    val sb = StringBuilder(name.length)
    for (c in name) {
        sb.append(
            when (c) {
                'à', 'á', 'â', 'ä', 'ã', 'å' -> 'a'
                'ç' -> 'c'
                'è', 'é', 'ê', 'ë' -> 'e'
                'ì', 'í', 'î', 'ï' -> 'i'
                'ñ' -> 'n'
                'ò', 'ó', 'ô', 'ö', 'õ', 'ō' -> 'o'
                'ù', 'ú', 'û', 'ü' -> 'u'
                'ý', 'ÿ' -> 'y'
                'À', 'Á', 'Â', 'Ä', 'Ã', 'Å' -> 'A'
                'Ç' -> 'C'
                'È', 'É', 'Ê', 'Ë' -> 'E'
                'Ì', 'Í', 'Î', 'Ï' -> 'I'
                'Ñ' -> 'N'
                'Ò', 'Ó', 'Ô', 'Ö', 'Õ', 'Ō' -> 'O'
                'Ù', 'Ú', 'Û', 'Ü' -> 'U'
                'Ý' -> 'Y'
                else -> c
            }
        )
    }
    return sb.toString()
}

/**
 * The key of the bundled logo for [publisher], or null when the pack has none.
 * [index] is the set of file names actually shipped.
 */
fun resolvePublisherLogoKey(publisher: String, index: Set<String>): String? {
    fun hit(candidate: String): String? =
        normalizePublisherName(candidate).takeIf { it.isNotEmpty() && it in index }

    hit(publisher)?.let { return it }
    hit(deaccent(publisher))?.let { return it }

    // A few series carry a JSON array of imprints instead of one name.
    val trimmed = publisher.trim()
    if (trimmed.startsWith("[")) {
        for (part in trimmed.trim('[', ']').split(',')) {
            hit(deaccent(part.trim().trim('"')))?.let { return it }
        }
    }

    val head = deaccent(publisher).split(splitAt).first().trim()
    hit(head)?.let { return it }

    val stripped = head.replace(legalSuffix, "")
    hit(stripped)?.let { return it }

    val key = normalizePublisherName(stripped)
    if (key.isEmpty()) return null
    key.replace(editionsPrefix, "").takeIf { it in index }?.let { return it }
    key.replace(descriptorSuffix, "").takeIf { it in index }?.let { return it }

    // Last resort, and only when there is no ambiguity: exactly one shipped file
    // extends this key ("pika" -> "pika_dition"), or this key extends exactly one
    // ("ubisoft_entertainment_s_a" -> "ubisoft").
    index.singleOrNull { it.startsWith("${key}_") }?.let { return it }
    index.singleOrNull { key.startsWith("${it}_") && it.length > 3 }?.let { return it }
    return null
}

/**
 * A bundled logo plus whether the dark hero badge has to tint it white.
 *
 * 126 of the 884 logos are near-black line art on a transparent ground. The
 * badge draws them on a black pill at 60% opacity, so they were invisible --
 * indistinguishable from a publisher with no logo at all. Coloured and opaque
 * art is not tinted: a red disc reads fine on black, and tinting would flatten
 * it into a white blob. The decision is made when the pack is built
 * (scripts/optimize-publisher-logos.py) so nothing has to read pixels here.
 */
data class PublisherLogo(
    val image: ImageBitmap,
    val tintOnDarkBackground: Boolean,
)

private const val LOGO_CACHE_SIZE = 24

private val logoCache = LinkedHashMap<String, PublisherLogo?>()
private val logoCacheMutex = Mutex()

private var packIndex: Set<String>? = null
private var tintList: Set<String>? = null
private val sidecarMutex = Mutex()

@OptIn(ExperimentalResourceApi::class)
private suspend fun readKeys(name: String): Set<String> = runCatching {
    Res.readBytes("files/publishers/$name")
        .decodeToString().lineSequence()
        .filter { it.isNotBlank() }.toSet()
}.getOrElse { emptySet() }

private suspend fun packIndex(): Set<String> {
    packIndex?.let { return it }
    return sidecarMutex.withLock {
        packIndex ?: readKeys("_index.txt").also { packIndex = it }
    }
}

private suspend fun tintList(): Set<String> {
    tintList?.let { return it }
    return sidecarMutex.withLock {
        tintList ?: readKeys("_tint.txt").also { tintList = it }
    }
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadPublisherLogo(publisher: String): PublisherLogo? {
    logoCacheMutex.withLock {
        if (logoCache.containsKey(publisher)) {
            // Re-insert so eviction is least-recently-used, not insertion order.
            val cached = logoCache.remove(publisher)
            logoCache[publisher] = cached
            return cached
        }
    }

    val key = resolvePublisherLogoKey(publisher, packIndex())
    val logo = key?.let {
        runCatching {
            PublisherLogo(
                image = Res.readBytes("files/publishers/$it.png").decodeToImageBitmap(),
                tintOnDarkBackground = it in tintList(),
            )
        }.getOrNull()
    }

    logoCacheMutex.withLock {
        logoCache[publisher] = logo
        while (logoCache.size > LOGO_CACHE_SIZE) {
            logoCache.remove(logoCache.keys.first())
        }
    }
    return logo
}

@Composable
fun rememberPublisherLogo(publisher: String?): PublisherLogo? {
    var bitmap by remember(publisher) { mutableStateOf<PublisherLogo?>(null) }
    if (!publisher.isNullOrBlank()) {
        LaunchedEffect(publisher) { bitmap = loadPublisherLogo(publisher) }
    }
    return bitmap
}
