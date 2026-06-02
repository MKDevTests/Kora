package snd.komelia.links

import snd.komga.client.common.KomgaWebLink
import snd.komga.client.series.KomgaSeriesId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KoraLinkCodecTest {

    @Test
    fun roundTrip() {
        for (type in SeriesRelationType.entries) {
            val link = KoraLinkCodec.relationLink(KomgaSeriesId("0ABC"), type)
            assertTrue(KoraLinkCodec.isKoraLink(link), "should be a Kora link for $type")
            val parsed = KoraLinkCodec.parse(link)
            assertEquals(KomgaSeriesId("0ABC"), parsed?.target, "target for $type")
            assertEquals(type, parsed?.type, "type for $type")
        }
    }

    @Test
    fun urlIsValidAndHostIndependent() {
        val link = KoraLinkCodec.relationLink(KomgaSeriesId("X1"), SeriesRelationType.SEQUEL)
        assertEquals("https://kora.invalid/series/X1?kora=sequel", link.url)
    }

    @Test
    fun parsesRegardlessOfHost() {
        // Different host/proxy than the one that wrote it — still resolves by path.
        val link = KomgaWebLink("Sequel", "http://other:25600/series/ZZ9?kora=sequel")
        val parsed = KoraLinkCodec.parse(link)
        assertEquals(KomgaSeriesId("ZZ9"), parsed?.target)
        assertEquals(SeriesRelationType.SEQUEL, parsed?.type)
    }

    @Test
    fun ignoresNonKoraLinks() {
        assertFalse(KoraLinkCodec.isKoraLink(KomgaWebLink("AniList", "https://anilist.co/manga/30002")))
        assertNull(KoraLinkCodec.parse(KomgaWebLink("Official", "https://example.org/series/1")))
        assertNull(KoraLinkCodec.parse(KomgaWebLink("Bad", "https://host/series/1?kora=bogus")))
    }
}
