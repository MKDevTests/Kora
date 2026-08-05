package snd.komelia.offline.mediacontainer

import com.github.junrar.Archive
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.context
import java.io.FileInputStream

class RarExtractor {
    /**
     * Reads one entry out of a RAR archive.
     *
     * Everything opened here is closed here (upstream b0cc900b): the previous
     * version handed a content-resolver stream to [Archive] and closed only the
     * archive, leaking a file descriptor per page read — which a reader does
     * hundreds of times per book.
     */
    fun getEntryBytes(file: PlatformFile, entryName: String): ByteArray {
        return when (val androidFile = file.androidFile) {
            is AndroidFile.FileWrapper -> Archive(androidFile.file).extractAndClose(entryName)
            is AndroidFile.UriWrapper -> {
                val descriptor = FileKit.context.contentResolver.openFileDescriptor(androidFile.uri, "r")
                    ?: error("Failed to open file descriptor ${androidFile.uri}")
                descriptor.use { fd ->
                    FileInputStream(fd.fileDescriptor).use { stream ->
                        Archive(stream).extractAndClose(entryName)
                    }
                }
            }
        }
    }

    private fun Archive.extractAndClose(entryName: String): ByteArray {
        return use { rar ->
            val header = rar.fileHeaders.find { it.fileName == entryName }
                ?: error("rar entry does not exist: $entryName")
            // getInputStream, not extractFile into a buffer: same bytes, one
            // copy less for a page that can weigh several megabytes.
            rar.getInputStream(header).use { it.readBytes() }
        }
    }
}
