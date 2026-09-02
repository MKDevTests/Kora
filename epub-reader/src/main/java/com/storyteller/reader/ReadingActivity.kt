package com.storyteller.reader

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import java.io.File
import java.net.URL

/**
 * Example Activity showing how to embed [EpubView] in a standalone app.
 *
 * Expects the calling Intent to carry:
 *   - `"bookUuid"` (String) — a stable identifier for the book
 *   - `"epubPath"` (String) — absolute path to the `.epub` ZIP file
 *   - `"locatorJson"` (String, optional) — JSON-serialised [Locator] for the saved reading position
 *
 * The activity's layout must contain a [FrameLayout] with `android:id="@+id/epub_container"`.
 */
class ReadingActivity : FragmentActivity() {

    private var epubView: EpubView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reading)

        val bookUuid = intent.getStringExtra("bookUuid")
            ?: error("ReadingActivity requires 'bookUuid' in Intent extras")
        val epubZipPath = intent.getStringExtra("epubPath")
            ?: error("ReadingActivity requires 'epubPath' in Intent extras")

        lifecycleScope.launch {
            // 1. Extract .epub ZIP to a local directory (idempotent — skip if already extracted)
            val extractedDir = File(filesDir, "books/$bookUuid").also { it.mkdirs() }
            if (extractedDir.list().isNullOrEmpty()) {
                BookService.extractArchive(
                    URL("file://$epubZipPath"),
                    extractedDir.toURI().toURL()
                )
            }

            // 2. Open publication
            BookService.openPublication(bookUuid, extractedDir.toURI().toURL())

            // 3. Restore saved position (null = start of book)
            val savedLocator: Locator? = intent.getStringExtra("locatorJson")
                ?.let { Locator.fromJSON(org.json.JSONObject(it)) }

            // 4. Create and mount EpubView
            val view = EpubView(
                context = this@ReadingActivity,
                activity = this@ReadingActivity,
                listener = object : EpubViewListener {
                    override fun onLocatorChange(locator: Locator) {
                        // Persist the reading position here, e.g. to a database or SharedPrefs
                    }

                    override fun onMiddleTouch() {
                        // Toggle toolbar visibility, etc.
                    }

                    override fun onDoubleTouch(locator: Locator) {
                        // Show bookmark/highlight controls at this position
                    }

                    override fun onHighlightTap(decorationId: String, x: Int, y: Int) {
                        // Show a context menu for the tapped highlight
                    }
                }
            ).also { epubView = it }

            view.pendingProps.bookUuid = bookUuid
            view.pendingProps.locator = savedLocator
            view.finalizeProps()

            findViewById<FrameLayout>(R.id.epub_container).addView(view)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        epubView = null
    }
}
