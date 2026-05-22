package snd.komelia.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.runtime.remember
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import io.github.oshai.kotlinlogging.KotlinLogging
import snd.komelia.MainActivity
import snd.komelia.dependencies
import snd.komelia.image.coil.BookDefaultThumbnailRequest
import snd.komelia.komga.api.model.KomeliaBook

private val logger = KotlinLogging.logger { }

/** Intent action used by widget taps to ask MainActivity to open a book. */
const val widgetActionOpenBook = "snd.komelia.action.OPEN_BOOK"
const val widgetExtraBookId = "snd.komelia.extra.BOOK_ID"

// 360x540 keeps the 3 covers well under Glance's ~5 MB bitmap budget
// (3 * 360 * 540 * 4B ≈ 2.2 MB) while looking sharp on a 4x3 cell.
private const val coverCapWidth = 360
private const val coverCapHeight = 540
private const val slotCount = 3

/**
 * Home-screen widget showing the 3 next books up. Renders a fixed Row of
 * 3 covers + truncated titles. Fewer than 3 books → fills remaining slots
 * with a celebratory "all caught up" tile. No books at all → single
 * full-row "all caught up" tile.
 *
 * Render path:
 *  1. Read whatever is in [WidgetCache] for an instant first paint.
 *  2. Try a fresh fetch via [snd.komelia.nextbook.NextBookService].
 *      - Success: persist + render fresh state.
 *      - Failure: fall back to whatever the cache had (already shown).
 *
 * The Kora dependency graph is bootstrapped from MainActivity, not from
 * Application.onCreate, so widget updates that fire before the user has
 * opened the app see a null [dependencies]. We render an "Ouvrir Kora"
 * placeholder in that case rather than fighting cold-start ordering.
 */
class NextBookWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cache = WidgetCache(context)
        val container = dependencies.value
        val initial = cache.load()

        // Try a fresh fetch in the background and rewrite the cache on
        // success. We then render whatever the cache holds AFTER that
        // attempt, so a successful refresh shows up immediately.
        container?.let { c ->
            runCatching {
                val books = c.nextBookService.getNextUpBooks(slotCount).getOrNull()
                if (books != null) {
                    val refreshed = persistFresh(context, c.coilImageLoader, cache, books)
                    cache.save(refreshed)
                    cache.pruneOrphans(refreshed)
                }
            }.onFailure { logger.warn(it) { "Could not refresh widget cache" } }
        }

        val rendered = cache.load().ifEmpty { initial }

        provideContent {
            WidgetContent(
                items = rendered,
                cache = cache,
                koraReady = container != null,
            )
        }
    }

    private suspend fun persistFresh(
        context: Context,
        imageLoader: ImageLoader,
        cache: WidgetCache,
        books: List<KomeliaBook>,
    ): List<WidgetCachedBook> {
        return books.mapNotNull { book ->
            val bitmap = loadCover(context, imageLoader, book) ?: return@mapNotNull null
            val path = cache.writeCover(book.id.value, bitmap) ?: return@mapNotNull null
            WidgetCachedBook(
                bookId = book.id.value,
                seriesTitle = book.seriesTitle,
                bookTitle = book.metadata.title,
                coverBitmapPath = path,
            )
        }
    }

    private suspend fun loadCover(
        context: Context,
        imageLoader: ImageLoader,
        book: KomeliaBook,
    ): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(BookDefaultThumbnailRequest(book.id))
            .size(coverCapWidth, coverCapHeight)
            .build()
        val result = imageLoader.execute(request)
        val image = result.image ?: return null
        return runCatching {
            (image as? BitmapImage)?.bitmap ?: image.toBitmap(coverCapWidth, coverCapHeight)
        }.getOrNull()
    }
}

@Composable
private fun WidgetContent(
    items: List<WidgetCachedBook>,
    cache: WidgetCache,
    koraReady: Boolean,
) {
    val context = LocalContext.current
    val openHomeIntent = remember(context) { mainActivityIntent(context, bookId = null) }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF1B1B1F)))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(actionStartActivity(openHomeIntent)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WidgetHeader()
        Spacer(GlanceModifier.height(6.dp))
        Box(
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !koraReady && items.isEmpty() -> SimpleTile(
                    text = "Ouvrir Kora",
                    onClickIntent = openHomeIntent,
                )
                items.isEmpty() -> SimpleTile(
                    text = "Vous êtes à jour 🎉",
                    onClickIntent = openHomeIntent,
                )
                else -> BooksRow(items = items, cache = cache, context = context)
            }
        }
    }
}

@Composable
private fun WidgetHeader() {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        Image(
            provider = ImageProvider(io.github.snd_r.komelia.R.mipmap.ic_launcher),
            contentDescription = "Kora",
            modifier = GlanceModifier.size(20.dp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = "Reprendre la lecture",
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        // Push the refresh icon to the trailing edge of the header.
        Spacer(GlanceModifier.defaultWeight())
        Image(
            provider = ImageProvider(android.R.drawable.ic_popup_sync),
            contentDescription = "Refresh",
            modifier = GlanceModifier
                .size(20.dp)
                .clickable(actionRunCallback<RefreshNextBookWidgetAction>()),
        )
    }
}

@Composable
private fun BooksRow(
    items: List<WidgetCachedBook>,
    cache: WidgetCache,
    context: Context,
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.Top,
    ) {
        repeat(slotCount) { index ->
            if (index > 0) Spacer(GlanceModifier.width(8.dp))
            val item = items.getOrNull(index)
            if (item != null) {
                BookTile(
                    item = item,
                    cache = cache,
                    intent = mainActivityIntent(context, bookId = item.bookId),
                    modifier = GlanceModifier.defaultWeight(),
                )
            } else {
                AllCaughtUpSlot(modifier = GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun BookTile(
    item: WidgetCachedBook,
    cache: WidgetCache,
    intent: Intent,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(actionStartActivity(intent))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bitmap = cache.readCover(item.coverBitmapPath)
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = item.bookTitle,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            )
        } else {
            Spacer(GlanceModifier.defaultWeight().fillMaxWidth())
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = item.bookTitle,
            maxLines = 2,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun AllCaughtUpSlot(modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(ColorProvider(Color(0x33FFFFFF)))
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "À jour 🎉",
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 12.sp,
            ),
        )
    }
}

@Composable
private fun SimpleTile(text: String, onClickIntent: Intent) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(onClickIntent)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

private fun mainActivityIntent(context: Context, bookId: String?): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (bookId != null) {
            action = widgetActionOpenBook
            putExtra(widgetExtraBookId, bookId)
        }
    }
