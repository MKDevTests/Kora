package snd.komelia.image

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.counted
import io.ktor.utils.io.readRemaining
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import snd.komelia.settings.model.TranslationLanguage
import snd.komelia.updates.UpdateClient
import snd.komelia.updates.UpdateProgress
import java.io.File
import java.security.MessageDigest

private val logger = KotlinLogging.logger { }

/**
 * Fetches a Bergamot language pair from the same place Firefox gets it.
 *
 * Mozilla's Remote Settings is queried at download time rather than the URLs
 * being written down here, because an attachment's location is a UUID that
 * changes whenever the model is republished. The copies in the
 * firefox-translations-models repository are not an option: they are git-lfs
 * pointers whose blobs the server answers 410 for.
 *
 * Roughly 36MB for en-fr, so this is a deliberate action with a progress bar,
 * never something a page turn triggers.
 */
class BergamotModelDownloader(
    private val ktor: HttpClient,
    private val updateClient: UpdateClient,
    private val modelRoot: File,
) {

    fun isDownloaded(source: TranslationLanguage, target: TranslationLanguage): Boolean =
        BergamotPair.of(source, target)?.isComplete(modelRoot) == true

    /**
     * Downloads the pair, replacing whatever was there.
     *
     * Files land in a sibling directory and are moved into place only once all
     * three have arrived and matched their hashes. A half-written model is
     * worse than none: the engine would fail to load it on every page instead
     * of falling back cleanly.
     */
    fun download(source: TranslationLanguage, target: TranslationLanguage): Flow<UpdateProgress> {
        return flow {
            val pair = BergamotPair.of(source, target)
            requireNotNull(pair) { "no Bergamot model for ${source.code}-${target.code}" }

            emit(UpdateProgress(0, 0, "looking up the ${pair.directory} model"))
            val records = recordsFor(pair)

            val staging = File(modelRoot, "${pair.directory}.partial")
            staging.deleteRecursively()
            staging.mkdirs()
            try {
                // Weights last: they are 30MB of the 36, so a failure on one of
                // the small files costs seconds rather than the whole transfer.
                listOf(
                    BergamotPair.VOCAB to records.vocab,
                    BergamotPair.SHORTLIST to records.shortlist,
                    BergamotPair.MODEL to records.model,
                ).forEach { (name, record) ->
                    downloadFile(record, File(staging, name))
                }

                val target = pair.dirIn(modelRoot)
                target.deleteRecursively()
                staging.renameTo(target)
                emit(UpdateProgress(1, 1, "ready"))
            } catch (e: Throwable) {
                staging.deleteRecursively()
                throw e
            }
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun FlowCollector<UpdateProgress>.downloadFile(record: Record, into: File) {
        val url = ATTACHMENT_BASE + record.attachment.location
        val digest = MessageDigest.getInstance("SHA-256")
        updateClient.streamFile(url) { response ->
            val length = response.headers["Content-Length"]?.toLong() ?: record.attachment.size
            emit(UpdateProgress(length, 0, record.name))
            val channel = response.bodyAsChannel().counted()
            into.outputStream().buffered().use { output ->
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                    while (!packet.exhausted()) {
                        val bytes = packet.readByteArray()
                        digest.update(bytes)
                        output.write(bytes)
                    }
                    output.flush()
                    emit(UpdateProgress(length, channel.totalBytesRead, record.name))
                }
            }
        }

        // Checked because the file is executable in every sense that matters:
        // marian mmaps it and reads structure out of it, and a truncated
        // download would surface as a native crash rather than an IO error.
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != record.attachment.hash) {
            throw IllegalStateException("${record.name} does not match its published hash")
        }
    }

    /** The three attachments of one pair, newest published version. */
    private suspend fun recordsFor(pair: BergamotPair): PairRecords {
        val all: RecordsResponse = ktor.get(RECORDS_URL).body()
        val mine = all.data.filter {
            it.fromLang == pair.source.code && it.toLang == pair.target.code
        }
        // Several versions of a pair are published at once (1.0 alongside 2.0),
        // and they are not interchangeable: a 2.0 model with a 1.0 shortlist
        // loads and then translates badly. Pin all three to one version.
        val version = mine.mapNotNull { it.version }.maxByOrNull { versionKey(it) }
            ?: error("no Bergamot model published for ${pair.directory}")
        logger.info { "using ${pair.directory} models version $version" }
        val ofVersion = mine.filter { it.version == version }

        fun ofType(type: String) = ofVersion.firstOrNull { it.fileType == type }
            ?: error("no $type published for ${pair.directory} $version")

        return PairRecords(
            model = ofType("model"),
            vocab = ofType("vocab"),
            shortlist = ofType("lex"),
        )
    }

    /** "10.0" has to sort above "9.0", which it does not as a string. */
    private fun versionKey(version: String): Int {
        val parts = version.split('.').map { it.toIntOrNull() ?: 0 }
        return parts.getOrElse(0) { 0 } * 1000 + parts.getOrElse(1) { 0 }
    }

    private data class PairRecords(val model: Record, val vocab: Record, val shortlist: Record)

    @Serializable
    private data class RecordsResponse(val data: List<Record>)

    @Serializable
    private data class Record(
        val name: String = "",
        val fileType: String = "",
        val fromLang: String = "",
        val toLang: String = "",
        val version: String? = null,
        val attachment: Attachment,
    )

    @Serializable
    private data class Attachment(
        val location: String,
        val hash: String,
        val size: Long = 0,
        @SerialName("mimetype") val mimeType: String? = null,
    )

    private companion object {
        const val RECORDS_URL =
            "https://firefox.settings.services.mozilla.com/v1/buckets/main/" +
                    "collections/translations-models/records"

        /**
         * Reported by the server itself under /v1/ as
         * capabilities.attachments.base_url. Written down rather than fetched:
         * it is one more request on a value that has not moved in years, and a
         * wrong base fails immediately and visibly.
         */
        const val ATTACHMENT_BASE = "https://firefox-settings-attachments.cdn.mozilla.net/"
    }
}
