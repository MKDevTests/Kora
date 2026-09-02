@file:OptIn(InternalReadiumApi::class)

package com.storyteller.reader

import kotlinx.coroutines.runBlocking
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.indexOfFirstWithHref
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.positionsByReadingOrder
import org.readium.r2.shared.util.RelativeUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.asset.DefaultArchiveOpener
import org.readium.r2.shared.util.asset.DefaultFormatSniffer
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.data.Readable
import org.readium.r2.shared.util.data.decodeString
import org.readium.r2.shared.util.data.decodeXml
import org.readium.r2.shared.util.data.readDecodeOrNull
import org.readium.r2.shared.util.file.FileResourceFactory
import org.readium.r2.shared.util.fromEpubHref
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.toUri
import org.readium.r2.shared.util.xml.ElementNode
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser
import java.io.File
import java.net.URL
import java.util.zip.ZipFile

object BookService {

    /** Returns the resource data as an XML Document at the given [url], or null. */
    @OptIn(InternalReadiumApi::class)
    private suspend inline fun Container<Readable>.readDecodeXmlOrNull(
        url: Url,
    ): ElementNode? =
        readDecodeOrNull(url) { it.decodeXml() }

    private val retriever: AssetRetriever = AssetRetriever(
        FileResourceFactory(),
        DefaultArchiveOpener(), DefaultFormatSniffer()
    )

    @OptIn(InternalReadiumApi::class)
    private val opener: PublicationOpener =
        PublicationOpener(
            EpubParser()
        )

    private var publications: MutableMap<String, Publication> = mutableMapOf()

    fun extractArchive(archiveUrl: URL, extractedUrl: URL) {
        ZipFile(archiveUrl.path).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .forEach { entry ->
                    zip.getInputStream(entry).use { input ->
                        val newFile = File(extractedUrl.path, entry.name)
                        newFile.parentFile?.mkdirs()
                        newFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
        }
    }

    fun getPublication(bookUuid: String): Publication? {
        return publications[bookUuid]
    }

    fun getResource(bookUuid: String, link: Link): Resource? {
        val publication = getPublication(bookUuid)
            ?: throw Exception("Publication for book $bookUuid is unopened.")
        return publication.get(link)
    }

    suspend fun getPositions(bookUuid: String): List<Locator> {
        val publication = getPublication(bookUuid)
            ?: throw Exception("Publication for book $bookUuid is unopened.")
        val rt = Runtime.getRuntime()
        val before = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
        android.util.Log.i("epub3-diag", "getPositions START heap=${before}MB chapters=${publication.readingOrder.size}")
        val result = publication.positions()
        val after = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
        android.util.Log.i("epub3-diag", "getPositions END heap=${after}MB delta=${after - before}MB positions=${result.size}")
        return result
    }

    @OptIn(InternalReadiumApi::class)
    private suspend fun locateFromPositions(bookUuid: String, link: Link): Locator {
        val publication = getPublication(bookUuid)
            ?: throw Exception("Publication for book $bookUuid is unopened.")

        val readingOrderIndex = publication.readingOrder.indexOfFirstWithHref(link.url())
            ?: throw Exception("Could not find a locator for href ${link.href} in reading order for book $bookUuid")

        return publication.positionsByReadingOrder()[readingOrderIndex].first()
    }

    @OptIn(InternalReadiumApi::class)
    suspend fun buildFragmentLocator(bookUuid: String, href: Url, fragment: String): Locator {
        val publication = getPublication(bookUuid)
            ?: throw Exception("Publication for book $bookUuid is unopened.")

        val defaultLocator = Locator(
            href = href,
            mediaType = MediaType.XHTML
        )

        val link = publication.linkWithHref(href) ?: return defaultLocator

        val resource = publication.get(link) ?: return defaultLocator
        val htmlContent = resource.readDecodeOrNull { Try.success(it.decodeString()) }?.getOrNull()
            ?: return defaultLocator
        val fragmentRegex = Regex("id=\"${fragment}\"")
        val startOfFragment = fragmentRegex.find(htmlContent)?.range?.start ?: return defaultLocator
        val progression = startOfFragment.toDouble() / htmlContent.length.toDouble()
        val startOfChapterProgression =
            locateFromPositions(bookUuid, link).locations.totalProgression
                ?: return defaultLocator
        val chapterIndex = publication.readingOrder.indexOfFirstWithHref(link.url())
            ?: return defaultLocator
        val nextChapterIndex = chapterIndex + 1
        val startOfNextChapterProgression = nextChapterIndex.let {
            if (it == publication.readingOrder.size) {
                return@let 1.0
            } else {
                val nextChapterLink = publication.readingOrder[nextChapterIndex]
                return@let locateFromPositions(bookUuid, nextChapterLink).locations.totalProgression
            }
        } ?: return defaultLocator
        val totalProgression =
            startOfChapterProgression + (progression * (startOfNextChapterProgression - startOfChapterProgression))

        return Locator(
            href = href,
            mediaType = MediaType.XHTML,
            locations = Locator.Locations(
                fragments = listOf(fragment),
                progression = progression,
                totalProgression = totalProgression
            )
        )
    }

    fun locateLink(bookUuid: String, link: Link): Locator? {
        val publication = getPublication(bookUuid) ?: return null
        return publication.locatorFromLink(link)
    }

    suspend fun openPublication(bookUuid: String, url: URL): Publication {
        if (publications.contains(bookUuid)) {
            return publications[bookUuid]!!
        }

        val file = File(url.toURI())

        require(file.exists())

        val container =
            DirectoryContainer(file).getOrElse { throw Exception("Failed to open publication at $url: ${it.message}") }

        val asset = this.retriever.retrieve(container, MediaType.EPUB)
            .getOrElse { throw Exception("Failed to open publication at $url: ${it.message}") }

        // No onCreatePublication transformer: the only thing it ever did was
        // re-parse the OPF to hang a "mediaOverlay" property on each reading
        // order link, for the read-along player that no longer exists.
        val publication = opener.open(asset, allowUserInteraction = false)
            .getOrElse { throw Exception("Failed to open publication at $url: ${it.message}") }

        publications[bookUuid] = publication

        return publication
    }

}
