package snd.komelia.ui.reader.image.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.LayoutDirection.Ltr
import androidx.compose.ui.unit.LayoutDirection.Rtl
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Size
import coil3.size.SizeResolver
import snd.komelia.image.ReaderImage
import snd.komelia.image.coil.BookPageThumbnailRequest
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.reader.image.PageMetadata
import kotlin.math.roundToInt

@Composable
fun ProgressSlider(
    pages: List<PageMetadata>,
    currentPageIndex: Int,
    onPageNumberChange: (Int) -> Unit,
    show: Boolean,
    layoutDirection: LayoutDirection,
    modifier: Modifier = Modifier,
    isBare: Boolean = false,
    loadThumbnailPreviews: Boolean = true,
    onLabelClick: () -> Unit = {},
) {
    PageSpreadProgressSlider(
        pageSpreads = pages.map { listOf(it) },
        currentSpreadIndex = currentPageIndex,
        onPageNumberChange = onPageNumberChange,
        show = show,
        layoutDirection = layoutDirection,
        modifier = modifier,
        isBare = isBare,
        loadThumbnailPreviews = loadThumbnailPreviews,
        onLabelClick = onLabelClick,
    )
}

@Composable
fun PageSpreadProgressSlider(
    pageSpreads: List<List<PageMetadata>>,
    currentSpreadIndex: Int,
    onPageNumberChange: (Int) -> Unit,
    show: Boolean,
    layoutDirection: LayoutDirection,
    modifier: Modifier = Modifier,
    isBare: Boolean = false,
    loadThumbnailPreviews: Boolean = true,
    onLabelClick: () -> Unit = {},
) {
    if (pageSpreads.isEmpty()) return

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier.then(
            if (isBare) Modifier.fillMaxWidth()
            else Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 10.dp)
        )
    ) {
        if (show || isHovered.value || isBare) {
            Column {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Slider(
                        pageSpreads = pageSpreads,
                        currentSpreadIndex = currentSpreadIndex,
                        onPageNumberChange = onPageNumberChange,
                        layoutDirection = layoutDirection,
                        interactionSource = interactionSource,
                        isBare = isBare,
                        loadThumbnailPreviews = loadThumbnailPreviews,
                        onLabelClick = onLabelClick,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Slider(
    pageSpreads: List<List<PageMetadata>>,
    currentSpreadIndex: Int,
    onPageNumberChange: (Int) -> Unit,
    layoutDirection: LayoutDirection,
    interactionSource: MutableInteractionSource,
    isBare: Boolean,
    loadThumbnailPreviews: Boolean,
    onLabelClick: () -> Unit,
) {
    var currentPos by remember(currentSpreadIndex) { mutableStateOf(currentSpreadIndex) }
    val currentSpread = remember(pageSpreads, currentPos) { pageSpreads.getOrElse(currentPos) { pageSpreads.last() } }
    val label = remember(currentSpread, pageSpreads, currentPos) {
        val spread = when (layoutDirection) {
            Ltr -> currentSpread
            Rtl -> currentSpread.reversed()
        }
        spread.map { it.pageNumber }.joinToString("-")

    }

    var showPreview by remember { mutableStateOf(false) }
    val sliderValue by derivedStateOf { currentPos.toFloat() }
    val accentColor = LocalAccentColor.current

    val sliderState = rememberSliderState(
        value = sliderValue,
        onValueChange = {
            showPreview = true
            currentPos = it.roundToInt()
        },
        onValueChangeFinished = {
            onPageNumberChange(currentPos)
            showPreview = false
        },
        steps = remember(pageSpreads.size) { (pageSpreads.size - 2).coerceAtLeast(0) },
        valueRange = remember(pageSpreads.size) { 0f..(pageSpreads.size - 1).toFloat() },
    )

    Layout(content = {
        if (showPreview && loadThumbnailPreviews) {
            Row {
                for (pageMetadata in currentSpread) {
                    BookPageThumbnail(
                        page = pageMetadata,
                        modifier = Modifier.height(300.dp).widthIn(min = 210.dp)
                    )
                }
            }
        } else Spacer(Modifier)

        val labelBackground =
            accentColor?.copy(alpha = 0.8f) ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        val onLabelColor = if (accentColor != null) {
            if (accentColor.luminance() > 0.5f) Color.Black else Color.White
        } else MaterialTheme.colorScheme.onSurfaceVariant

        if (showPreview || !isBare) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .background(
                        color = labelBackground,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onLabelClick() }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .defaultMinSize(minWidth = 40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ViewCarousel,
                    contentDescription = null,
                    tint = onLabelColor,
                    modifier = Modifier.size(20.dp).padding(end = 4.dp).offset(y = (-2).dp)
                )
                Text(
                    label,
                    textAlign = TextAlign.Center,
                    color = onLabelColor,
                    modifier = Modifier.offset(y = (-2).dp)
                )
            }
        } else Spacer(Modifier)

        Slider(
            state = sliderState,
            colors = AppSliderDefaults.colors(accentColor = accentColor),
            interactionSource = interactionSource,
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = AppSliderDefaults.colors(accentColor = accentColor),
                    modifier = Modifier.height(16.dp)
                )
            },
            thumb = { state ->
                val isPressed by interactionSource.collectIsPressedAsState()
                val isDragged by interactionSource.collectIsDraggedAsState()
                val isInteracting = isPressed || isDragged

                val thumbWidth by animateDpAsState(
                    targetValue = if (isInteracting) 2.dp else 4.dp,
                    label = "ThumbWidthAnimation"
                )

                val thumbColor = accentColor ?: MaterialTheme.colorScheme.tertiaryContainer

                Box(
                    modifier = Modifier
                        .size(width = thumbWidth, height = 44.dp)
                        .background(
                            thumbColor,
                            RoundedCornerShape(50)
                        )
                )
            }
        )

    }) { measurables, constraints ->
        val previewPlaceable = measurables[0].measure(constraints)
        val labelPlaceable = measurables[1].measure(constraints)
        val sliderPlaceable = measurables[2].measure(constraints)

        val previewOffsetX = (constraints.maxWidth * sliderState.coercedValueAsFraction - previewPlaceable.width / 2)
            .roundToInt()
            .coerceIn(0, constraints.maxWidth - previewPlaceable.width)

        val labelOffsetX = (constraints.maxWidth * sliderState.coercedValueAsFraction - labelPlaceable.width / 2)
            .roundToInt()
            .coerceIn(0, constraints.maxWidth - labelPlaceable.width)

        val totalHeight = if (isBare) sliderPlaceable.height
        else previewPlaceable.height + sliderPlaceable.height + labelPlaceable.height

        layout(constraints.maxWidth, totalHeight) {
            if (!isBare) {
                previewPlaceable.placeRelative(
                    x = previewOffsetX,
                    y = 0
                )
                labelPlaceable.placeRelative(
                    x = labelOffsetX,
                    y = previewPlaceable.height
                )
                sliderPlaceable.placeRelative(
                    x = 0,
                    y = previewPlaceable.height + labelPlaceable.height
                )
            } else {
                previewPlaceable.placeRelative(
                    x = previewOffsetX,
                    y = -previewPlaceable.height - labelPlaceable.height - 10.dp.roundToPx()
                )
                labelPlaceable.placeRelative(
                    x = labelOffsetX,
                    y = -labelPlaceable.height - 10.dp.roundToPx()
                )
                sliderPlaceable.placeRelative(0, 0)
            }
        }
    }
}

@Composable
fun BookPageThumbnail(
    page: PageMetadata,
//    image: ImageResult?,
    modifier: Modifier,
    useRoundedCorners: Boolean = true,
    isCurrentPage: Boolean = false
) {
    val shape = if (useRoundedCorners) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp)
    val borderColor = if (isCurrentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val borderWidth = 2.dp
    Column(
        modifier = modifier
            .border(BorderStroke(borderWidth, borderColor), shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val context = LocalPlatformContext.current
        val request = remember(page) {
            val pageId = page.toPageId()
            ImageRequest.Builder(context)
                .data(BookPageThumbnailRequest(page.bookId, page.pageNumber))
                .memoryCacheKey(pageId.toString())
                .diskCacheKey(pageId.toString())
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build()
        }

//        val painter = remember(image) { image?.image?.asPainter(context) }
//        if (painter != null) {
//            Image(
//                painter = painter,
//                contentDescription = null,
//                contentScale = ContentScale.Fit,
//                modifier = modifier
//            )
//        }
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun rememberSliderState(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 0,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
): SliderState {

    val state = remember(steps, valueRange) {
        SliderState(value, steps, onValueChangeFinished, valueRange)
    }

    state.onValueChangeFinished = onValueChangeFinished
    state.onValueChange = onValueChange
    state.value = value
    return state
}
