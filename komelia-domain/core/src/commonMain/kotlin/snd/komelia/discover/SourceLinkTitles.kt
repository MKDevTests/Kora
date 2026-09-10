package snd.komelia.discover

/**
 * Reads a romanised or English series title out of the tracker links Komga
 * already stores, without asking anybody anything.
 *
 * The point is that MangaUpdates indexes romaji and English titles, while these
 * shelves are named in French. A Nautiljon link says
 * `/mangas/arpeggio+of+blue+steel.html` and an Anime-Planet one
 * `/manga/fairy-tail-100-years-quest`: both slugs ARE the title in the form the
 * search wants, so turning one into a query costs no request and no scraping —
 * the URL alone carries it.
 *
 * Deliberately limited to slug-carrying trackers. AniList, MyAnimeList and
 * MangaDex links hold a numeric id and no title, so they say nothing here;
 * manga-news slugs repeat the French title Komga already has, which the plain
 * title path covers.
 */
fun trackerTitle(url: String): String? {
    val lower = url.lowercase()
    val host = SLUG_HOSTS.firstOrNull { lower.contains(it.marker) } ?: return null
    val start = lower.indexOf(host.marker) + host.marker.length
    if (start >= url.length) return null
    val slug = url.substring(start)
        .substringBefore('?')
        .substringBefore('#')
        .removeSuffix("/")
        .substringAfterLast('/')
        .removeSuffix(".html")
    if (slug.isBlank()) return null

    val decoded = percentDecode(slug)
        .replace('+', ' ')
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
    return decoded.ifBlank { null }
}

private class SlugHost(val marker: String)

private val SLUG_HOSTS = listOf(
    SlugHost("nautiljon.com/mangas/"),
    SlugHost("anime-planet.com/manga/"),
)

/**
 * Minimal percent decoding: these slugs are UTF-8 percent-encoded French, so
 * "404+d%C3%A9mons" has to come back as "404 démons". Bytes are gathered and
 * decoded together because one accented character spans two escapes.
 */
private fun percentDecode(value: String): String {
    if (!value.contains('%')) return value
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        val ch = value[index]
        if (ch == '%' && index + 2 < value.length) {
            val hex = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (hex != null) {
                bytes.add(hex.toByte())
                index += 3
                continue
            }
        }
        ch.toString().encodeToByteArray().forEach { bytes.add(it) }
        index++
    }
    return bytes.toByteArray().decodeToString()
}
